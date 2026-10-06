import { identityFrom } from './auth';
import { liveWorkId } from './bindings';
import type { Env } from './env';
import { normalize } from './normalize';
import type { BindingWritePlan } from './trust';
import { ingestTelemetry, planBindingWrite } from './trust';
import { err, now, ok } from './util';

const MAX_FEEDS = 20;
const MAX_EPISODES_PER_FEED = 5000;
const MAX_NAME = 500;
const MAX_KEY = 50;
const MAX_DOC = 200;
const MAX_AIR_DATE = 20;
const MAX_URL_FIELD = 1000;
const MAX_OVERVIEW = 5000;
const MAX_URL = 1000;
const DIVERGENCE_MIN_FEED = 10;
const DIVERGENCE_THRESHOLD = 0.5;
/** Human-confirmed bindings (Find Provider picks) never record below
 *  this confidence — mirrors chaptermap's USER_PICKED_FLOOR. */
const USER_PICKED_FLOOR = 0.9;
/** Reserved source_key for the on-device MikoDB artifact feed — the
 *  structural authority (D-01). Only a feed carrying real artifact
 *  shape keeps this key; forged claims demote to 'feed:mikodb:map'. */
const ARTIFACT_SOURCE = 'mikodb:map';
const ARTIFACT_STRUCTURED_MIN = 0.9;

const KINDS = new Set(['episode', 'special', 'nc']);

interface FeedEpisode {
    name?: string;
    url?: string;
    /** Source-reported upload/air time (epoch ms). */
    date_upload?: number;
    /** 'episode' | 'special' | 'nc' — artifact/carrier-asserted; when
     *  absent the kind derives from the `key` prefix (digits → episode,
     *  S → special, C → nc, anything else → special) else 'episode'. */
    kind?: string;
    /** Kind-scoped doc-local number (provider-local for carriers). */
    number?: number | null;
    /** Artifact key verbatim — "1", "S3", "C2" (artifact feeds only). */
    key?: string;
    /** Artifact doc namespace — "anidb:1234" (artifact feeds only). */
    doc?: string;
    /** Canonical absolute episode number, present only when the feed
     *  canonicalized the number against the bound artifact entry. */
    abs?: number | null;
    season?: number | null;
    ep_in_season?: number | null;
    /** ISO date ("1998-11-13") — wins date_upload semantics (D-05). */
    air_date?: string;
    image?: string;
    overview?: string;
    runtime?: number | null;
    anidb_eid?: number | null;
    tvdb_eid?: number | null;
    tvdb_show_id?: number | null;
    filler?: number | boolean;
}

interface CanonizeFeed {
    source_key?: string;
    tier?: string;
    verified?: boolean;
    score?: number;
    provider_ref?: string;
    provenance?: string;
    episodes?: FeedEpisode[];
}

interface CanonizeRequest {
    work_id?: string;
    feeds?: CanonizeFeed[];
    /** Bundled mint telemetry (Phase 20) — observational provider_stats
     *  counters, never an input to lifecycle math. */
    telemetry?: unknown;
}

/** Stored feed row → the shape the merge loop consumes. */
export interface StoredFeed {
    source_key: string;
    episodes: FeedEpisode[];
}

interface EEntry {
    kind: string;
    number: number | null;
    abs: number | null;
    season: number | null;
    epInSeason: number | null;
    epKey: string | null;
    doc: string | null;
    /** Representative label (trimmed name). */
    label: string;
    /** Normalized label — label-merge + divergence grouping. */
    norm: string;
    mkey: string;
    src: string;
    url: string | null;
    date: number | null;
    airDate: string | null;
    image: string | null;
    overview: string | null;
    runtime: number | null;
    anidbEid: number | null;
    tvdbEid: number | null;
    tvdbShowId: number | null;
    filler: number;
}

function num(v: unknown): number | null {
    return typeof v === 'number' && Number.isFinite(v) ? v : null;
}

function str(v: unknown): string | null {
    return typeof v === 'string' && v ? v : null;
}

/** Kind from the artifact key prefix — digits main, S special, C nc,
 *  T/P fold into special (AniDB vocabulary, RESEARCH Pattern 1). */
function kindFromKey(key: string | null): string | null {
    if (!key) return null;
    if (/^\d+$/.test(key)) return 'episode';
    if (key.startsWith('S')) return 'special';
    if (key.startsWith('C')) return 'nc';
    return 'special';
}

/**
 * Merge keys per Design §4 — three disjoint keyspaces:
 *  - mains:            e|{abs ?? number}  (abs wins when canonicalized)
 *  - specials/nc:      {kind}|{doc ?? ''}|{ep_in_season ?? number}
 *    (doc namespaces same-coordinate specials across a multi-doc work;
 *    carriers never carry doc, so they merge with un-namespaced rows)
 *  - unnumbered extra: {kind}|L|{normalized label}
 */
function mergeKey(
    kind: string, number: number | null, abs: number | null,
    epInSeason: number | null, doc: string | null, norm: string,
): string {
    if (kind === 'episode') {
        const n = abs ?? number;
        return n !== null ? `e|${n}` : `episode|L|${norm}`;
    }
    const n = epInSeason ?? number;
    return n !== null ? `${kind}|${doc ?? ''}|${n}` : `${kind}|L|${norm}`;
}

async function review(env: Env, kind: string, payload: unknown) {
    const json = JSON.stringify(payload);
    // Re-merges re-evaluate the full stored corpus — an already-known
    // issue shouldn't enqueue another identical review row.
    const { results } = await env.COMMENTS_DB.prepare(
        'SELECT 1 AS x FROM identity_review WHERE kind = ? AND payload = ? LIMIT 1',
    ).bind(kind, json).all();
    if (results.length > 0) return;
    await env.COMMENTS_DB.prepare(
        'INSERT INTO identity_review (kind, payload, created_at) VALUES (?,?,?)',
    ).bind(kind, json, now()).run();
}

/** True when ≥90% of episodes carry an artifact-shaped field
 *  (key/doc/anidb_eid). Empty feeds pass — they assert no structure. */
function isArtifactStructured(eps: FeedEpisode[]): boolean {
    if (eps.length === 0) return true;
    const structured = eps.filter(
        (e) => e.key != null || e.doc != null || e.anidb_eid != null,
    ).length;
    return structured / eps.length >= ARTIFACT_STRUCTURED_MIN;
}

/**
 * POST /v1/episodes/canonize — rebuild a work's canonical episode
 * sequence from the artifact feed + carrier episode feeds. Body:
 *   { work_id, feeds: [{source_key, episodes: [{name, url?, kind?,
 *     number?, key?, doc?, abs?, season?, ep_in_season?, air_date?,
 *     image?, overview?, runtime?, anidb_eid?, tvdb_eid?,
 *     tvdb_show_id?, filler?, date_upload?}]}] }
 * Merge keys per Design §4; 'mikodb:map' in sources → verified.
 */
export async function handleCanonizeEpisodes(req: Request, env: Env): Promise<Response> {
    let body: CanonizeRequest;
    try {
        body = await req.json<CanonizeRequest>();
    } catch {
        return err(400, 'bad_json');
    }
    if (typeof body.work_id !== 'string' || !body.work_id) return err(400, 'bad_request');
    // Dead-id canonize targets redirect onto the merged live work.
    const workId = await liveWorkId(env, body.work_id);
    // Optional Bearer — identified devices attribute mints to their
    // device hash; unauthenticated canonize still writes the feed but
    // its binding lands provisional with NULL minter (invisible until
    // identified devices confirm it — the safe degrade, SD-6).
    const identity = await identityFrom(req, env);
    if (!Array.isArray(body.feeds) || body.feeds.length === 0 || body.feeds.length > MAX_FEEDS) {
        return err(400, 'bad_request');
    }
    for (const f of body.feeds) {
        if (typeof f.source_key !== 'string' || !f.source_key) return err(400, 'bad_request');
        if (!Array.isArray(f.episodes) || f.episodes.length > MAX_EPISODES_PER_FEED) {
            return err(400, 'bad_request');
        }
        if (f.provider_ref !== undefined &&
            (typeof f.provider_ref !== 'string' || f.provider_ref.length > 500)) {
            return err(400, 'bad_request');
        }
        if (f.provenance !== undefined &&
            (typeof f.provenance !== 'string' || f.provenance.length > 40)) {
            return err(400, 'bad_request');
        }
        for (const e of f.episodes) {
            if (typeof e.name !== 'string' || e.name.length > MAX_NAME) return err(400, 'bad_request');
            if (e.date_upload !== undefined &&
                (typeof e.date_upload !== 'number' || !Number.isFinite(e.date_upload))) {
                return err(400, 'bad_request');
            }
            for (const k of ['number', 'abs', 'season', 'ep_in_season', 'runtime',
                'anidb_eid', 'tvdb_eid', 'tvdb_show_id'] as const) {
                const v = e[k];
                if (v !== undefined && v !== null &&
                    (typeof v !== 'number' || !Number.isFinite(v))) {
                    return err(400, 'bad_request');
                }
            }
            for (const [k, cap] of [['key', MAX_KEY], ['doc', MAX_DOC],
                ['air_date', MAX_AIR_DATE], ['image', MAX_URL_FIELD],
                ['overview', MAX_OVERVIEW], ['url', MAX_URL],
                ['kind', 20]] as const) {
                const v = e[k as keyof FeedEpisode];
                if (v !== undefined && (typeof v !== 'string' || v.length > cap)) {
                    return err(400, 'bad_request');
                }
            }
            if (e.filler !== undefined &&
                typeof e.filler !== 'number' && typeof e.filler !== 'boolean') {
                return err(400, 'bad_request');
            }
        }
    }

    // Persist each feed (latest-wins per source_key) then remerge the
    // work's full stored corpus — feeds are durable inputs, and
    // canonical_episodes is their materialized view. A feed whose
    // provider_ref minted a non-confirmed binding persists pending=1 —
    // withheld from the shared merge until solidification (SD-1).
    const ts0 = now();
    const writes: D1PreparedStatement[] = [];
    const refConflicts: Array<{ provider: string; ref: string; existing: string }> = [];
    const demoted: string[] = [];
    const bindingPlans: BindingWritePlan[] = [];
    for (const f of body.feeds) {
        // Forge guard: a feed claiming the artifact source_key without
        // artifact-shaped rows demotes to a plain carrier key — stored
        // and merged like any feed, but it grants no authority.
        let src = f.source_key as string;
        if (src === ARTIFACT_SOURCE && !isArtifactStructured(f.episodes as FeedEpisode[])) {
            src = `feed:${ARTIFACT_SOURCE}`;
            demoted.push(src);
        }
        // The lifecycle decision precedes the feed write — the binding's
        // status decides whether this feed's content joins shared canon
        // now or stays pending (SD-10).
        let pending = 0;
        if (typeof f.provider_ref === 'string' && f.provider_ref) {
            const wantsCrawler = f.provenance === 'crawler';
            const authed = !!env.CRAWLER_KEY &&
                req.headers.get('x-crawler-key') === env.CRAWLER_KEY;
            const provenance = wantsCrawler && !authed
                ? 'device_feed'
                : (f.provenance ?? 'device_feed');
            const confidence = provenance === 'user_picked' && f.verified === true
                ? Math.max(typeof f.score === 'number' ? f.score : 0, USER_PICKED_FLOOR)
                : (typeof f.score === 'number' ? f.score : null);
            const plan = await planBindingWrite(env, {
                provider: src, ref: f.provider_ref, workId,
                confidence, provenance,
                deviceHash: identity?.device_hash ?? null, ts: ts0,
            });
            if (plan.conflict) {
                refConflicts.push({
                    provider: src, ref: f.provider_ref, existing: plan.conflict,
                });
            } else {
                writes.push(...plan.writes);
                bindingPlans.push(plan);
                pending = plan.pendingFeed ? 1 : 0;
            }
        }
        writes.push(env.COMMENTS_DB.prepare(
            `REPLACE INTO canonical_feeds
             (work_id, source_key, feed_json, verified, score, received_at,
              pending, submitter_device_hash)
             VALUES (?,?,?,?,?,?,?,?)`,
        ).bind(
            workId, src, JSON.stringify(f.episodes ?? []),
            f.verified === true ? 1 : 0, f.score ?? null, ts0,
            pending, identity?.device_hash ?? null,
        ));
    }
    await env.COMMENTS_DB.batch(writes);
    // Post-batch lifecycle effects — the just-queued confirm rows are
    // now visible to the ledger (SD-10), and a solidify clears pending
    // so THIS request's remerge publishes the feed into canon.
    const solidified: string[] = [];
    for (const plan of bindingPlans) {
        for (const fx of plan.post) await fx();
        if (plan.solidified) solidified.push(plan.ref);
    }
    if (refConflicts.length > 0) {
        await review(env, 'provider_ref_conflict', {
            work_id: workId, conflicts: refConflicts,
        });
    }
    if (demoted.length > 0) {
        await review(env, 'artifact_feed_unstructured', {
            work_id: workId, feeds: demoted,
        });
    }

    // Tentative feeds (SeriesMatcher floor-without-margin) merge but
    // flag for review — same policy as the chapter side.
    const tentative = body.feeds.filter((f) => f.verified === false);
    if (tentative.length > 0) {
        await review(env, 'episode_feed_tentative', {
            work_id: workId,
            feeds: tentative.map((f) => ({ source_key: f.source_key, score: f.score })),
        });
    }

    // Bundled mint telemetry — observational counters only (locked:
    // zero new per-event requests); malformed/over-cap payloads clamp
    // out inside and can never fail the canonize.
    await ingestTelemetry(env, body.telemetry);

    const summary = await remergeEpisodes(env, workId);
    return ok({
        work_id: workId,
        ...summary,
        solidified,
    });
}

/** A merged canonical row — what remergeEpisodes persists and the
 *  pending-feed overlay shapes into the GET DTO. */
interface MergedEpisode {
    kind: string;
    number: number | null;
    abs: number | null;
    season: number | null;
    epInSeason: number | null;
    epKey: string | null;
    doc: string | null;
    label: string;
    air_date: string | null;
    dateUpload: number | null;
    image: string | null;
    overview: string | null;
    runtime: number | null;
    anidbEid: number | null;
    tvdbEid: number | null;
    tvdbShowId: number | null;
    filler: number;
    confidence: string;
    sources: string[];
    refs: Record<string, string>;
}

interface EpisodeCorpus {
    merged: MergedEpisode[];
    skipped: number;
    /** Numbering conflicts: same track point, >1 merge key across
     *  >1 source — carried whole into the divergence review. */
    conflicts: Array<{ at: string; keys: string[]; sources: string[] }>;
    /** Substantive feeds whose episode merge keys are <50%
     *  corroborated. */
    divergent: string[];
    /** Feeds with ≥DIVERGENCE_MIN_FEED main-episode keys — the
     *  divergence review's `> 1` gate operand. */
    substantive: number;
}

/**
 * The pure merge core of remergeEpisodes — parse → group → flag →
 * order over an arbitrary feed set. No reads, no writes: the persist
 * wrapper owns reviews and the canonical_episodes rewrite; the pending
 * overlay reuses it to answer a minter's GET in memory (SD-2).
 */
export function mergeEpisodeCorpus(feeds: StoredFeed[]): EpisodeCorpus {
    const entries: EEntry[] = [];
    let skipped = 0;
    for (const f of feeds) {
        const src = f.source_key;
        for (const e of f.episodes ?? []) {
            const epKey = str(e.key);
            const doc = str(e.doc);
            const declared = str(e.kind);
            const kind = declared && KINDS.has(declared)
                ? declared
                : (kindFromKey(epKey) ?? 'episode');
            const number = num(e.number);
            const abs = num(e.abs);
            const season = num(e.season);
            const epInSeason = num(e.ep_in_season);
            const label = (e.name as string).trim();
            const norm = normalize(e.name);
            // An entry with no coordinates AND no usable label can't
            // merge on anything stable — skip rather than collapse it
            // into a shared L:'' bucket.
            const coordless = (kind === 'episode' && number === null && abs === null) ||
                (kind !== 'episode' && (epInSeason ?? number) === null);
            if (coordless && !norm) {
                skipped++;
                continue;
            }
            entries.push({
                kind,
                number,
                abs,
                season,
                epInSeason,
                epKey,
                doc,
                label: label || norm,
                norm,
                mkey: mergeKey(kind, number, abs, epInSeason, doc, norm),
                src,
                url: str(e.url),
                date: num(e.date_upload) && (e.date_upload as number) > 0
                    ? (e.date_upload as number)
                    : null,
                airDate: str(e.air_date),
                image: str(e.image),
                overview: str(e.overview),
                runtime: num(e.runtime),
                anidbEid: num(e.anidb_eid),
                tvdbEid: num(e.tvdb_eid),
                tvdbShowId: num(e.tvdb_show_id),
                filler: e.filler === true || num(e.filler) ? 1 : 0,
            });
        }
    }

    const groups = new Map<string, EEntry[]>();
    for (const e of entries) {
        const g = groups.get(e.mkey);
        if (g) g.push(e);
        else groups.set(e.mkey, [e]);
    }

    // Divergence — two feeds disagreeing on where the same main episode
    // lands (same doc-local number or same normalized label, different
    // merge keys). Plus the chapter-side feed-health mirror: a
    // substantive feed (≥MIN mains) whose merge keys are <50%
    // corroborated by other substantive feeds is likely renumbered.
    const divergence = new Map<string, { keys: Set<string>; srcs: Set<string> }>();
    const track = (at: string, e: EEntry) => {
        const g = divergence.get(at) ?? { keys: new Set<string>(), srcs: new Set<string>() };
        g.keys.add(e.mkey);
        g.srcs.add(e.src);
        divergence.set(at, g);
    };
    const feedKeys = new Map<string, Set<string>>();
    for (const e of entries) {
        if (e.kind !== 'episode') continue;
        const fk = feedKeys.get(e.src) ?? new Set<string>();
        fk.add(e.mkey);
        feedKeys.set(e.src, fk);
        if (e.number !== null) track(`n:${e.number}`, e);
        if (e.norm) track(`label:${e.norm}`, e);
    }
    const conflicts: Array<{ at: string; keys: string[]; sources: string[] }> = [];
    for (const [at, g] of divergence) {
        if (g.keys.size > 1 && g.srcs.size > 1) {
            conflicts.push({
                at,
                keys: [...g.keys].sort(),
                sources: [...g.srcs].sort(),
            });
        }
    }

    const allKeys = new Map<string, Set<string>>();
    const substantive = new Set<string>();
    for (const [src, keys] of feedKeys) {
        if (keys.size < DIVERGENCE_MIN_FEED) continue;
        substantive.add(src);
        for (const k of keys) {
            const s = allKeys.get(k) ?? new Set();
            s.add(src);
            allKeys.set(k, s);
        }
    }
    const divergent: string[] = [];
    for (const src of substantive) {
        const keys = feedKeys.get(src) as Set<string>;
        const corroborated = [...keys].filter((k) => (allKeys.get(k)?.size ?? 0) > 1).length;
        if (corroborated / keys.size < DIVERGENCE_THRESHOLD) divergent.push(src);
    }
    const merged = [...groups.values()].map((g) => {
        const sources = [...new Set(g.map((e) => e.src))];
        const refs: Record<string, string> = {};
        for (const e of g) if (e.url) refs[e.src] = e.url;
        // Artifact entries carry the canonical structure — their values
        // win over carrier absence (Design §4).
        const artifact = g.find((e) => e.src === ARTIFACT_SOURCE);
        const rep = artifact ?? g[0];
        const pick = <T>(sel: (e: EEntry) => T | null): T | null =>
            (artifact ? sel(artifact) : null) ?? g.map(sel).find((v) => v !== null) ?? null;
        const airDate = pick((e) => e.airDate);
        const airMs = airDate ? Date.parse(airDate) : NaN;
        const dates = g.map((e) => e.date).filter((d): d is number => d !== null);
        return {
            kind: rep.kind,
            number: pick((e) => e.number),
            abs: pick((e) => e.abs),
            season: pick((e) => e.season),
            epInSeason: pick((e) => e.epInSeason),
            epKey: pick((e) => e.epKey),
            doc: pick((e) => e.doc),
            label: rep.label,
            air_date: airDate,
            // D-05: canonical air_date is date_upload semantics; else
            // earliest contributing feed date (0009 rule).
            dateUpload: Number.isFinite(airMs) ? airMs : (dates.length ? Math.min(...dates) : null),
            image: pick((e) => e.image),
            overview: pick((e) => e.overview),
            runtime: pick((e) => e.runtime),
            anidbEid: pick((e) => e.anidbEid),
            tvdbEid: pick((e) => e.tvdbEid),
            tvdbShowId: pick((e) => e.tvdbShowId),
            filler: pick((e) => (e.filler ? 1 : null)) ?? 0,
            // Anti-forge rule (Design §5): verified requires the
            // artifact — carrier-only rows cap at 'single'.
            confidence: sources.includes(ARTIFACT_SOURCE) ? 'verified' : 'single',
            sources,
            refs,
        };
    });
    merged.sort((a, b) => {
        const order = (k: string) => (k === 'episode' ? 0 : k === 'special' ? 1 : 2);
        const ka = order(a.kind);
        const kb = order(b.kind);
        if (ka !== kb) return ka - kb;
        const inf = Number.MAX_SAFE_INTEGER;
        if (a.kind === 'episode') {
            const d = (a.abs ?? inf) - (b.abs ?? inf);
            return d !== 0 ? d : (a.number ?? inf) - (b.number ?? inf);
        }
        if (a.kind === 'special') {
            const d = (a.season ?? inf) - (b.season ?? inf);
            if (d !== 0) return d;
            return (a.epInSeason ?? a.number ?? inf) - (b.epInSeason ?? b.number ?? inf);
        }
        return (a.number ?? inf) - (b.number ?? inf);
    });

    return {
        merged,
        skipped,
        conflicts,
        divergent,
        substantive: substantive.size,
    };
}

/**
 * Rebuild a work's canonical episode sequence from its ENTIRE stored
 * feed corpus (canonical_feeds) — pending feeds stay withheld until
 * their binding solidifies (SD-1). Merge keys and confidence per Design
 * §4/§5; divergent feeds and numbering conflicts go to identity_review.
 */
export async function remergeEpisodes(env: Env, workId: string): Promise<{
    count: number;
    verified: number;
    skipped: number;
    conflicts: number;
    divergent: string[];
}> {
    const { results: rows } = await env.COMMENTS_DB.prepare(
        `SELECT source_key, feed_json FROM canonical_feeds
         WHERE work_id = ? AND pending = 0`,
    ).bind(workId).all<{ source_key: string; feed_json: string }>();

    const feeds: StoredFeed[] = [];
    for (const r of rows) {
        try {
            feeds.push({ source_key: r.source_key, episodes: JSON.parse(r.feed_json) });
        } catch { /* corrupt feed row — skip, next canonize repairs */ }
    }

    const corpus = mergeEpisodeCorpus(feeds);
    if ((corpus.divergent.length > 0 && corpus.substantive > 1) || corpus.conflicts.length > 0) {
        await review(env, 'episode_feed_divergent', {
            work_id: workId,
            divergent: corpus.substantive > 1 ? corpus.divergent : [],
            conflicts: corpus.conflicts,
        });
    }

    const ts = now();
    const stmts = [
        env.COMMENTS_DB.prepare('DELETE FROM canonical_episodes WHERE work_id = ?').bind(workId),
        ...corpus.merged.map((m, i) =>
            env.COMMENTS_DB.prepare(
                `INSERT INTO canonical_episodes
                 (work_id, seq, kind, number, abs_number, season, ep_in_season,
                  ep_key, doc_id, label, air_date, date_upload, image, overview,
                  runtime, anidb_eid, tvdb_eid, tvdb_show_id, filler,
                  confidence, sources_json, refs_json, updated_at)
                 VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)`,
            ).bind(
                workId, i, m.kind, m.number, m.abs, m.season, m.epInSeason,
                m.epKey, m.doc, m.label, m.air_date, m.dateUpload, m.image,
                m.overview, m.runtime, m.anidbEid, m.tvdbEid, m.tvdbShowId,
                m.filler, m.confidence, JSON.stringify(m.sources),
                JSON.stringify(m.refs), ts,
            ),
        ),
    ];
    await env.COMMENTS_DB.batch(stmts);

    return {
        count: corpus.merged.length,
        verified: corpus.merged.filter((m) => m.confidence === 'verified').length,
        skipped: corpus.skipped,
        conflicts: corpus.conflicts.length,
        divergent: corpus.divergent,
    };
}

/** GET /v1/works/:id/episodes — the canonical sequence, empty list if
 *  never canonized. A Bearer whose pending feeds are withheld from
 *  canon (own submissions, or feeds on source_keys carrying the
 *  caller's provisional binding) gets the in-memory overlay merge —
 *  shared feeds ∪ caller-visible pending feeds, never persisted (SD-2). */
export async function handleGetEpisodes(
    req: Request,
    params: Record<string, string>,
    _url: URL,
    env: Env,
): Promise<Response> {
    const workId = await liveWorkId(env, params.id);
    const me = await identityFrom(req, env);
    if (me) {
        const { results: pending } = await env.COMMENTS_DB.prepare(
            `SELECT source_key, feed_json FROM canonical_feeds
             WHERE work_id = ? AND pending = 1
               AND (submitter_device_hash = ?
                    OR source_key IN (
                        SELECT provider FROM external_ids
                        WHERE miko_work_id = ? AND minter_device_hash = ?
                          AND status = 'provisional'))`,
        ).bind(workId, me.device_hash, workId, me.device_hash)
            .all<{ source_key: string; feed_json: string }>();
        if (pending.length > 0) {
            const { results: shared } = await env.COMMENTS_DB.prepare(
                `SELECT source_key, feed_json FROM canonical_feeds
                 WHERE work_id = ? AND pending = 0`,
            ).bind(workId).all<{ source_key: string; feed_json: string }>();
            const feeds: StoredFeed[] = [];
            for (const r of [...shared, ...pending]) {
                try {
                    feeds.push({ source_key: r.source_key, episodes: JSON.parse(r.feed_json) });
                } catch { /* corrupt feed row — skip */ }
            }
            const corpus = mergeEpisodeCorpus(feeds);
            return ok({
                work_id: workId,
                episodes: corpus.merged.map((m, i) => ({
                    seq: i,
                    kind: m.kind,
                    number: m.number,
                    abs: m.abs,
                    season: m.season,
                    ep_in_season: m.epInSeason,
                    ep_key: m.epKey,
                    doc: m.doc,
                    label: m.label,
                    air_date: m.air_date,
                    date_upload: m.dateUpload,
                    image: m.image,
                    overview: m.overview,
                    runtime: m.runtime,
                    anidb_eid: m.anidbEid,
                    tvdb_eid: m.tvdbEid,
                    tvdb_show_id: m.tvdbShowId,
                    filler: m.filler,
                    confidence: m.confidence,
                    sources: m.sources,
                    refs: m.refs,
                })),
            });
        }
    }
    const { results } = await env.COMMENTS_DB.prepare(
        `SELECT seq, kind, number, abs_number, season, ep_in_season, ep_key, doc_id,
                label, air_date, date_upload, image, overview, runtime,
                anidb_eid, tvdb_eid, tvdb_show_id, filler,
                confidence, sources_json, refs_json
         FROM canonical_episodes WHERE work_id = ? ORDER BY seq`,
    ).bind(workId).all<{
        seq: number; kind: string; number: number | null; abs_number: number | null;
        season: number | null; ep_in_season: number | null; ep_key: string | null;
        doc_id: string | null; label: string; air_date: string | null;
        date_upload: number | null; image: string | null; overview: string | null;
        runtime: number | null; anidb_eid: number | null; tvdb_eid: number | null;
        tvdb_show_id: number | null; filler: number; confidence: string;
        sources_json: string; refs_json: string;
    }>();
    return ok({
        work_id: workId,
        episodes: results.map((r) => ({
            seq: r.seq,
            kind: r.kind,
            number: r.number,
            abs: r.abs_number,
            season: r.season,
            ep_in_season: r.ep_in_season,
            ep_key: r.ep_key,
            doc: r.doc_id,
            label: r.label,
            air_date: r.air_date,
            date_upload: r.date_upload,
            image: r.image,
            overview: r.overview,
            runtime: r.runtime,
            anidb_eid: r.anidb_eid,
            tvdb_eid: r.tvdb_eid,
            tvdb_show_id: r.tvdb_show_id,
            filler: r.filler,
            confidence: r.confidence,
            sources: JSON.parse(r.sources_json) as string[],
            refs: JSON.parse(r.refs_json) as Record<string, string>,
        })),
    });
}
