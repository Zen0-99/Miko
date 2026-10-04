import type { Env } from './env';
import { ok } from './util';

/**
 * Follow work_merges — a catalog dedupe leaves the dead id as an alias:
 * canonize targets, chapter lists, bindings and resolve hits on a dead
 * id all land on the live work.
 */
export async function liveWorkId(env: Env, id: string): Promise<string> {
    const row = await env.COMMENTS_DB.prepare(
        'SELECT live_id FROM work_merges WHERE dead_id = ?',
    ).bind(id).first<{ live_id: string }>();
    return row?.live_id ?? id;
}

/**
 * GET /v1/works/:id/bindings — the work's carrier list: which providers
 * (mangadex / extension pkg names) hold an entry for it, with the ref
 * that reopens it, provenance, and freshness. `__none__` tombstones are
 * resolve markers, not carriers — excluded.
 */
export async function handleGetBindings(
    _req: Request,
    params: Record<string, string>,
    _url: URL,
    env: Env,
): Promise<Response> {
    const workId = await liveWorkId(env, params.id);
    const { results } = await env.COMMENTS_DB.prepare(
        `SELECT provider, external_id, confidence, provenance, last_seen_at
         FROM external_ids
         WHERE miko_work_id = ? AND external_id != '__none__'
         ORDER BY provider`,
    ).bind(workId).all<{
        provider: string; external_id: string; confidence: number | null;
        provenance: string | null; last_seen_at: number | null;
    }>();
    return ok({
        work_id: workId,
        bindings: results.map((r) => ({
            source_key: r.provider,
            provider_ref: r.external_id,
            confidence: r.confidence,
            provenance: r.provenance,
            last_seen_at: r.last_seen_at,
        })),
    });
}
