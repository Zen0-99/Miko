import { identityFrom, requireIdentity } from './auth';
import { liveWorkId } from './bindings';
import type { Env } from './env';
import { isTrustedMinter } from './trust';
import { checkWrite } from './strikes';
import { err, now, ok } from './util';

/**
 * Volume segmentation (Phase 21-01 / NCM-016).
 *
 * GET /v1/works/:id/volumes — one payload per work:
 *   volumes[]   per-(volume, language) cover + edition metadata
 *               (MangaBaka covers/works seed; MangaDex gap-fill)
 *   boundaries  merged chapter→volume map + mean confidence, written
 *               only by the harvester's seed POST — the worker never
 *               derives boundaries itself.
 *   finalVolume / coversCount ride along from works_cache.
 * A Bearer caller on an unmapped work enqueues `volume_seed_queue` —
 * the tail beyond the top-500 harvest feeds back here.
 *
 * POST /v1/works/:id/volumes/seed — maintainer lane (trusted_minters),
 * upsert-only; re-seeding a work is a re-vote, not an error.
 */

interface SeedVolume {
    volume_no: number;
    language: string;
    cover_url?: string | null;
    cover_thumb_url?: string | null;
    cover_blurhash?: string | null;
    isbn?: string | null;
    release_date?: string | null;
    pages?: number | null;
    source: string;
}

interface SeedBoundary {
    chapter: number;
    volume: number;
    confidence: number;
    scheme?: string;
    sources?: string[];
}

const SEED_SOURCES = new Set([
    'mangabaka', 'mangadex', 'wiki', 'cubari', 'feed_markers', 'comick', 'viz',
]);

export async function handleGetWorkVolumes(
    req: Request,
    params: Record<string, string>,
    _url: URL,
    env: Env,
): Promise<Response> {
    const workId = await liveWorkId(env, params.id);
    const me = await identityFrom(req, env);

    const work = await env.COMMENTS_DB.prepare(
        'SELECT final_volume, covers_count FROM works_cache WHERE miko_work_id = ?',
    ).bind(workId).first<{ final_volume: number | null; covers_count: number | null }>();
    if (!work) return err(404, 'not_found');

    const { results: vols } = await env.COMMENTS_DB.prepare(
        `SELECT volume_no, language, cover_url, cover_thumb_url, cover_blurhash,
                isbn, release_date, pages, source
         FROM work_volumes WHERE work_id = ?
         ORDER BY volume_no ASC, language ASC`,
    ).bind(workId).all();

    const { results: bounds } = await env.COMMENTS_DB.prepare(
        `SELECT chapter, volume_no, confidence, scheme, sources
         FROM work_volume_chapters WHERE work_id = ? ORDER BY chapter ASC`,
    ).bind(workId).all();

    // Tail feed — an authorized read of an unmapped work queues it for
    // the next harvest run. Idempotent; unauthenticated reads don't queue.
    if (me && (vols ?? []).length === 0 && (bounds ?? []).length === 0) {
        await env.COMMENTS_DB.prepare(
            'INSERT OR IGNORE INTO volume_seed_queue (work_id, requested_at, device_hash) VALUES (?, ?, ?)',
        ).bind(workId, now(), me.device_hash).run();
    }

    const boundaries: Record<string, number> = {};
    let confSum = 0;
    let confN = 0;
    for (const b of bounds ?? []) {
        const row = b as { chapter: number; volume_no: number; confidence: number };
        boundaries[String(row.chapter)] = row.volume_no;
        confSum += row.confidence;
        confN += 1;
    }

    return ok({
        work_id: workId,
        final_volume: work.final_volume,
        covers_count: work.covers_count,
        volumes: vols ?? [],
        boundaries,
        boundary_confidence: confN ? confSum / confN : null,
        queued_for_seed: (vols ?? []).length === 0 && (bounds ?? []).length === 0,
    });
}

export async function handleSeedWorkVolumes(
    req: Request,
    params: Record<string, string>,
    _url: URL,
    env: Env,
): Promise<Response> {
    const id = await requireIdentity(req, env);
    if (id instanceof Response) return id;
    const gate = await checkWrite(env, id.device_hash);
    if (!gate.allowed) return err(403, 'suspended', { suspended_until: gate.suspended_until });
    // Maintainer lane — boundary truth is shared-catalog data; only the
    // trusted-minter allowlist pushes seeds.
    if (!(await isTrustedMinter(env, id.device_hash))) return err(403, 'forbidden');

    const workId = await liveWorkId(env, params.id);
    let body: {
        volumes?: SeedVolume[];
        boundaries?: SeedBoundary[];
        final_volume?: number | null;
        covers_count?: number | null;
    };
    try {
        body = await req.json();
    } catch {
        return err(400, 'bad_json');
    }

    const vols = Array.isArray(body.volumes) ? body.volumes.slice(0, 500) : [];
    const bounds = Array.isArray(body.boundaries) ? body.boundaries.slice(0, 5000) : [];
    if (vols.length + bounds.length === 0 && body.final_volume == null) {
        return err(400, 'bad_request');
    }
    for (const v of vols) {
        if (typeof v?.volume_no !== 'number' || typeof v?.language !== 'string' ||
            !v.language || !SEED_SOURCES.has(v?.source)) return err(400, 'bad_volume');
    }
    for (const b of bounds) {
        if (typeof b?.chapter !== 'number' || typeof b?.volume !== 'number' ||
            typeof b?.confidence !== 'number' || b.confidence < 0 || b.confidence > 1) {
            return err(400, 'bad_boundary');
        }
    }

    const t = now();
    const stmts: D1PreparedStatement[] = [];
    for (const v of vols) {
        stmts.push(env.COMMENTS_DB.prepare(
            `INSERT OR REPLACE INTO work_volumes
             (work_id, volume_no, language, cover_url, cover_thumb_url,
              cover_blurhash, isbn, release_date, pages, source, updated_at)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        ).bind(
            workId, v.volume_no, v.language,
            v.cover_url ?? null, v.cover_thumb_url ?? null, v.cover_blurhash ?? null,
            v.isbn ?? null, v.release_date ?? null, v.pages ?? null,
            v.source, t,
        ));
    }
    for (const b of bounds) {
        // Re-vote semantics: the winner keeps its volume/scheme unless the
        // incoming vote is at least as confident; confidence can only rise;
        // provenance unions so losing votes never disappear.
        stmts.push(env.COMMENTS_DB.prepare(
            `INSERT INTO work_volume_chapters
             (work_id, chapter, volume_no, confidence, scheme, sources)
             VALUES (?, ?, ?, ?, ?, ?)
             ON CONFLICT (work_id, chapter) DO UPDATE SET
                 volume_no = CASE WHEN excluded.confidence >= work_volume_chapters.confidence
                                  THEN excluded.volume_no ELSE work_volume_chapters.volume_no END,
                 confidence = MAX(work_volume_chapters.confidence, excluded.confidence),
                 scheme = CASE WHEN excluded.confidence >= work_volume_chapters.confidence
                               THEN excluded.scheme ELSE work_volume_chapters.scheme END,
                 sources = (
                     SELECT json_group_array(value) FROM (
                         SELECT value FROM json_each(work_volume_chapters.sources)
                         UNION
                         SELECT value FROM json_each(excluded.sources)
                     )
                 )`,
        ).bind(
            workId, b.chapter, b.volume, b.confidence,
            b.scheme ?? 'tankobon', JSON.stringify(b.sources ?? []),
        ));
    }
    if (body.final_volume != null || body.covers_count != null) {
        stmts.push(env.COMMENTS_DB.prepare(
            `UPDATE works_cache
             SET final_volume = COALESCE(?, final_volume),
                 covers_count = COALESCE(?, covers_count)
             WHERE miko_work_id = ?`,
        ).bind(body.final_volume ?? null, body.covers_count ?? null, workId));
    }
    // A seeded work never stays queued.
    stmts.push(env.COMMENTS_DB.prepare(
        'DELETE FROM volume_seed_queue WHERE work_id = ?',
    ).bind(workId));

    if (stmts.length) await env.COMMENTS_DB.batch(stmts);
    return ok({
        work_id: workId,
        volumes_upserted: vols.length,
        boundaries_upserted: bounds.length,
    });
}
