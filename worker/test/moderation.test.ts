import { SELF, env } from 'cloudflare:test';
import { describe, expect, it } from 'vitest';

const THREAD = 'manga:mk:mb:1';
const DAY_MS = 86_400_000;

const dev = (n: number) => n.toString(16).padStart(64, '0');

async function post(path: string, body: unknown, token?: string): Promise<Response> {
    return SELF.fetch(`http://test${path}`, {
        method: 'POST',
        headers: {
            'content-type': 'application/json',
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify(body),
    });
}

async function user(device: string, name: string): Promise<string> {
    const { token } = await (await post('/v1/device/register', { device_hash: device }))
        .json<{ token: string }>();
    await post('/v1/names', { name }, token);
    return token;
}

async function commentId(token: string, body = 'hi'): Promise<number> {
    const { comment } = await (await post('/v1/comments', { thread_id: THREAD, body }, token))
        .json<{ comment: { id: number } }>();
    return comment.id;
}

describe('rules', () => {
    it('rejects over-length bodies', async () => {
        const t = await user(dev(1), 'A1');
        const res = await post('/v1/comments', { thread_id: THREAD, body: 'x'.repeat(2001) }, t);
        expect((await res.json<{ rule: string }>()).rule).toBe('max_len');
    });

    it('rejects too many links', async () => {
        const t = await user(dev(1), 'A2');
        const body = 'a http://x.co/1 b http://x.co/2 c http://x.co/3 d http://x.co/4';
        const res = await post('/v1/comments', { thread_id: THREAD, body }, t);
        expect((await res.json<{ rule: string }>()).rule).toBe('max_links');
    });

    it('rejects duplicates within the window', async () => {
        const t = await user(dev(1), 'A3');
        await post('/v1/comments', { thread_id: THREAD, body: 'same text' }, t);
        const res = await post('/v1/comments', { thread_id: THREAD, body: 'same text' }, t);
        expect((await res.json<{ rule: string }>()).rule).toBe('duplicate');
    });

    it('rate-limits after posts_per_min', async () => {
        const t = await user(dev(1), 'A4');
        for (let i = 0; i < 3; i++) {
            await post('/v1/comments', { thread_id: THREAD, body: `msg ${i}` }, t);
        }
        const res = await post('/v1/comments', { thread_id: THREAD, body: 'msg 3' }, t);
        expect((await res.json<{ rule: string }>()).rule).toBe('rate_min');
    });
});

describe('strikes + suspension', () => {
    it('first violation → level 1 (1h) and writes get 403', async () => {
        const device = dev(2);
        const t = await user(device, 'B1');
        const bad = await post('/v1/comments', { thread_id: THREAD, body: 'free crypto' }, t);
        const { suspended_until } = await bad.json<{ suspended_until: number }>();
        expect(suspended_until).toBeGreaterThan(Date.now() + 55 * 60 * 1000);
        expect(suspended_until).toBeLessThan(Date.now() + 65 * 60 * 1000);

        const blocked = await post('/v1/comments', { thread_id: THREAD, body: 'ok' }, t);
        expect(blocked.status).toBe(403);
        const vote = await post(`/v1/comments/1/vote`, { value: 1 }, t);
        expect(vote.status).toBe(403);

        // Reads still work while suspended.
        const list = await SELF.fetch(`http://test/v1/comments?thread_id=${THREAD}`);
        expect(list.status).toBe(200);
    });

    it('escalates the ladder and decays after 30 clean days', async () => {
        const device = dev(3);
        const t = await user(device, 'C1');
        await post('/v1/comments', { thread_id: THREAD, body: 'free crypto' }, t);
        // Second violation while still hot → level 2 (5h).
        await env.COMMENTS_DB.prepare(
            'UPDATE strikes SET suspended_until = 0 WHERE device_hash = ?',
        ).bind(device).run();
        const res = await post('/v1/comments', { thread_id: THREAD, body: 'free crypto' }, t);
        const { suspended_until } = await res.json<{ suspended_until: number }>();
        expect(suspended_until).toBeGreaterThan(Date.now() + 4.9 * 3600e3);

        // 31 clean days → level resets, next violation is level 1 again.
        const old = Date.now() - 31 * DAY_MS;
        await env.COMMENTS_DB.prepare(
            'UPDATE strikes SET suspended_until = 0, last_strike_at = ? WHERE device_hash = ?',
        ).bind(old, device).run();
        const decayed = await post('/v1/comments', { thread_id: THREAD, body: 'free crypto' }, t);
        const row = await decayed.json<{ suspended_until: number }>();
        expect(row.suspended_until).toBeLessThan(Date.now() + 65 * 60 * 1000);
    });
});

describe('reports + auto-hide', () => {
    it('hides at cumulative weight ≥3 and logs it', async () => {
        const author = await user(dev(4), 'D1');
        const cid = await commentId(author);

        // Four aged reporters × weight 1.0 = 4 ≥ 3 → hides.
        const reporters = [14, 15, 16, 17];
        for (const r of reporters) {
            const t = await user(dev(r), `R-${String(r)}`);
            // Age the reporter past 30d so weight = 1.0.
            await env.COMMENTS_DB.prepare(
                'UPDATE identities SET created_at = ? WHERE device_hash = ?',
            ).bind(Date.now() - 31 * DAY_MS, dev(r)).run();
            await post(`/v1/comments/${cid}/report`, {}, t);
        }
        const list = await SELF.fetch(`http://test/v1/comments?thread_id=${THREAD}`);
        const body = await list.json<{ comments: unknown[] }>();
        expect(body.comments).toHaveLength(0); // hidden, not listed

        const log = await env.COMMENTS_DB.prepare(
            "SELECT layer, rule_id, action FROM moderation_log WHERE comment_id = ?",
        ).bind(cid).first<{ layer: string; rule_id: string; action: string }>();
        expect(log).toEqual({ layer: 'report_threshold', rule_id: 'auto_hide', action: 'hide' });
    });

    it('new-device reports alone do not hide', async () => {
        const author = await user(dev(9), 'I1');
        const cid = await commentId(author);
        for (const r of [20, 21, 22, 23, 24]) {
            const t = await user(dev(r), `R-${String(r)}`);
            await post(`/v1/comments/${cid}/report`, {}, t);
        }
        // 5 fresh reporters × 0.25 = 1.25 < 3 → still visible.
        const list = await SELF.fetch(`http://test/v1/comments?thread_id=${THREAD}`);
        const body = await list.json<{ comments: unknown[] }>();
        expect(body.comments).toHaveLength(1);
    });
});
