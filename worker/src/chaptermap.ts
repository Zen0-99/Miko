import { liveWorkId } from './bindings';
import { parseChapter } from './chapterparse';
import type { Env } from './env';
import { normalize } from './normalize';
import { err, now, ok } from './util';

const MAX_FEEDS = 20;
const MAX_CHAPTERS_PER_FEED = 5000;
const MAX_NAME = 500;
const DIVERGENCE_MIN_FEED = 10;
const DIVERGENCE_THRESHOLD = 0.5;
/** Human-confirmed bindings (Find Provider picks) never record below
 *  this confidence — a user's explicit pick outranks a matcher ACCEPT. */
const USER_PICKED_FLOOR = 0.9;

interface FeedChapter {
    name?: string;
    url?: string;
    /** Source-reported upload time (epoch ms); merged rows keep the
     *  earliest contributing date. */
    date_upload?: number;
    /** Structured fields from server-side feeds (MangaDex/crawler) —
     *  bypass parseChapter when present. number may be null for
     *  unnumbered extras whose kind the feed asserts directly. */
    number?: number | null;
    volume?: number | null;
    part?: number | null;
    kind?: string;
}

interface CanonizeFeed {
    source_key?: string;
    tier?: string;
    /** Series-matcher provenance (13-01/13-02): feeds that cleared the
     * floor but not the margin arrive as tentative — they merge but
     * also write an identity_review row. */
    verified?: boolean;
    score?: number;
    /** Provider entry ref (manga URL / id on the source) — persisted to
     *  external_ids so the work→provider binding is durable. */
    provider_ref?: string;
    /** Who produced this feed — 'device_feed' (default), 'user_picked'
     *  (Find Provider sheet confirmation, 17-01), 'crawler',
     *  'mangadex_seed'. Stamped onto the external_ids binding. */
    provenance?: string;
    chapters?: FeedChapter[];
}

interface CanonizeRequest {
    work_id?: string;
    feeds?: CanonizeFeed[];
}

interface Entry {
    number: number | null;
    volume: number | null;
    part: number | null;
    kind: string;
    label: string;
    src: string;
    url: string | null;
    date: number | null;
}

function mergeKey(e: Entry): string {
    // Unnumbered specials merge only on identical normalized label —
    // "Extra 2" parses to extra/2 (numbered merge); a bare "Omake"
    // merges only with another "omake".
    const num = e.number ?? `L:${e.label}`;
    return `${e.kind}|${num}|${e.part ?? ''}`;
}

function sortKey(e: { number: number | null; part: number | null; volume: number | null; kind: string }): number {
    const base = e.number ?? (e.kind === 'prologue' ? -1 : 1e6);
    return base * 1000 + (e.part ?? 0) * 10 + Math.min(e.volume ?? 0, 9);
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

/**
 * POST /v1/chapters/canonize — rebuild a work's canonical chapter
 * sequence from golden-source feeds. Body:
 *   { work_id, feeds: [{source_key, tier?, chapters: [{name, url?}]}] }
 * Parse each name; merge by (kind, number|label, part); ≥2 sources
 * → verified. Divergent feeds and kind conflicts go to identity_review.
 */
export async function handleCanonize(req: Request, env: Env): Promise<Response> {
    let body: CanonizeRequest;
    try {
        body = await req.json<CanonizeRequest>();
    } catch {
        return err(400, 'bad_json');
    }
    if (typeof body.work_id !== 'string' || !body.work_id) return err(400, 'bad_request');
    // Dead-id canonize targets redirect onto the merged live work.
    const workId = await liveWorkId(env, body.work_id);
    if (!Array.isArray(body.feeds) || body.feeds.length === 0 || body.feeds.length > MAX_FEEDS) {
        return err(400, 'bad_request');
    }
    for (const f of body.feeds) {
        if (typeof f.source_key !== 'string' || !f.source_key) return err(400, 'bad_request');
        if (!Array.isArray(f.chapters) || f.chapters.length > MAX_CHAPTERS_PER_FEED) {
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
        for (const c of f.chapters) {
            if (typeof c.name !== 'string' || c.name.length > MAX_NAME) return err(400, 'bad_request');
            if (c.date_upload !== undefined &&
                (typeof c.date_upload !== 'number' || !Number.isFinite(c.date_upload))) {
                return err(400, 'bad_request');
            }
            for (const k of ['number', 'volume', 'part'] as const) {
                const v = c[k];
                if (v !== undefined && v !== null &&
                    (typeof v !== 'number' || !Number.isFinite(v))) {
                    return err(400, 'bad_request');
                }
            }
        }
    }

    // Persist each feed (latest-wins per source_key) then remerge the
    // work's full stored corpus — feeds are durable inputs, and
    // canonical_chapters is their materialized view.
    const ts0 = now();
    const writes: D1PreparedStatement[] = [];
    const refConflicts: Array<{ provider: string; ref: string; existing: string }> = [];
    for (const f of body.feeds) {
        const src = f.source_key as string;
        writes.push(env.COMMENTS_DB.prepare(
            `REPLACE INTO canonical_feeds
             (work_id, source_key, feed_json, verified, score, received_at)
             VALUES (?,?,?,?,?,?)`,
        ).bind(
            workId, src, JSON.stringify(f.chapters ?? []),
            f.verified === true ? 1 : 0, f.score ?? null, ts0,
        ));
        if (typeof f.provider_ref === 'string' && f.provider_ref) {
            // The binding is keyed (provider, ref) → a ref already bound
            // to a DIFFERENT work is an identity conflict: flag it,
            // never rebind silently (OPM sibling-work collision).
            const existing = await env.COMMENTS_DB.prepare(
                `SELECT miko_work_id FROM external_ids
                 WHERE provider = ? AND external_id = ?`,
            ).bind(src, f.provider_ref).first<{ miko_work_id: string }>();
            if (existing && existing.miko_work_id !== workId) {
                refConflicts.push({
                    provider: src, ref: f.provider_ref, existing: existing.miko_work_id,
                });
            } else {
                // 'crawler' provenance is a privileged claim — it says
                // "a managed host vouches for this binding". Require the
                // shared key; unauthenticated claims degrade to
                // 'device_feed' rather than reject the feed itself.
                const wantsCrawler = f.provenance === 'crawler';
                const authed = !!env.CRAWLER_KEY &&
                    req.headers.get('x-crawler-key') === env.CRAWLER_KEY;
                const provenance = wantsCrawler && !authed
                    ? 'device_feed'
                    : (f.provenance ?? 'device_feed');
                // 'user_picked' = a human confirmed this result IS the work
                // in the Find Provider sheet (17-01). The confirmation is
                // the signal, not the sender — no auth, but verified=true is
                // required and the floor outranks a bare matcher ACCEPT.
                const confidence = provenance === 'user_picked' && f.verified === true
                    ? Math.max(typeof f.score === 'number' ? f.score : 0, USER_PICKED_FLOOR)
                    : (typeof f.score === 'number' ? f.score : null);
                writes.push(env.COMMENTS_DB.prepare(
                    `INSERT INTO external_ids
                     (provider, external_id, miko_work_id, confidence, provenance, last_seen_at)
                     VALUES (?,?,?,?,?,?)
                     ON CONFLICT(provider, external_id) DO UPDATE SET
                        confidence = COALESCE(excluded.confidence, external_ids.confidence),
                        provenance = excluded.provenance,
                        last_seen_at = excluded.last_seen_at`,
                ).bind(
                    src, f.provider_ref, workId,
                    confidence, provenance, ts0,
                ));
            }
        }
    }
    await env.COMMENTS_DB.batch(writes);
    if (refConflicts.length > 0) {
        await review(env, 'provider_ref_conflict', {
            work_id: workId, conflicts: refConflicts,
        });
    }

    // Tentative feeds (SeriesMatcher floor-without-margin) merge into
    // the sequence but flag for review — an ambiguous identity should
    // never silently contribute structure.
    const tentative = body.feeds.filter((f) => f.verified === false);
    if (tentative.length > 0) {
        await review(env, 'chapter_feed_tentative', {
            work_id: workId,
            feeds: tentative.map((f) => ({ source_key: f.source_key, score: f.score })),
        });
    }

    const summary = await remergeChapters(env, workId);
    return ok({
        work_id: workId,
        ...summary,
    });
}

/** Stored feed row → the shape the merge loop consumes. */
interface StoredFeed {
    source_key: string;
    chapters: FeedChapter[];
}

/**
 * Rebuild a work's canonical chapter sequence from its ENTIRE stored
 * feed corpus (canonical_feeds). Entries merge by (kind, number|label,
 * part); ≥2 sources → verified. Divergent feeds and kind conflicts go
 * to identity_review (deduped on identical payloads so re-merges don't
 * spam the queue).
 */
export async function remergeChapters(env: Env, workId: string): Promise<{
    count: number;
    verified: number;
    skipped: number;
    conflicts: number;
    divergent: string[];
}> {
    const { results: rows } = await env.COMMENTS_DB.prepare(
        'SELECT source_key, feed_json FROM canonical_feeds WHERE work_id = ?',
    ).bind(workId).all<{ source_key: string; feed_json: string }>();

    const feeds: StoredFeed[] = [];
    for (const r of rows) {
        try {
            feeds.push({ source_key: r.source_key, chapters: JSON.parse(r.feed_json) });
        } catch { /* corrupt feed row — skip, next canonize repairs */ }
    }

    const entries: Entry[] = [];
    const feedNums = new Map<string, Set<number>>();
    let skipped = 0;
    for (const f of feeds) {
        const src = f.source_key;
        const nums = new Set<number>();
        for (const c of f.chapters ?? []) {
            const structured = c.number !== undefined;
            const p = structured
                ? {
                    number: c.number ?? null,
                    volume: c.volume ?? null,
                    part: c.part ?? null,
                    kind: c.kind ?? (c.number == null ? 'extra' : 'chapter'),
                }
                : parseChapter(c.name as string);
            if (p.kind === 'unknown' || p.kind === 'notice') {
                skipped++;
                continue;
            }
            if (p.number !== null) nums.add(p.number);
            entries.push({
                number: p.number,
                volume: p.volume,
                part: p.part,
                kind: p.kind,
                label: p.number === null ? normalize(c.name as string) : (c.name as string).trim(),
                src,
                url: typeof c.url === 'string' ? c.url : null,
                date: typeof c.date_upload === 'number' && c.date_upload > 0 ? c.date_upload : null,
            });
        }
        feedNums.set(src, nums);
    }

    const groups = new Map<string, Entry[]>();
    for (const e of entries) {
        const k = mergeKey(e);
        const g = groups.get(k);
        if (g) g.push(e);
        else groups.set(k, [e]);
    }

    // Kind conflict: same number+part under different kinds ("Ch. 30" vs
    // "Ch. 30 Omake") — keep both, flag for review.
    const byNum = new Map<string, Set<string>>();
    for (const [k, g] of groups) {
        const first = g[0];
        if (first.number === null) continue;
        const nk = `${first.number}|${first.part ?? ''}`;
        const kinds = byNum.get(nk) ?? new Set();
        kinds.add(first.kind);
        byNum.set(nk, kinds);
    }
    const conflicts = [...byNum.values()].filter((s) => s.size > 1).length;
    if (conflicts > 0) {
        await review(env, 'chapter_kind_conflict', { work_id: workId, conflicts });
    }

    // Divergent feed: ≥10 parsed chapters with <50% corroboration.
    // Only substantive feeds (≥MIN themselves) count as corroborating
    // peers — an empty/licensed-removed feed (MangaDex) can't vouch for
    // anything and must not single-source-flag every real feed.
    const allNums = new Map<number, Set<string>>();
    const substantive = new Set<string>();
    for (const [src, nums] of feedNums) {
        if (nums.size < DIVERGENCE_MIN_FEED) continue;
        substantive.add(src);
        for (const n of nums) {
            const s = allNums.get(n) ?? new Set();
            s.add(src);
            allNums.set(n, s);
        }
    }
    const divergent: string[] = [];
    for (const src of substantive) {
        const nums = feedNums.get(src) as Set<number>;
        const corroborated = [...nums].filter((n) => (allNums.get(n)?.size ?? 0) > 1).length;
        if (corroborated / nums.size < DIVERGENCE_THRESHOLD) divergent.push(src);
    }
    if (divergent.length > 0 && substantive.size > 1) {
        await review(env, 'chapter_feed_divergent', { work_id: workId, divergent });
    }

    const merged = [...groups.values()].map((g) => {
        const sources = [...new Set(g.map((e) => e.src))];
        const refs: Record<string, string> = {};
        for (const e of g) if (e.url) refs[e.src] = e.url;
        // Earliest contributing upload date — the true publication time;
        // never the canonization time.
        const dates = g.map((e) => e.date).filter((d): d is number => d !== null);
        const rep = g[0];
        return {
            number: rep.number,
            part: rep.part,
            volume: rep.volume,
            kind: rep.kind,
            label: rep.label,
            confidence: sources.length >= 2 ? 'verified' : 'single',
            sources,
            refs,
            dateUpload: dates.length ? Math.min(...dates) : null,
        };
    });
    merged.sort((a, b) => sortKey(a) - sortKey(b));

    const ts = now();
    const stmts = [
        env.COMMENTS_DB.prepare('DELETE FROM canonical_chapters WHERE work_id = ?').bind(workId),
        ...merged.map((m, i) =>
            env.COMMENTS_DB.prepare(
                `INSERT INTO canonical_chapters
                 (work_id, seq, number, part, volume, kind, label, confidence, sources_json, refs_json, date_upload, updated_at)
                 VALUES (?,?,?,?,?,?,?,?,?,?,?,?)`,
            ).bind(
                workId, i, m.number, m.part, m.volume, m.kind, m.label,
                m.confidence, JSON.stringify(m.sources), JSON.stringify(m.refs),
                m.dateUpload, ts,
            ),
        ),
    ];
    await env.COMMENTS_DB.batch(stmts);

    return {
        count: merged.length,
        verified: merged.filter((m) => m.confidence === 'verified').length,
        skipped,
        conflicts,
        divergent,
    };
}

/** GET /v1/works/:id/chapters — the canonical sequence, empty list if never canonized. */
export async function handleGetChapters(
    _req: Request,
    params: Record<string, string>,
    _url: URL,
    env: Env,
): Promise<Response> {
    const workId = await liveWorkId(env, params.id);
    const { results } = await env.COMMENTS_DB.prepare(
        `SELECT seq, number, part, volume, kind, label, confidence, sources_json, refs_json, date_upload
         FROM canonical_chapters WHERE work_id = ? ORDER BY seq`,
    ).bind(workId).all<{
        seq: number; number: number | null; part: number | null; volume: number | null;
        kind: string; label: string; confidence: string; sources_json: string; refs_json: string;
        date_upload: number | null;
    }>();
    return ok({
        work_id: workId,
        chapters: results.map((r) => ({
            seq: r.seq,
            number: r.number,
            part: r.part,
            volume: r.volume,
            kind: r.kind,
            label: r.label,
            confidence: r.confidence,
            sources: JSON.parse(r.sources_json) as string[],
            refs: JSON.parse(r.refs_json) as Record<string, string>,
            date_upload: r.date_upload,
        })),
    });
}
