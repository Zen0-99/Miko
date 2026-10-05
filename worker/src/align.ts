import type { Env } from './env';
import { referenceChapters } from './mangadex';
import { err, now, ok, sha256Hex } from './util';

const ALIGN_THRESHOLD = 0.6;
const PADDING_FACTOR = 3;

interface AlignRequest {
    work_id?: string;
    source_key?: string;
    medium?: string;
    title?: string;
    count?: number;
    numbers?: number[];
    names_hash?: string;
}

/**
 * POST /v1/chapters/align — score a source's chapter list against the
 * best available reference and persist the verdict. Chapter threads for
 * a (work, source) pair are enabled only when status is not misaligned
 * (10-CONTEXT.md).
 */
export async function handleAlign(req: Request, env: Env): Promise<Response> {
    let body: AlignRequest;
    try {
        body = await req.json<AlignRequest>();
    } catch {
        return err(400, 'bad_json');
    }
    const workId = body.work_id;
    const sourceKey = body.source_key;
    const numbers = body.numbers;
    if (
        typeof workId !== 'string' || !workId ||
        typeof sourceKey !== 'string' || !sourceKey ||
        !Array.isArray(numbers) || numbers.length === 0 ||
        numbers.some((n) => typeof n !== 'number' || !Number.isFinite(n))
    ) {
        return err(400, 'bad_request');
    }
    // Phase 19: shows align against canonical_episodes + sibling
    // episode_* tables; every other medium keeps the chapter_* pool.
    const medium = body.medium;
    const fpTable = medium === 'shows' ? 'episode_fingerprints' : 'chapter_fingerprints';
    const alTable = medium === 'shows' ? 'episode_alignments' : 'chapter_alignments';

    // Numbering exceptions applied before hashing/scoring.
    const { results: aliases } = await env.COMMENTS_DB.prepare(
        'SELECT reported, canonical FROM chapter_aliases WHERE work_id = ? AND source_key = ?',
    ).bind(workId, sourceKey).all<{ reported: number; canonical: number }>();
    const aliasMap = new Map(aliases.map((a) => [a.reported, a.canonical]));
    const aliased = numbers.map((n) => aliasMap.get(n) ?? n).sort((a, b) => a - b);

    const fingerprintHash = await sha256Hex(
        `${aliased.join(',')}|${body.names_hash ?? ''}`,
    );

    // Cached verdict on the exact fingerprint.
    const cached = await env.COMMENTS_DB.prepare(
        `SELECT status, score, reference FROM ${alTable}
         WHERE work_id = ? AND source_key = ? AND fingerprint_hash = ?`,
    ).bind(workId, sourceKey, fingerprintHash)
        .first<{ status: string; score: number; reference: string }>();
    if (cached) {
        return ok({ status: cached.status, score: cached.score, reference: cached.reference });
    }

    // Fingerprint always recorded — it feeds consensus even when the
    // verdict ends up `unknown`.
    const ts = now();
    await env.COMMENTS_DB.prepare(
        `INSERT INTO ${fpTable}
         (work_id, source_key, fingerprint_hash, count, numbers_json, names_hash, first_seen, last_seen)
         VALUES (?,?,?,?,?,?,?,?)
         ON CONFLICT(work_id, source_key, fingerprint_hash)
         DO UPDATE SET last_seen = excluded.last_seen`,
    ).bind(
        workId, sourceKey, fingerprintHash, aliased.length,
        JSON.stringify(aliased), body.names_hash ?? '', ts, ts,
    ).run();

    // Reference: canonical episode abs numbers for shows; MangaDex for
    // manga; inter-source consensus otherwise (and as the fallback when
    // the primary reference can't produce a set).
    let reference: Set<number> | null = null;
    let referenceKind = 'none';
    if (medium === 'shows') {
        const { results } = await env.COMMENTS_DB.prepare(
            `SELECT abs_number FROM canonical_episodes
             WHERE work_id = ? AND kind = 'episode' AND abs_number IS NOT NULL
             ORDER BY abs_number`,
        ).bind(workId).all<{ abs_number: number }>();
        if (results.length > 0) {
            reference = new Set(results.map((r) => r.abs_number));
            referenceKind = 'canonical_episodes';
        }
    }
    if (!reference && medium === 'manga' && typeof body.title === 'string' && body.title) {
        reference = await referenceChapters(body.title, env.MANGADEX_BASE);
        if (reference) referenceKind = 'mangadex';
    }
    if (!reference) {
        reference = await consensus(env, workId, sourceKey, fpTable);
        if (reference) referenceKind = 'consensus';
    }

    let status = 'unknown';
    let score: number | null = null;
    if (reference && reference.size > 0) {
        const hits = aliased.filter((n) => reference.has(n)).length;
        score = hits / aliased.length;
        // A source reporting far more chapters than the reference is
        // likely padding (split pages, extras) — cap the score so it
        // can't align by volume alone.
        if (aliased.length > PADDING_FACTOR * reference.size) {
            score = Math.min(score, 0.5);
        }
        status = score >= ALIGN_THRESHOLD ? 'aligned' : 'misaligned';
    }

    await env.COMMENTS_DB.prepare(
        `INSERT INTO ${alTable}
         (work_id, source_key, fingerprint_hash, status, score, reference, checked_at)
         VALUES (?,?,?,?,?,?,?)`,
    ).bind(workId, sourceKey, fingerprintHash, status, score, referenceKind, ts).run();

    return ok({ status, score, reference: referenceKind });
}

/**
 * Numbers reported by ≥2 distinct other sources for this work.
 * Excludes the requesting source so it can't self-confirm.
 */
async function consensus(
    env: Env,
    workId: string,
    sourceKey: string,
    fpTable: string,
): Promise<Set<number> | null> {
    const { results } = await env.COMMENTS_DB.prepare(
        `SELECT DISTINCT source_key, numbers_json FROM ${fpTable}
         WHERE work_id = ? AND source_key != ?`,
    ).bind(workId, sourceKey).all<{ source_key: string; numbers_json: string }>();
    if (results.length < 2) return null;

    const counts = new Map<number, number>();
    for (const row of results) {
        const nums = new Set<number>(JSON.parse(row.numbers_json) as number[]);
        for (const n of nums) counts.set(n, (counts.get(n) ?? 0) + 1);
    }
    const agreed = new Set([...counts.entries()].filter(([, c]) => c >= 2).map(([n]) => n));
    return agreed.size > 0 ? agreed : null;
}
