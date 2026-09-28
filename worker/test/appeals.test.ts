import { env, fetchMock, SELF } from 'cloudflare:test';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';

const THREAD = 'manga:mk:mb:appeals';
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

async function hide(id: number) {
    await env.COMMENTS_DB.prepare(
        "UPDATE comments SET status = 'hidden' WHERE id = ?",
    ).bind(id).run();
    await env.COMMENTS_DB.prepare(
        `INSERT INTO moderation_log
         (comment_id, layer, rule_id, ruleset_version, action, created_at)
         VALUES (?,?,?,?,?,?)`,
    ).bind(id, 'report_threshold', 'auto_hide', 1, 'hide', Date.now()).run();
}

function jevMock(choice: 'restore' | 'uphold', confidence = 0.9) {
    fetchMock
        .get('https://api.typesafe.ai')
        .intercept({ path: '/v1/systemone', method: 'POST' })
        .reply(200, {
            model: 'jev-1.13.0',
            answers: {
                verdict: {
                    type: 'choice', choice,
                    probabilities: { [choice]: confidence }, confidence,
                },
            },
            usage: { input_tokens: 1, output_tokens: 1 },
        });
}

beforeAll(() => {
    fetchMock.activate();
    fetchMock.disableNetConnect();
});

afterAll(() => {
    fetchMock.deactivate();
});

describe('appeals', () => {
    it('restores the comment when Jev says restore', async () => {
        jevMock('restore');
        const t = await user(dev(10), 'V1');
        const id = await commentId(t);
        await hide(id);

        const res = await post(`/v1/comments/${id}/appeal`, { reason: 'false positive' }, t);
        expect(res.status).toBe(200);
        expect(await res.json()).toMatchObject({ status: 'restored' });

        const row = await env.COMMENTS_DB.prepare(
            'SELECT status FROM comments WHERE id = ?',
        ).bind(id).first<{ status: string }>();
        expect(row?.status).toBe('visible');
    });

    it('upholds the hide when Jev says uphold', async () => {
        jevMock('uphold');
        const t = await user(dev(11), 'V2');
        const id = await commentId(t);
        await hide(id);

        const res = await post(`/v1/comments/${id}/appeal`, {}, t);
        expect(await res.json()).toMatchObject({ status: 'upheld' });
        const row = await env.COMMENTS_DB.prepare(
            'SELECT status FROM comments WHERE id = ?',
        ).bind(id).first<{ status: string }>();
        expect(row?.status).toBe('hidden');
    });

    it('rejects appeals from non-authors', async () => {
        const author = await user(dev(12), 'V3');
        const other = await user(dev(13), 'V4');
        const id = await commentId(author);
        await hide(id);
        expect((await post(`/v1/comments/${id}/appeal`, {}, other)).status).toBe(403);
    });

    it('rejects appeals on visible comments and double pending appeals', async () => {
        const t = await user(dev(14), 'V5');
        const id = await commentId(t);
        expect((await post(`/v1/comments/${id}/appeal`, {}, t)).status).toBe(422);

        await hide(id);
        // Jev 500 → appeal stays pending (never auto-restores).
        fetchMock
            .get('https://api.typesafe.ai')
            .intercept({ path: '/v1/systemone', method: 'POST' })
            .reply(500, 'upstream');
        const res = await post(`/v1/comments/${id}/appeal`, {}, t);
        expect(res.status).toBe(202);
        expect(await res.json()).toMatchObject({ status: 'pending' });
        expect((await post(`/v1/comments/${id}/appeal`, {}, t)).status).toBe(409);
    });

    it('logs jev_appeal actions to moderation_log', async () => {
        jevMock('uphold');
        const t = await user(dev(15), 'V6');
        const id = await commentId(t);
        await hide(id);
        await post(`/v1/comments/${id}/appeal`, { reason: 'please' }, t);

        const row = await env.COMMENTS_DB.prepare(
            `SELECT layer, rule_id, action FROM moderation_log
             WHERE comment_id = ? AND layer = 'jev_appeal'`,
        ).bind(id).first<{ layer: string; rule_id: string; action: string }>();
        expect(row).toMatchObject({ layer: 'jev_appeal', rule_id: 'appeal', action: 'uphold' });
    });

    it('lists my appeals', async () => {
        jevMock('uphold');
        const t = await user(dev(16), 'V7');
        const id = await commentId(t);
        await hide(id);
        await post(`/v1/comments/${id}/appeal`, {}, t);

        const res = await SELF.fetch('http://test/v1/appeals/mine', {
            headers: { Authorization: `Bearer ${t}` },
        });
        const body = await res.json<{ appeals: Array<{ status: string }> }>();
        expect(body.appeals.length).toBeGreaterThan(0);
        expect(body.appeals[0].status).toBe('upheld');
    });
});
