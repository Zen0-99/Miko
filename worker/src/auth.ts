import type { Env } from './env';
import { err, isHex64, now, ok, sha256Hex } from './util';

export interface Identity {
    device_hash: string;
    token_hash: string;
    display_name: string | null;
    name_tag: string | null;
    created_at: number;
    name_set_at: number | null;
    last_seen_at: number;
}

/**
 * POST /v1/device/register {device_hash}
 * Hashed ANDROID_ID → fresh bearer token. Re-registering an existing
 * device re-issues the token (reinstall path — the device hash itself
 * survives reinstall). Raw ANDROID_ID and raw token are never stored.
 */
export async function handleRegister(req: Request, env: Env): Promise<Response> {
    let body: { device_hash?: string };
    try {
        body = await req.json();
    } catch {
        return err(400, 'bad_json');
    }
    if (!isHex64(body.device_hash)) return err(400, 'bad_device_hash');

    const device = body.device_hash;
    const ts = now();
    const existing = await env.COMMENTS_DB.prepare(
        'SELECT created_at FROM identities WHERE device_hash = ?',
    ).bind(device).first<{ created_at: number }>();
    // Re-registering within a minute is a retry/abuse — throttle.
    if (existing && ts - existing.created_at < 60_000) {
        return err(429, 'register_rate_limit');
    }

    const token = crypto.randomUUID();
    const tokenHash = await sha256Hex(token);
    await env.COMMENTS_DB.prepare(
        `INSERT INTO identities (device_hash, token_hash, created_at, last_seen_at)
         VALUES (?,?,?,?)
         ON CONFLICT(device_hash) DO UPDATE SET
           token_hash = excluded.token_hash,
           last_seen_at = excluded.last_seen_at`,
    ).bind(device, tokenHash, existing?.created_at ?? ts, ts).run();

    return ok({ token, registered_at: existing?.created_at ?? ts });
}

/**
 * Bearer-token → identity row, or null. Cheap: one indexed read —
 * allowed on every write request per the suspension design.
 */
export async function identityFrom(req: Request, env: Env): Promise<Identity | null> {
    const header = req.headers.get('Authorization');
    if (!header?.startsWith('Bearer ')) return null;
    const tokenHash = await sha256Hex(header.slice(7).trim());
    const row = await env.COMMENTS_DB.prepare(
        'SELECT * FROM identities WHERE token_hash = ?',
    ).bind(tokenHash).first<Identity>();
    if (!row) return null;
    await env.COMMENTS_DB.prepare(
        'UPDATE identities SET last_seen_at = ? WHERE device_hash = ?',
    ).bind(now(), row.device_hash).run();
    return row;
}

/** Auth gate for write routes — returns the identity or a 401 Response. */
export async function requireIdentity(
    req: Request,
    env: Env,
): Promise<Identity | Response> {
    const id = await identityFrom(req, env);
    return id ?? err(401, 'unauthorized');
}
