import type { Identity } from './auth';
import type { Env } from './env';
import { evaluateComment, type RuleResult } from './moderation';
import { RULESET } from './ruleset';
import { now } from './util';

/**
 * Full comment gate: pure rules (moderation.ts) + stateful checks
 * (rate limits, duplicates) that need D1. Evaluated server-side on
 * every post — modified clients can't bypass (NCM-005).
 *
 * Rate caps come in two device-age tiers — the anonymous-device analog
 * of Discourse's trust-level limits: identities registered in the last
 * `new_device_ms` get the `*_new` numbers, everyone else gets the base
 * `posts_per_*`. A missing `*_new`/`thread_*` key falls back to the
 * base behaviour so older rulesets still work.
 */
export async function evaluateCommentFull(
    env: Env,
    id: Identity,
    threadId: string,
    body: string,
): Promise<RuleResult> {
    const pure = evaluateComment(body);
    if (!pure.ok) return pure;

    const ts = now();
    const r = RULESET.comment;
    // New-device tier — the anonymous-device analog of Discourse's
    // first-day volume caps. The per-minute cap stays flat: tightening
    // it would trip normal back-and-forth replying.
    const isNew = ts - id.created_at < (r.rate.new_device_ms ?? 0);
    const perHourCap = isNew ? (r.rate.new_device_posts_per_hour ?? r.rate.posts_per_hour)
        : r.rate.posts_per_hour;

    const [perMin, perHour] = await Promise.all([
        env.COMMENTS_DB.prepare(
            'SELECT COUNT(*) AS n FROM comments WHERE device_hash = ? AND created_at > ?',
        ).bind(id.device_hash, ts - 60_000).first<{ n: number }>(),
        env.COMMENTS_DB.prepare(
            'SELECT COUNT(*) AS n FROM comments WHERE device_hash = ? AND created_at > ?',
        ).bind(id.device_hash, ts - 3_600_000).first<{ n: number }>(),
    ]);
    if ((perMin?.n ?? 0) >= r.rate.posts_per_min) return { ok: false, rule: 'rate_min' };
    if ((perHour?.n ?? 0) >= perHourCap) return { ok: false, rule: 'rate_hour' };

    // Per-thread flood cap (Lemmy-style action bucket, window-counted):
    // spam concentrates on a single thread, so the same-device burst
    // gets its own quota on top of the global ones.
    const threadBurst = r.rate.thread_burst_per_window;
    if (threadBurst != null) {
        const window = r.rate.thread_burst_window_ms ?? 600_000;
        const n = await env.COMMENTS_DB.prepare(
            'SELECT COUNT(*) AS n FROM comments WHERE device_hash = ? AND thread_id = ? AND created_at > ?',
        ).bind(id.device_hash, threadId, ts - window).first<{ n: number }>();
        if ((n?.n ?? 0) >= threadBurst) return { ok: false, rule: 'rate_thread' };
    }

    const dup = await env.COMMENTS_DB.prepare(
        'SELECT id FROM comments WHERE device_hash = ? AND body = ? AND created_at > ?',
    ).bind(id.device_hash, body, ts - r.duplicate_window_h * 3_600_000).first();
    if (dup) return { ok: false, rule: 'duplicate' };

    return { ok: true };
}
