import type { Env } from './env';
import { now } from './util';

// Suspension ladder by strike_level — 0 means none, 6 is the cap.
// No permanent bans (NCM-004).
const LADDER_MS = [
    0,
    60 * 60 * 1000,            // 1: 1 hour
    5 * 60 * 60 * 1000,        // 2: 5 hours
    24 * 60 * 60 * 1000,       // 3: 1 day
    3 * 24 * 60 * 60 * 1000,   // 4: 3 days
    7 * 24 * 60 * 60 * 1000,   // 5: 1 week
    30 * 24 * 60 * 60 * 1000,  // 6: 1 month
];
const DECAY_MS = 30 * 24 * 60 * 60 * 1000; // 30 clean days → reset

export const MAX_STRIKE_LEVEL = LADDER_MS.length - 1;

interface StrikeRow {
    strike_level: number;
    suspended_until: number;
    last_strike_at: number;
}

/** Write-path check — reads are never gated. */
export async function checkWrite(
    env: Env,
    deviceHash: string,
): Promise<{ allowed: true } | { allowed: false; suspended_until: number }> {
    const row = await env.COMMENTS_DB.prepare(
        'SELECT suspended_until FROM strikes WHERE device_hash = ?',
    ).bind(deviceHash).first<{ suspended_until: number }>();
    if (row && row.suspended_until > now()) {
        return { allowed: false, suspended_until: row.suspended_until };
    }
    return { allowed: true };
}

/**
 * Records a violation: 30-day clean decay applies first, then
 * level+1 (capped at 6) and the matching suspension duration.
 * Returns the resulting suspension end for the response body.
 */
export async function addStrike(env: Env, deviceHash: string): Promise<number> {
    const ts = now();
    const row = await env.COMMENTS_DB.prepare(
        'SELECT strike_level, last_strike_at FROM strikes WHERE device_hash = ?',
    ).bind(deviceHash).first<StrikeRow>();

    let level = (row?.strike_level ?? 0) + 1;
    if (row && ts - row.last_strike_at >= DECAY_MS) level = 1;
    level = Math.min(level, MAX_STRIKE_LEVEL);
    const suspendedUntil = ts + LADDER_MS[level];

    await env.COMMENTS_DB.prepare(
        `INSERT INTO strikes (device_hash, strike_level, suspended_until, last_strike_at, updated_at)
         VALUES (?,?,?,?,?)
         ON CONFLICT(device_hash) DO UPDATE SET
           strike_level = excluded.strike_level,
           suspended_until = excluded.suspended_until,
           last_strike_at = excluded.last_strike_at,
           updated_at = excluded.updated_at`,
    ).bind(deviceHash, level, suspendedUntil, ts, ts).run();
    return suspendedUntil;
}

export async function logModeration(
    env: Env,
    entry: {
        comment_id?: number | null;
        device_hash?: string | null;
        layer: string;
        rule_id: string;
        action: string;
    },
    rulesetVersion: number,
): Promise<void> {
    await env.COMMENTS_DB.prepare(
        `INSERT INTO moderation_log
         (comment_id, device_hash, layer, rule_id, ruleset_version, action, created_at)
         VALUES (?,?,?,?,?,?,?)`,
    ).bind(
        entry.comment_id ?? null, entry.device_hash ?? null,
        entry.layer, entry.rule_id, rulesetVersion, entry.action, now(),
    ).run();
}
