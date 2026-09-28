import { requireIdentity } from './auth';
import type { Env } from './env';
import type { AppealState } from './jev';
import { judgeAppeal } from './jev';
import { evaluateComment } from './moderation';
import { RULESET } from './ruleset';
import { logModeration } from './strikes';
import { err, now, ok } from './util';

const MAX_REASON = 500;

/**
 * POST /v1/comments/:id/appeal {reason?}
 * Author-only appeal of a hidden comment. Jev judges immediately when
 * JEV_MODERATION_KEY is set; otherwise (or on Jev failure) the appeal
 * stays pending and the client can poll /v1/appeals/mine.
 */
export async function handleAppeal(
    req: Request,
    params: Record<string, string>,
    _u: URL,
    env: Env,
): Promise<Response> {
    const id = await requireIdentity(req, env);
    if (id instanceof Response) return id;
    const commentId = Number(params.id);
    if (!Number.isInteger(commentId)) return err(400, 'bad_id');

    let body: { reason?: string } = {};
    try {
        body = await req.json<{ reason?: string }>();
    } catch {
        // no body is fine — reason is optional
    }
    const reason = body.reason?.trim() || null;
    if (reason != null) {
        if (reason.length > MAX_REASON) return err(422, 'rule', { rule: 'max_len' });
        const verdict = evaluateComment(reason);
        if (!verdict.ok) return err(422, 'rule', { rule: verdict.rule });
    }

    const comment = await env.COMMENTS_DB.prepare(
        `SELECT c.device_hash, c.status, c.body,
                (SELECT COALESCE(SUM(weight),0) FROM reports r WHERE r.comment_id = c.id) AS report_weight
         FROM comments c WHERE c.id = ?`,
    ).bind(commentId)
        .first<{ device_hash: string; status: string; body: string; report_weight: number }>();
    if (!comment) return err(404, 'not_found');
    if (comment.device_hash !== id.device_hash) return err(403, 'not_author');
    if (comment.status !== 'hidden') return err(422, 'not_hidden');

    const pending = await env.COMMENTS_DB.prepare(
        "SELECT id FROM appeals WHERE comment_id = ? AND status = 'pending'",
    ).bind(commentId).first();
    if (pending) return err(409, 'appeal_pending');

    const ts = now();
    const inserted = await env.COMMENTS_DB.prepare(
        `INSERT INTO appeals (comment_id, device_hash, reason, status, created_at)
         VALUES (?,?,?,'pending',?)`,
    ).bind(commentId, id.device_hash, reason, ts).run();
    const appealId = inserted.meta.last_row_id;

    // Which rule hid it? Latest moderation_log row for this comment.
    const lastAction = await env.COMMENTS_DB.prepare(
        `SELECT rule_id FROM moderation_log
         WHERE comment_id = ? ORDER BY id DESC LIMIT 1`,
    ).bind(commentId).first<{ rule_id: string }>();

    const state: AppealState = {
        comment_body: comment.body,
        triggered_rule: lastAction?.rule_id ?? 'report_threshold',
        ruleset_version: RULESET.version,
        report_weight: comment.report_weight,
        appeal_reason: reason,
    };
    const verdict = await judgeAppeal(env, state);

    if (!verdict) {
        await logModeration(env, {
            comment_id: commentId, device_hash: id.device_hash,
            layer: 'jev_appeal', rule_id: 'appeal', action: 'pending',
        }, RULESET.version);
        return ok({ appeal_id: appealId, status: 'pending' }, 202);
    }

    const status = verdict.choice === 'restore' ? 'restored' : 'upheld';
    if (verdict.choice === 'restore') {
        await env.COMMENTS_DB.prepare(
            "UPDATE comments SET status = 'visible' WHERE id = ?",
        ).bind(commentId).run();
    }
    await env.COMMENTS_DB.prepare(
        `UPDATE appeals SET status = ?, jev_answer = ?, jev_confidence = ?, resolved_at = ?
         WHERE id = ?`,
    ).bind(status, verdict.choice, verdict.confidence, now(), appealId).run();
    await logModeration(env, {
        comment_id: commentId, device_hash: id.device_hash,
        layer: 'jev_appeal', rule_id: 'appeal',
        action: verdict.choice === 'restore' ? 'restore' : 'uphold',
    }, RULESET.version);

    return ok({ appeal_id: appealId, status });
}

/** GET /v1/appeals/mine — the caller's appeals, newest first. */
export async function handleMyAppeals(req: Request, env: Env): Promise<Response> {
    const id = await requireIdentity(req, env);
    if (id instanceof Response) return id;
    const { results } = await env.COMMENTS_DB.prepare(
        `SELECT id, comment_id, status, jev_answer, created_at, resolved_at
         FROM appeals WHERE device_hash = ? ORDER BY id DESC LIMIT 50`,
    ).bind(id.device_hash).all();
    return ok({ appeals: results });
}
