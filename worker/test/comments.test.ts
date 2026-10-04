import { SELF, env } from 'cloudflare:test';
import { describe, expect, it } from 'vitest';

const DEVICE = 'a'.repeat(64);
const DEVICE2 = 'b'.repeat(64);
const THREAD = 'manga:mk:mb:531343';

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

async function register(device = DEVICE): Promise<string> {
    const res = await post('/v1/device/register', { device_hash: device });
    const { token } = await res.json<{ token: string }>();
    return token;
}

async function namedUser(device = DEVICE, name = 'Reader A'): Promise<string> {
    const token = await register(device);
    await post('/v1/names', { name }, token);
    return token;
}

describe('device register', () => {
    it('issues a token for a valid device hash', async () => {
        const res = await post('/v1/device/register', { device_hash: DEVICE });
        expect(res.status).toBe(200);
        const { token } = await res.json<{ token: string }>();
        expect(token).toMatch(/^[0-9a-f-]{36}$/);
    });

    it('rejects a malformed device hash', async () => {
        const res = await post('/v1/device/register', { device_hash: 'nope' });
        expect(res.status).toBe(400);
    });

    it('throttles a re-register within a minute', async () => {
        await register();
        const res = await post('/v1/device/register', { device_hash: DEVICE });
        expect(res.status).toBe(429);
    });
});

describe('names', () => {
    it('requires auth', async () => {
        const res = await post('/v1/names', { name: 'x' });
        expect(res.status).toBe(401);
    });

    it('accepts and stores a valid name', async () => {
        const token = await register();
        const res = await post('/v1/names', { name: 'Reader A' }, token);
        expect(res.status).toBe(200);
    });

    it('rejects reserved names and allows duplicate names with distinct tags', async () => {
        const t1 = await register();
        const t2 = await register(DEVICE2);
        expect((await post('/v1/names', { name: 'Admin' }, t1)).status).toBe(422);
        const r1 = await post('/v1/names', { name: 'Reader A' }, t1);
        expect(r1.status).toBe(200);
        const j1 = await r1.json() as { display_name: string; name_tag: string };
        expect(j1.name_tag).toMatch(/^\d{4}$/);
        // Same name on another device succeeds — discriminated by tag.
        const r2 = await post('/v1/names', { name: 'reader a' }, t2);
        expect(r2.status).toBe(200);
        const j2 = await r2.json() as { display_name: string; name_tag: string };
        expect(j2.name_tag).toMatch(/^\d{4}$/);
        expect(j2.name_tag).not.toBe(j1.name_tag);
    });
});

describe('comments', () => {
    it('posts and lists anonymously', async () => {
        const token = await namedUser();
        const post_res = await post(
            '/v1/comments',
            { thread_id: THREAD, body: 'great chapter' },
            token,
        );
        expect(post_res.status).toBe(201);
        const { comment } = await post_res.json<{ comment: { author: { name: string } } }>();
        expect(comment.author.name).toBe('Reader A');

        const list = await SELF.fetch(`http://test/v1/comments?thread_id=${THREAD}`);
        const body = await list.json<{ comments: Array<{ body: string }> }>();
        expect(body.comments).toHaveLength(1);
        expect(body.comments[0].body).toBe('great chapter');
    });

    it('returns per-thread counts and skips malformed ids', async () => {
        const token = await namedUser('c'.repeat(64));
        await post('/v1/comments', { thread_id: THREAD, body: 'one' }, token);
        await post('/v1/comments', { thread_id: THREAD, body: 'two' }, token);
        await post('/v1/comments', { thread_id: 'shows:mk:imdb:tt1', body: 'hi' }, token);

        const res = await SELF.fetch(
            `http://test/v1/threads/counts?ids=${THREAD},shows:mk:imdb:tt1,bad%20id,`,
        );
        expect(res.status).toBe(200);
        const { counts } = await res.json<{ counts: Record<string, number> }>();
        expect(counts[THREAD]).toBe(2);
        expect(counts['shows:mk:imdb:tt1']).toBe(1);
        expect(Object.keys(counts)).toHaveLength(2);

        const empty = await SELF.fetch('http://test/v1/threads/counts?ids=');
        expect((await empty.json<{ counts: Record<string, number> }>()).counts)
            .toEqual({});
    });

    it('rejects posting without a display name', async () => {
        const token = await register();
        const res = await post('/v1/comments', { thread_id: THREAD, body: 'hi' }, token);
        expect(res.status).toBe(428);
    });

    it('rejects rule violations', async () => {
        const token = await namedUser();
        const res = await post(
            '/v1/comments',
            { thread_id: THREAD, body: 'win crypto!!!' },
            token,
        );
        expect(res.status).toBe(422);
    });

    it('accepts the carrier thread vocabulary only', async () => {
        const token = await namedUser();
        const ok1 = await post(
            '/v1/comments',
            { thread_id: 'shows:mk:imdb:tt0903747', body: 'great episode' },
            token,
        );
        expect(ok1.status).toBe(201);
        const ok2 = await post(
            '/v1/comments',
            { thread_id: 'books:mk:ol:OL1W', body: 'great book' },
            token,
        );
        expect(ok2.status).toBe(201);

        // Legacy prefixes die at the 0006 boundary — no alias window for
        // thread ids (locked ruling).
        for (const threadId of ['anime:mk:mb:1', 'novel:mk:mb:1']) {
            const res = await post(
                '/v1/comments', { thread_id: threadId, body: 'hi there' }, token,
            );
            expect(res.status).toBe(400);
            expect(await res.json()).toMatchObject({ error: 'bad_thread_id' });
        }
        const list = await SELF.fetch('http://test/v1/comments?thread_id=anime:mk:mb:1');
        expect(list.status).toBe(400);
    });

    it('threads replies under the same parent only', async () => {
        const token = await namedUser();
        const parent = await (await post(
            '/v1/comments', { thread_id: THREAD, body: 'parent' }, token,
        )).json<{ comment: { id: number } }>();
        const okRes = await post(
            '/v1/comments',
            { thread_id: THREAD, body: 'reply', parent_id: parent.comment.id },
            token,
        );
        expect(okRes.status).toBe(201);
        const cross = await post(
            '/v1/comments',
            { thread_id: 'manga:mk:mb:1', body: 'x', parent_id: parent.comment.id },
            token,
        );
        expect(cross.status).toBe(422);
    });
});

describe('anti-spam rate limits', () => {
    async function makeEstablished(device: string) {
        await env.COMMENTS_DB.prepare(
            'UPDATE identities SET created_at = ? WHERE device_hash = ?',
        ).bind(Date.now() - 2 * 86_400_000, device).run();
    }

    async function seedComments(
        rows: Array<{ thread: string; device: string; body: string; at: number }>,
    ) {
        await env.COMMENTS_DB.batch(rows.map((r) =>
            env.COMMENTS_DB.prepare(
                'INSERT INTO comments (thread_id, device_hash, body, created_at) VALUES (?,?,?,?)',
            ).bind(r.thread, r.device, r.body, r.at),
        ));
    }

    it('caps any device at 3 comments per minute', async () => {
        const token = await namedUser();
        await makeEstablished(DEVICE);
        for (let i = 0; i < 3; i++) {
            expect((await post(
                '/v1/comments', { thread_id: THREAD, body: `thought ${i}` }, token,
            )).status).toBe(201);
        }
        const res = await post(
            '/v1/comments', { thread_id: 'manga:mk:mb:2', body: 'fourth' }, token,
        );
        expect(res.status).toBe(422);
        expect(await res.json()).toMatchObject({ error: 'rule', rule: 'rate_min' });
    });

    it('first-day devices get the tighter hourly cap', async () => {
        const token = await namedUser(); // registered now → new-device tier
        // 8 posts in the last hour — at the new-device cap (8), under
        // the established cap (20), and all outside the per-minute window.
        const at = Date.now() - 30 * 60_000;
        await seedComments(
            Array.from({ length: 8 }, (_, i) => ({
                thread: THREAD, device: DEVICE, body: `seed ${i}`, at: at + i,
            })),
        );
        const res = await post('/v1/comments', { thread_id: THREAD, body: 'over quota' }, token);
        expect(res.status).toBe(422);
        expect(await res.json()).toMatchObject({ error: 'rule', rule: 'rate_hour' });
    });

    it('caps any device at 6 comments per thread per 10 min', async () => {
        const token = await namedUser();
        await makeEstablished(DEVICE); // keep the per-min cap out of the way
        const at = Date.now() - 120_000; // 2 min ago — inside the 10-min window
        await seedComments(
            Array.from({ length: 6 }, (_, i) => ({
                thread: THREAD, device: DEVICE, body: `take ${i}`, at: at + i,
            })),
        );
        // A different thread in the same window is still fine — the
        // cap is thread-scoped. (Checked first: the violation below
        // strikes the device, which suspends it for an hour.)
        expect((await post(
            '/v1/comments', { thread_id: 'manga:mk:mb:2', body: 'elsewhere' }, token,
        )).status).toBe(201);
        const res = await post('/v1/comments', { thread_id: THREAD, body: 'flood' }, token);
        expect(res.status).toBe(422);
        expect(await res.json()).toMatchObject({ error: 'rule', rule: 'rate_thread' });
    });

    it('rejects reposting an identical body', async () => {
        const token = await namedUser();
        await makeEstablished(DEVICE);
        await seedComments([
            { thread: 'manga:mk:mb:old', device: DEVICE, body: 'same words', at: Date.now() - 1_800_000 },
        ]);
        const res = await post(
            '/v1/comments', { thread_id: THREAD, body: 'same words' }, token,
        );
        expect(res.status).toBe(422);
        expect(await res.json()).toMatchObject({ error: 'rule', rule: 'duplicate' });
    });
});

describe('votes + reports + delete', () => {
    it('votes up/down/off and sums score', async () => {
        const t1 = await namedUser(DEVICE, 'One');
        const t2 = await namedUser(DEVICE2, 'Two');
        const { comment } = await (await post(
            '/v1/comments', { thread_id: THREAD, body: 'x' }, t1,
        )).json<{ comment: { id: number } }>();
        const cid = comment.id;

        expect((await (await post(`/v1/comments/${cid}/vote`, { value: 1 }, t1)).json<{ score: number }>()).score).toBe(1);
        expect((await (await post(`/v1/comments/${cid}/vote`, { value: -1 }, t2)).json<{ score: number }>()).score).toBe(0);
        expect((await (await post(`/v1/comments/${cid}/vote`, { value: 0 }, t2)).json<{ score: number }>()).score).toBe(1);
    });

    it('report is idempotent per device', async () => {
        const t1 = await namedUser(DEVICE, 'One');
        const t2 = await namedUser(DEVICE2, 'Two');
        const { comment } = await (await post(
            '/v1/comments', { thread_id: THREAD, body: 'x' }, t1,
        )).json<{ comment: { id: number } }>();
        await post(`/v1/comments/${comment.id}/report`, {}, t2);
        await post(`/v1/comments/${comment.id}/report`, {}, t2);
        const { count } = await env.COMMENTS_DB.prepare(
            'SELECT COUNT(*) AS count FROM reports WHERE comment_id = ?',
        ).bind(comment.id).first<{ count: number }>() as { count: number };
        expect(count).toBe(1);
    });

    it('delete tombstones own comment only', async () => {
        const t1 = await namedUser(DEVICE, 'One');
        const t2 = await namedUser(DEVICE2, 'Two');
        const { comment } = await (await post(
            '/v1/comments', { thread_id: THREAD, body: 'x' }, t1,
        )).json<{ comment: { id: number } }>();

        expect((await SELF.fetch(`http://test/v1/comments/${comment.id}`, {
            method: 'DELETE', headers: { Authorization: `Bearer ${t2}` },
        })).status).toBe(404);
        expect((await SELF.fetch(`http://test/v1/comments/${comment.id}`, {
            method: 'DELETE', headers: { Authorization: `Bearer ${t1}` },
        })).status).toBe(200);

        const list = await SELF.fetch(`http://test/v1/comments?thread_id=${THREAD}`);
        const body = await list.json<{ comments: Array<{ status: string; body: unknown }> }>();
        expect(body.comments[0].status).toBe('deleted');
        expect(body.comments[0].body).toBeNull();
    });
});
