import { parseChapter } from './chapterparse';
import type { Env } from './env';
import { normalize } from './normalize';
import { err, now, ok } from './util';

const MAX_FEEDS = 20;
const MAX_CHAPTERS_PER_FEED = 5000;
const MAX_NAME = 500;
const DIVERGENCE_MIN_FEED = 10;
const DIVERGENCE_THRESHOLD = 0.5;

interface FeedChapter {
    name?: string;
    url?: string;
}

interface CanonizeFeed {
    source_key?: string;
    tier?: string;
    /** Series-matcher provenance (13-01/13-02): feeds that cleared the
     * floor but not the margin arrive as tentative — they merge but
     * also write an identity_review row. */
    verified?: boolean;
    score?: number;
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
    await env.COMMENTS_DB.prepare(
        'INSERT INTO identity_review (kind, payload, created_at) VALUES (?,?,?)',
    ).bind(kind, JSON.stringify(payload), now()).run();
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
    const workId = body.work_id;
    if (typeof workId !== 'string' || !workId) return err(400, 'bad_request');
    if (!Array.isArray(body.feeds) || body.feeds.length === 0 || body.feeds.length > MAX_FEEDS) {
        return err(400, 'bad_request');
    }
    for (const f of body.feeds) {
        if (typeof f.source_key !== 'string' || !f.source_key) return err(400, 'bad_request');
        if (!Array.isArray(f.chapters) || f.chapters.length > MAX_CHAPTERS_PER_FEED) {
            return err(400, 'bad_request');
        }
        for (const c of f.chapters) {
            if (typeof c.name !== 'string' || c.name.length > MAX_NAME) return err(400, 'bad_request');
        }
    }

    const entries: Entry[] = [];
    const feedNums = new Map<string, Set<number>>();
    let skipped = 0;
    for (const f of body.feeds) {
        const src = f.source_key as string;
        const nums = new Set<number>();
        for (const c of f.chapters ?? []) {
            const p = parseChapter(c.name as string);
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
    const allNums = new Map<number, Set<string>>();
    for (const [src, nums] of feedNums) {
        for (const n of nums) {
            const s = allNums.get(n) ?? new Set();
            s.add(src);
            allNums.set(n, s);
        }
    }
    const divergent: string[] = [];
    for (const [src, nums] of feedNums) {
        if (nums.size < DIVERGENCE_MIN_FEED) continue;
        const corroborated = [...nums].filter((n) => (allNums.get(n)?.size ?? 0) > 1).length;
        if (corroborated / nums.size < DIVERGENCE_THRESHOLD) divergent.push(src);
    }
    if (divergent.length > 0 && feedNums.size > 1) {
        await review(env, 'chapter_feed_divergent', { work_id: workId, divergent });
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

    const merged = [...groups.values()].map((g) => {
        const sources = [...new Set(g.map((e) => e.src))];
        const refs: Record<string, string> = {};
        for (const e of g) if (e.url) refs[e.src] = e.url;
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
        };
    });
    merged.sort((a, b) => sortKey(a) - sortKey(b));

    const ts = now();
    const stmts = [
        env.COMMENTS_DB.prepare('DELETE FROM canonical_chapters WHERE work_id = ?').bind(workId),
        ...merged.map((m, i) =>
            env.COMMENTS_DB.prepare(
                `INSERT INTO canonical_chapters
                 (work_id, seq, number, part, volume, kind, label, confidence, sources_json, refs_json, updated_at)
                 VALUES (?,?,?,?,?,?,?,?,?,?,?)`,
            ).bind(
                workId, i, m.number, m.part, m.volume, m.kind, m.label,
                m.confidence, JSON.stringify(m.sources), JSON.stringify(m.refs), ts,
            ),
        ),
    ];
    await env.COMMENTS_DB.batch(stmts);

    return ok({
        work_id: workId,
        count: merged.length,
        verified: merged.filter((m) => m.confidence === 'verified').length,
        skipped,
        conflicts,
        divergent,
    });
}

/** GET /v1/works/:id/chapters — the canonical sequence, empty list if never canonized. */
export async function handleGetChapters(
    _req: Request,
    params: Record<string, string>,
    _url: URL,
    env: Env,
): Promise<Response> {
    const workId = params.id;
    const { results } = await env.COMMENTS_DB.prepare(
        `SELECT seq, number, part, volume, kind, label, confidence, sources_json, refs_json
         FROM canonical_chapters WHERE work_id = ? ORDER BY seq`,
    ).bind(workId).all<{
        seq: number; number: number | null; part: number | null; volume: number | null;
        kind: string; label: string; confidence: string; sources_json: string; refs_json: string;
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
        })),
    });
}
