import { requireIdentity } from './auth';
import { liveWorkId } from './bindings';
import type { Env } from './env';
import { recomputeProviderStats } from './providerstats';
import { RULESET } from './ruleset';
import { checkWrite } from './strikes';
import {
    applyProviderDerank,
    evaluateBindingRate,
    maybeSolidify,
    recordBindingRate,
    recomputePoints,
} from './trust';
import { err, now, ok } from './util';

/**
 * Carrier vote & remove-signal endpoints (Phase 20-05 / NCM-015).
 *
 * POST /v1/bindings/vote owns the MUTUALLY-EXCLUSIVE vote slot: a device
 * holds up (+1 — a binding_confirms row, the same ledger a mint's
 * self-seed writes) XOR down (a kind='downvote' flat-weight signal)
 * XOR nothing. Switching deletes the opposite signal so the pair can
 * never coexist; 'clear' empties the slot entirely.
 *
 * POST /v1/bindings/remove records the stackable kind='remove' signal
 * (−remove_weight) — the local half of "remove" (the carrier_exclusions
 * row that hides the provider on this entry) is client-side and never
 * reaches the worker; this endpoint only moves the ledger. removed=false
 * is the retract path for Find-Providers restore.
 *
 * Reports stay on /v1/bindings/report — a reasoned 'report' row stacks
 * with the vote slot and the remove signal (one device: max −1.5).
 */

type VoteAction = 'up' | 'down' | 'clear';

export async function handleVoteBinding(req: Request, env: Env): Promise<Response> {
    const id = await requireIdentity(req, env);
    if (id instanceof Response) return id;
    const gate = await checkWrite(env, id.device_hash);
    if (!gate.allowed) return err(403, 'suspended', { suspended_until: gate.suspended_until });
    const rate = await evaluateBindingRate(env, id, 'vote');
    if (!rate.ok) return err(429, rate.rule);

    let body: { work_id?: string; provider?: string; provider_ref?: string; vote?: string };
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
    const vote = body.vote;
    if (vote !== 'up' && vote !== 'down' && vote !== 'clear') return err(400, 'bad_request');
    if (body.provider_ref === '__none__') return err(404, 'not_found');

    const workId = await liveWorkId(env, body.work_id);
    // Any status is votable (SD-14) — downvoting a confirmed incumbent
    // is the legitimate "this binding is wrong" path.
    const target = await env.COMMENTS_DB.prepare(
        `SELECT status FROM external_ids
         WHERE provider = ? AND external_id = ? AND miko_work_id = ?`,
    ).bind(body.provider, body.provider_ref, workId).first<{ status: string }>();
    if (!target) return err(404, 'not_found');

    // Rate row rides the attempt — after the gates pass, before the
    // ledger write (SD-15).
    await recordBindingRate(env, id.device_hash, 'vote');

    const stmts: D1PreparedStatement[] = [];
    if (vote !== 'down') {
        // 'up' and 'clear' both vacate the down slot — mutual
        // exclusivity is enforced by the paired delete, not app logic.
        stmts.push(env.COMMENTS_DB.prepare(
            `DELETE FROM binding_reports
             WHERE provider = ? AND external_id = ? AND device_hash = ? AND kind = 'downvote'`,
        ).bind(body.provider, body.provider_ref, id.device_hash));
    }
    if (vote !== 'up') {
        // 'down' and 'clear' both vacate the up slot.
        stmts.push(env.COMMENTS_DB.prepare(
            `DELETE FROM binding_confirms
             WHERE provider = ? AND external_id = ? AND device_hash = ?`,
        ).bind(body.provider, body.provider_ref, id.device_hash));
    }
    if (vote === 'up') {
        stmts.push(env.COMMENTS_DB.prepare(
            `INSERT OR IGNORE INTO binding_confirms
             (provider, external_id, miko_work_id, device_hash, created_at)
             VALUES (?,?,?,?,?)`,
        ).bind(body.provider, body.provider_ref, workId, id.device_hash, now()));
    }
    if (vote === 'down') {
        stmts.push(env.COMMENTS_DB.prepare(
            `INSERT OR IGNORE INTO binding_reports
             (provider, external_id, miko_work_id, device_hash, weight, kind, reason, created_at)
             VALUES (?,?,?,?,?,'downvote',NULL,?)`,
        ).bind(
            body.provider, body.provider_ref, workId, id.device_hash,
            RULESET.bindings.downvote_weight ?? 0.25, now(),
        ));
    }
    await env.COMMENTS_DB.batch(stmts);

    await recomputePoints(env, body.provider, body.provider_ref);
    // An upvote can tip a provisional over the margin; 'down'/'clear'
    // can strand one — solidify is one-way, so no demote path needed.
    const solidified = await maybeSolidify(env, body.provider, body.provider_ref, workId);
    // Confirm rows are ledger-visible to provider stats (confirm_count).
    await recomputeProviderStats(env, body.provider);
    return ok(solidified ? { voted: true, vote, solidified: true } : { voted: true, vote });
}

export async function handleRemoveBinding(req: Request, env: Env): Promise<Response> {
    const id = await requireIdentity(req, env);
    if (id instanceof Response) return id;
    const gate = await checkWrite(env, id.device_hash);
    if (!gate.allowed) return err(403, 'suspended', { suspended_until: gate.suspended_until });
    const rate = await evaluateBindingRate(env, id, 'vote');
    if (!rate.ok) return err(429, rate.rule);

    let body: {
        work_id?: string; provider?: string; provider_ref?: string; removed?: boolean;
    };
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
    if (typeof body.removed !== 'boolean') return err(400, 'bad_request');
    if (body.provider_ref === '__none__') return err(404, 'not_found');

    const workId = await liveWorkId(env, body.work_id);
    const target = await env.COMMENTS_DB.prepare(
        `SELECT status FROM external_ids
         WHERE provider = ? AND external_id = ? AND miko_work_id = ?`,
    ).bind(body.provider, body.provider_ref, workId).first<{ status: string }>();
    if (!target) return err(404, 'not_found');

    await recordBindingRate(env, id.device_hash, 'vote');

    if (body.removed) {
        // The kind-scoped PK makes re-remove idempotent — the second
        // tap can never double-weight.
        await env.COMMENTS_DB.prepare(
            `INSERT OR IGNORE INTO binding_reports
             (provider, external_id, miko_work_id, device_hash, weight, kind, reason, created_at)
             VALUES (?,?,?,?,?,'remove',NULL,?)`,
        ).bind(
            body.provider, body.provider_ref, workId, id.device_hash,
            RULESET.bindings.remove_weight ?? 0.25, now(),
        ).run();
    } else {
        await env.COMMENTS_DB.prepare(
            `DELETE FROM binding_reports
             WHERE provider = ? AND external_id = ? AND device_hash = ? AND kind = 'remove'`,
        ).bind(body.provider, body.provider_ref, id.device_hash).run();
    }

    await recomputePoints(env, body.provider, body.provider_ref);
    await applyProviderDerank(env, body.provider);
    await recomputeProviderStats(env, body.provider);
    return ok({ removed: body.removed });
}
