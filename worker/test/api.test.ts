import { SELF, env } from 'cloudflare:test';
import { describe, expect, it } from 'vitest';

async function post(path: string, body: unknown): Promise<Response> {
    return SELF.fetch(`http://test${path}`, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify(body),
    });
}

describe('scaffold', () => {
    it('health returns ok', async () => {
        const res = await SELF.fetch('http://test/v1/health');
        expect(res.status).toBe(200);
        expect(await res.json()).toEqual({ ok: true });
    });

    it('ruleset serves version 1', async () => {
        const res = await SELF.fetch('http://test/v1/ruleset');
        const body = await res.json<{
            ruleset_version: number;
            ruleset: { version: number; comment: { max_len: number } };
        }>();
        expect(body.ruleset_version).toBe(1);
        expect(body.ruleset.comment.max_len).toBe(2000);
    });

    it('unknown route 404s', async () => {
        const res = await SELF.fetch('http://test/v1/nope');
        expect(res.status).toBe(404);
    });
});

describe('resolve', () => {
    // Storage is isolated per test — seed inside each one.
    async function seedShadowSlaveNovel() {
        await env.COMMENTS_DB.prepare(
            `INSERT INTO works_cache
             (miko_work_id, type, title, normalized_title, provider_links, fetched_at)
             VALUES ('mk:mb:531343', 'novel', 'Shadow Slave', 'shadow slave', '[]', 0)`,
        ).run();
        await env.COMMENTS_DB.prepare(
            `INSERT INTO works_titles (miko_work_id, title, normalized)
             VALUES ('mk:mb:531343', 'Shadow Slave', 'shadow slave')`,
        ).run();
        await env.COMMENTS_DB.prepare(
            `INSERT INTO external_ids (provider, external_id, miko_work_id)
             VALUES ('royalroad', '21220', 'mk:mb:531343')`,
        ).run();
    }

    it('hits works_cache by normalized title', async () => {
        await seedShadowSlaveNovel();
        const res = await post('/v1/works/resolve', { title: 'shadow slave!', medium: 'books' });
        expect(res.status).toBe(200);
        const body = await res.json<{ work: { miko_work_id: string; external_ids: Record<string, string> } }>();
        expect(body.work.miko_work_id).toBe('mk:mb:531343');
        expect(body.work.external_ids.royalroad).toBe('21220');
    });

    it('hits by external id', async () => {
        await seedShadowSlaveNovel();
        const res = await post('/v1/works/resolve', {
            provider: 'royalroad', external_id: '21220',
            title: 'anything', medium: 'books',
        });
        const body = await res.json<{ work: { miko_work_id: string } }>();
        expect(body.work.miko_work_id).toBe('mk:mb:531343');
    });

    it('never mixes mediums', async () => {
        await seedShadowSlaveNovel();
        // Cached row is type=novel — outside manga's allowed set; remote is
        // unreachable in tests → work null rather than a cross-medium match.
        const res = await post('/v1/works/resolve', { title: 'shadow slave', medium: 'manga' });
        const body = await res.json<{ work: unknown }>();
        expect(body.work).toBeNull();
    });

    it('rejects missing title', async () => {
        const res = await post('/v1/works/resolve', { title: '   ', medium: 'manga' });
        expect(res.status).toBe(400);
    });
});
