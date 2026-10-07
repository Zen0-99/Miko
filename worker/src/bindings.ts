import { identityFrom } from './auth';
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
 * that reopens it, provenance, lifecycle status and confirm points.
 * `__none__` tombstones are resolve markers, not carriers — excluded.
 *
 * Provisional rows are visible only to their minter (optional Bearer);
 * quarantined rows are listed flagged to everyone for transparency —
 * this is a LISTING predicate: the strict serve gate lives on the
 * content-driving reads (resolve ext-id JOIN, externalIdsFor, feed
 * overlay — SD-8).
 */
export async function handleGetBindings(
    req: Request,
    params: Record<string, string>,
    _url: URL,
    env: Env,
): Promise<Response> {
    const workId = await liveWorkId(env, params.id);
    // Optional — readers need no auth; a Bearer unlocks the caller's own
    // provisional rows and sets `mine` + the per-row `my` vote state.
    const me = await identityFrom(req, env);
    const { results } = await env.COMMENTS_DB.prepare(
        `SELECT provider, external_id, confidence, provenance, last_seen_at,
                status, minter_device_hash, points
         FROM external_ids
         WHERE miko_work_id = ? AND external_id != '__none__'
           AND (status != 'provisional' OR minter_device_hash = ?)
         ORDER BY (CASE status WHEN 'confirmed' THEN 0 WHEN 'provisional' THEN 1 ELSE 2 END),
                  points DESC, external_id ASC`,
    ).bind(workId, me?.device_hash ?? '').all<{
        provider: string; external_id: string; confidence: number | null;
        provenance: string | null; last_seen_at: number | null;
        status: string; minter_device_hash: string | null; points: number;
    }>();

    // Per-device signal state (20-05): the picker's vote/report/remove
    // toggles render from `my` without a second call. Unauthenticated
    // callers get my:null — no identity, no state.
    const myByRef = new Map<string, { vote: string | null; reported: boolean; removed: boolean }>();
    if (me != null) {
        const [votes, signals] = await Promise.all([
            env.COMMENTS_DB.prepare(
                `SELECT provider, external_id FROM binding_confirms
                 WHERE miko_work_id = ? AND device_hash = ?`,
            ).bind(workId, me.device_hash).all<{ provider: string; external_id: string }>(),
            env.COMMENTS_DB.prepare(
                `SELECT provider, external_id, kind FROM binding_reports
                 WHERE miko_work_id = ? AND device_hash = ?`,
            ).bind(workId, me.device_hash).all<{
                provider: string; external_id: string; kind: string;
            }>(),
        ]);
        const key = (p: string, r: string) => `${p}${r}`;
        for (const v of votes.results) {
            const m = myByRef.get(key(v.provider, v.external_id)) ??
                { vote: null, reported: false, removed: false };
            m.vote = 'up';
            myByRef.set(key(v.provider, v.external_id), m);
        }
        for (const s of signals.results) {
            const m = myByRef.get(key(s.provider, s.external_id)) ??
                { vote: null, reported: false, removed: false };
            if (s.kind === 'downvote') m.vote = 'down';
            else if (s.kind === 'report') m.reported = true;
            else if (s.kind === 'remove') m.removed = true;
            myByRef.set(key(s.provider, s.external_id), m);
        }
    }

    return ok({
        work_id: workId,
        bindings: results.map((r) => {
            const mine = r.minter_device_hash != null && r.minter_device_hash === me?.device_hash;
            const my = myByRef.get(`${r.provider}${r.external_id}`) ?? null;
            return {
                source_key: r.provider,
                provider_ref: r.external_id,
                confidence: r.confidence,
                provenance: r.provenance,
                last_seen_at: r.last_seen_at,
                status: r.status,
                mine,
                points: r.points,
                my,
            };
        }),
    });
}
