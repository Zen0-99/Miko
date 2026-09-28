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
