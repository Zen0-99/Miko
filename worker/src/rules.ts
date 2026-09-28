import type { Env } from './env';
import { evaluateComment, type RuleResult } from './moderation';
import { RULESET } from './ruleset';
import { now } from './util';

/**
 * Full comment gate: pure rules (moderation.ts) + stateful checks
 * (rate limits, duplicates) that need D1. Evaluated server-side on
 * every post — modified clients can't bypass (NCM-005).
 */
export async function evaluateCommentFull(
    env: Env,
    deviceHash: string,
    body: string,
): Promise<RuleResult> {
    const pure = evaluateComment(body);
    if (!pure.ok) return pure;

    const ts = now();
    const r = RULESET.comment;
    const [perMin, perHour] = await Promise.all([
        env.COMMENTS_DB.prepare(
            'SELECT COUNT(*) AS n FROM comments WHERE device_hash = ? AND created_at > ?',
        ).bind(deviceHash, ts - 60_000).first<{ n: number }>(),
        env.COMMENTS_DB.prepare(
            'SELECT COUNT(*) AS n FROM comments WHERE device_hash = ? AND created_at > ?',
        ).bind(deviceHash, ts - 3_600_000).first<{ n: number }>(),
    ]);
    if ((perMin?.n ?? 0) >= r.rate.posts_per_min) return { ok: false, rule: 'rate_min' };
    if ((perHour?.n ?? 0) >= r.rate.posts_per_hour) return { ok: false, rule: 'rate_hour' };

    const dup = await env.COMMENTS_DB.prepare(
        'SELECT id FROM comments WHERE device_hash = ? AND body = ? AND created_at > ?',
    ).bind(deviceHash, body, ts - r.duplicate_window_h * 3_600_000).first();
    if (dup) return { ok: false, rule: 'duplicate' };

    return { ok: true };
}
