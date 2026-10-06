import { requireIdentity } from './auth';
import { liveWorkId } from './bindings';
import type { Env } from './env';
import { recomputeProviderStats } from './providerstats';
import { RULESET } from './ruleset';
import { checkWrite } from './strikes';
import {
    applyProviderDerank,
    evaluateBindingRate,
    quarantineBinding,
    recordBindingRate,
    recomputePoints,
    remergeForWork,
    syncFeedPending,
} from './trust';
import { err, now, ok } from './util';

const DAY_MS = 86_400_000;

/**
 * Report & self-revoke endpoints (Phase 20-02 / NCM-014).
 *
 * POST /v1/bindings/report clones the comment system's handleReport:
 * identity → suspension → rate → validation → liveWorkId →
 * target-exists → ledger → weighted threshold → quarantine. Weight
 * comes from reporter age and dedupe rides the
 * (provider, external_id, device_hash) PK — a second report by the
 * same device is a no-op, never double-weights.
 *
 * POST /v1/bindings/unbind is "wrong work? unbind": the minter DELETES
 * their own provisional row (freeing the (provider, ref) PK for a
 * correct re-mint) and the event lands in binding_reports as a
 * zero-weight audit row — self-revocation is the minter fixing their
 * own mistake, not provider malfeasance (SD-13).
 */

/** SD-16 report kinds; 'unbind' is reserved for the unbind path
 *  (rejected client-supplied) and bare 'report' is the absent default. */
const REPORT_KINDS = new Set(['report', 'wrong_work', 'dead_feed', 'low_quality', 'other']);

export async function handleReportBinding(req: Request, env: Env): Promise<Response> {
    const id = await requireIdentity(req, env);
    if (id instanceof Response) return id;
    const gate = await checkWrite(env, id.device_hash);
    if (!gate.allowed) return err(403, 'suspended', { suspended_until: gate.suspended_until });
    const rate = await evaluateBindingRate(env, id, 'report');
    if (!rate.ok) return err(429, rate.rule);

    let body: { work_id?: string; provider?: string; provider_ref?: string; kind?: string };
    try {
        body = await req.json();
    } catch {
        return err(400, 'bad_json');
    }
    if (typeof body.work_id !== 'string' || !body.work_id) return err(400, 'bad_request');
    if (typeof body.provider !== 'string' || !body.provider || body.provider.length > 100) {
        return err(400, 'bad_request');
    }
    if (typeof body.provider_ref !== 'string' || !body.provider_ref || body.provider_ref.length > 500) {
        return err(400, 'bad_request');
    }
    const kind = body.kind ?? 'report';
    if (!REPORT_KINDS.has(kind)) return err(400, 'bad_request');
    // '__none__' tombstones assert absence — nothing to report.
    if (body.provider_ref === '__none__') return err(404, 'not_found');

    // Dead-id report targets redirect onto the merged live work.
    const workId = await liveWorkId(env, body.work_id);
    // Any status is reportable (SD-14) — quarantining a confirmed
    // binding is the escape hatch for a bad incumbent.
    const target = await env.COMMENTS_DB.prepare(
        `SELECT status FROM external_ids
         WHERE provider = ? AND external_id = ? AND miko_work_id = ?`,
    ).bind(body.provider, body.provider_ref, workId).first<{ status: string }>();
    if (!target) return err(404, 'not_found');

    // Rate row rides the attempt — after the gates pass, before the
    // ledger write (SD-15).
    await recordBindingRate(env, id.device_hash, 'report');

    // Reporter-age weighting (SD-11): established devices outweigh
    // fresh ones — comment-system values, ruleset-keyed.
    const b = RULESET.bindings;
    const weight = now() - id.created_at >= (b.reporter_age_full_ms ?? 30 * DAY_MS)
        ? (b.report_weight_established ?? 1.0)
        : (b.report_weight_new_device ?? 0.25);
    const ins = await env.COMMENTS_DB.prepare(
        `INSERT OR IGNORE INTO binding_reports
         (provider, external_id, miko_work_id, device_hash, weight, kind, created_at)
         VALUES (?,?,?,?,?,?,?)`,
    ).bind(body.provider, body.provider_ref, workId, id.device_hash, weight, kind, now()).run();
    if ((ins.meta.changes ?? 0) === 0) {
        // PK dedupe — a second report by this device never
        // double-weights; skip the threshold recompute entirely.
        return ok({ reported: true, already: true });
    }

    await recomputePoints(env, body.provider, body.provider_ref);
    const total = (await env.COMMENTS_DB.prepare(
        `SELECT COALESCE(SUM(weight),0) AS w FROM binding_reports
         WHERE provider = ? AND external_id = ?`,
    ).bind(body.provider, body.provider_ref).first<{ w: number }>())?.w ?? 0;
    let quarantined = false;
    if (total >= b.report_quarantine_weight) {
        quarantined = await quarantineBinding(env, body.provider, body.provider_ref, workId);
    }
    await applyProviderDerank(env, body.provider);
    // The accepted report row moved the provider's ledger sums — full
    // recompute (20-04; report_weight/confirm_count/quarantined_count
    // and the dynamic_tier evaluation all derive in one write).
    await recomputeProviderStats(env, body.provider);
    return ok(quarantined ? { reported: true, quarantined: true } : { reported: true });
}

export async function handleUnbindBinding(req: Request, env: Env): Promise<Response> {
    const id = await requireIdentity(req, env);
    if (id instanceof Response) return id;
    // checkWrite applies here too — a suspended device may not mutate
    // anything, including its own rows (SD-13); its provisional mints
    // age out harmlessly via reports/quarantine anyway.
    const gate = await checkWrite(env, id.device_hash);
    if (!gate.allowed) return err(403, 'suspended', { suspended_until: gate.suspended_until });
    const rate = await evaluateBindingRate(env, id, 'unbind');
    if (!rate.ok) return err(429, rate.rule);

    let body: { work_id?: string; provider?: string; provider_ref?: string };
    try {
        body = await req.json();
    } catch {
        return err(400, 'bad_json');
    }
    if (typeof body.work_id !== 'string' || !body.work_id) return err(400, 'bad_request');
    if (typeof body.provider !== 'string' || !body.provider || body.provider.length > 100) {
        return err(400, 'bad_request');
    }
    if (typeof body.provider_ref !== 'string' || !body.provider_ref || body.provider_ref.length > 500) {
        return err(400, 'bad_request');
    }

    const workId = await liveWorkId(env, body.work_id);
    // Self-service revoke only: the caller's OWN provisional row —
    // confirmed rows, other devices' rows and NULL-minter rows all
    // 404 (SD-13).
    const target = await env.COMMENTS_DB.prepare(
        `SELECT status, minter_device_hash FROM external_ids
         WHERE provider = ? AND external_id = ? AND miko_work_id = ?`,
    ).bind(body.provider, body.provider_ref, workId)
        .first<{ status: string; minter_device_hash: string | null }>();
    if (target == null || target.status !== 'provisional' ||
        target.minter_device_hash !== id.device_hash) {
        return err(404, 'not_found');
    }
    await recordBindingRate(env, id.device_hash, 'unbind');

    // Delete, don't quarantine — the freed (provider, ref) PK lets a
    // correct re-mint land clean. The guarded delete covers a raced
    // status flip: a row the caller no longer owns can't be removed.
    const del = await env.COMMENTS_DB.prepare(
        `DELETE FROM external_ids
         WHERE provider = ? AND external_id = ?
           AND status = 'provisional' AND minter_device_hash = ?`,
    ).bind(body.provider, body.provider_ref, id.device_hash).run();
    if ((del.meta.changes ?? 0) === 0) return err(404, 'not_found');
    await env.COMMENTS_DB.prepare(
        'DELETE FROM binding_confirms WHERE provider = ? AND external_id = ?',
    ).bind(body.provider, body.provider_ref).run();

    // Feed cleanup (SD-13): the caller's own submitted feed goes only
    // when no binding remains to back it — a feed row submitted by a
    // different device (REPLACE-shared (work_id, source_key) rows) is
    // NEVER deleted on this unbind; the fallback is always the
    // coverage resync.
    await env.COMMENTS_DB.prepare(
        `DELETE FROM canonical_feeds
         WHERE work_id = ? AND source_key = ? AND submitter_device_hash = ?
           AND NOT EXISTS (
               SELECT 1 FROM external_ids
               WHERE miko_work_id = ? AND provider = ?)`,
    ).bind(workId, body.provider, id.device_hash, workId, body.provider).run();
    await syncFeedPending(env, workId, body.provider);
    await remergeForWork(env, workId);

    // Audit row — INSERT OR IGNORE is load-bearing, not stylistic: the
    // (provider, external_id, device_hash) PK may already hold this
    // device's row (a prior self-report — reporting your own
    // provisional mint is allowed — or an earlier unbind of a
    // re-minted ref) and a constraint throw HERE would land after the
    // deletes committed, returning a 503 with side effects. weight=0
    // keeps self-revocation out of quarantine thresholds and provider
    // derank sums by construction.
    await env.COMMENTS_DB.prepare(
        `INSERT OR IGNORE INTO binding_reports
         (provider, external_id, miko_work_id, device_hash, weight, kind, created_at)
         VALUES (?,?,?,?,0,'unbind',?)`,
    ).bind(body.provider, body.provider_ref, workId, id.device_hash, now()).run();
    // The delete dropped a device-attributed row + its confirms —
    // recompute the provider's counts (20-04).
    await recomputeProviderStats(env, body.provider);
    return ok({ unbound: true });
}
