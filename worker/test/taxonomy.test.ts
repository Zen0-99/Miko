import { env, fetchMock, SELF } from 'cloudflare:test';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';

// MANGABAKA_BASE is bound to this fake origin in vitest.config.ts —
// fetchMock intercepts it here so the MangaBaka persist path (nsfw stamping)
// can be exercised deterministically.
const MB = 'http://127.0.0.1:59999';

async function resolve(body: unknown): Promise<Response> {
    return SELF.fetch('http://test/v1/works/resolve', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify(body),
    });
}

// Intercepts are keyed on the ?q= title so each test's mock only answers
// its own request (undici intercepts persist across the file).
function mockMbSearch(title: string, series: unknown[]) {
    fetchMock
        .get(MB)
        .intercept({
            path: '/v1/series/search',
            method: 'GET',
            query: { q: title, limit: '10' },
        })
        .reply(200, { data: series });
}

function mbSeries(id: number, title: string, over: Record<string, unknown> = {}) {
    return {
        id, title, romanized_title: null, native_title: null,
        secondary_titles: null, titles: [], type: 'manga', year: null,
        total_chapters: null, authors: null, links_v2: null, source: null,
        ...over,
    };
}

async function cacheRow(workId: string) {
    return env.COMMENTS_DB.prepare(
        'SELECT type, medium, nsfw, content_rating FROM works_cache WHERE miko_work_id = ?',
    ).bind(workId).first<{
        type: string; medium: string | null; nsfw: number; content_rating: string | null;
    }>();
}

beforeAll(() => {
    fetchMock.activate();
    fetchMock.disableNetConnect();
});

afterAll(() => {
    fetchMock.deactivate();
});

interface WorkDto {
    miko_work_id: string;
    kind: string;
    medium: string | null;
    nsfw: boolean;
    content_rating: string | null;
}

describe('resolve — medium vocabulary', () => {
    it('rejects an unknown medium instead of collapsing to manga', async () => {
        const res = await resolve({ title: 'Berserk', medium: 'bogus' });
        expect(res.status).toBe(400);
        expect(await res.json()).toMatchObject({ error: 'bad_medium' });
    });

    it('rejects a missing medium', async () => {
        const res = await resolve({ title: 'Berserk' });
        expect(res.status).toBe(400);
        expect(await res.json()).toMatchObject({ error: 'bad_medium' });
    });

    it('legacy anime/novel mediums are rejected (alias window closed)', async () => {
        for (const medium of ['anime', 'novel']) {
            const res = await resolve({ title: 'Anything', medium });
            expect(res.status).toBe(400);
            expect(await res.json()).toMatchObject({ error: 'bad_medium' });
        }
    });

    it('a manga-medium request can no longer reach anime kinds', async () => {
        await env.COMMENTS_DB.prepare(
            `INSERT INTO works_cache
             (miko_work_id, type, medium, title, normalized_title, provider_links, fetched_at)
             VALUES ('mk:imdb:tt9999999', 'anime', 'shows', 'Some Show', 'some show', '[]', 0)`,
        ).run();
        await env.COMMENTS_DB.prepare(
            `INSERT INTO works_titles (miko_work_id, title, normalized)
             VALUES ('mk:imdb:tt9999999', 'Some Show', 'some show')`,
        ).run();
        // 'anime' kind is outside manga's allowed set — the old collapse
        // would have dropped 'manga' requests to manga vocab anyway, but
        // this pins the carrier boundary under the new vocab.
        const res = await resolve({ title: 'some show', medium: 'manga' });
        const body = await res.json<{ work: unknown }>();
        expect(body.work).toBeNull();
    });
});

describe('resolve — taxonomy DTO', () => {
    it('emits medium, kind, nsfw, content_rating (no legacy type)', async () => {
        await env.COMMENTS_DB.prepare(
            `INSERT INTO works_cache
             (miko_work_id, type, medium, nsfw, content_rating, title, normalized_title, provider_links, fetched_at)
             VALUES ('mk:mb:777', 'manga', 'manga', 1, 'erotica', 'Ero Manga', 'ero manga', '[]', 0)`,
        ).run();
        await env.COMMENTS_DB.prepare(
            `INSERT INTO works_titles (miko_work_id, title, normalized)
             VALUES ('mk:mb:777', 'Ero Manga', 'ero manga')`,
        ).run();
        const res = await resolve({ title: 'ero manga!', medium: 'manga' });
        expect(res.status).toBe(200);
        const body = await res.json<{ work: WorkDto }>();
        // `type` mirror is gone with the alias window — `kind` only.
        expect((body.work as unknown as Record<string, unknown>).type).toBeUndefined();
        expect(body.work.kind).toBe('manga');
        expect(body.work.medium).toBe('manga');
        expect(body.work.nsfw).toBe(true);
        expect(body.work.content_rating).toBe('erotica');
    });
});

describe('resolve — nsfw stamping from MangaBaka', () => {
    it("content_rating='erotica' persists nsfw=1 and emits nsfw:true", async () => {
        mockMbSearch('Ero Work', [mbSeries(900, 'Ero Work', { content_rating: 'erotica' })]);
        const res = await resolve({ title: 'Ero Work', medium: 'manga' });
        expect(res.status).toBe(200);
        const body = await res.json<{ work: WorkDto }>();
        expect(body.work.miko_work_id).toBe('mk:mb:900');
        expect(body.work.nsfw).toBe(true);
        expect(body.work.content_rating).toBe('erotica');
        expect(await cacheRow('mk:mb:900')).toMatchObject({
            medium: 'manga', nsfw: 1, content_rating: 'erotica',
        });
    });

    it("content_rating='suggestive' stays SFW but the raw rating is kept", async () => {
        mockMbSearch('Tease Work', [mbSeries(901, 'Tease Work', { content_rating: 'suggestive' })]);
        const res = await resolve({ title: 'Tease Work', medium: 'manga' });
        const body = await res.json<{ work: WorkDto }>();
        expect(body.work.nsfw).toBe(false);
        expect(body.work.content_rating).toBe('suggestive');
        expect(await cacheRow('mk:mb:901')).toMatchObject({
            medium: 'manga', nsfw: 0, content_rating: 'suggestive',
        });
    });

    it('a MangaBaka novel row resolves under the books carrier', async () => {
        mockMbSearch('Web Novel', [mbSeries(902, 'Web Novel', { type: 'novel' })]);
        const res = await resolve({ title: 'Web Novel', medium: 'books' });
        const body = await res.json<{ work: WorkDto }>();
        expect(body.work.miko_work_id).toBe('mk:mb:902');
        expect(body.work.kind).toBe('novel');
        expect(body.work.medium).toBe('books');
        expect((await cacheRow('mk:mb:902'))?.medium).toBe('books');
    });
});

describe('resolve — kind_hint', () => {
    it("'movie'/'series' hints are accepted and inert pre-15-02", async () => {
        for (const kind_hint of ['movie', 'series']) {
            const res = await resolve({ title: 'Nothing Real', medium: 'shows', kind_hint });
            expect(res.status).toBe(200);
            const body = await res.json<{ work: unknown }>();
            expect(body.work).toBeNull();
        }
    });

    it('out-of-vocab kind_hint is ignored, not rejected', async () => {
        const res = await resolve({ title: 'Nothing Real', medium: 'books', kind_hint: 'spaceship' });
        expect(res.status).toBe(200);
        expect(await res.json()).toMatchObject({ work: null });
    });
});

describe('resolve — audiobook kind (Phase 22 acceptance-side)', () => {
    it("a books-carrier work with kind 'audiobook' resolves and reports the kind", async () => {
        await env.COMMENTS_DB.prepare(
            `INSERT INTO works_cache
             (miko_work_id, type, medium, title, normalized_title, provider_links, fetched_at)
             VALUES ('mk:abs:a1b2c3d4e5f6:it-1', 'audiobook', 'books', 'Dune Audio', 'dune audio', '[]', 0)`,
        ).run();
        await env.COMMENTS_DB.prepare(
            `INSERT INTO external_ids (provider, external_id, miko_work_id)
             VALUES ('abs', 'a1b2c3d4e5f6:it-1', 'mk:abs:a1b2c3d4e5f6:it-1')`,
        ).run();
        const res = await resolve({
            title: 'anything', medium: 'books',
            provider: 'abs', external_id: 'a1b2c3d4e5f6:it-1',
        });
        expect(res.status).toBe(200);
        const body = await res.json<{ work: WorkDto }>();
        expect(body.work.miko_work_id).toBe('mk:abs:a1b2c3d4e5f6:it-1');
        expect(body.work.kind).toBe('audiobook');
        expect(body.work.medium).toBe('books');
    });

    it("a legacy medium-less row is admitted by the 'audiobook' kind alone", async () => {
        // medium NULL (rows predating the medium column) — admission rides
        // entirely on allowed.has('audiobook'); without the vocabulary the
        // row is filtered out and the hit returns work:null.
        await env.COMMENTS_DB.prepare(
            `INSERT INTO works_cache
             (miko_work_id, type, medium, title, normalized_title, provider_links, fetched_at)
             VALUES ('mk:abs:feedbeefcafe:it-9', 'audiobook', NULL, 'Legacy Audio', 'legacy audio', '[]', 0)`,
        ).run();
        await env.COMMENTS_DB.prepare(
            `INSERT INTO external_ids (provider, external_id, miko_work_id)
             VALUES ('abs', 'feedbeefcafe:it-9', 'mk:abs:feedbeefcafe:it-9')`,
        ).run();
        const res = await resolve({
            title: 'anything', medium: 'books',
            provider: 'abs', external_id: 'feedbeefcafe:it-9',
        });
        expect(res.status).toBe(200);
        const body = await res.json<{ work: WorkDto | null }>();
        expect(body.work?.kind).toBe('audiobook');
    });
});
