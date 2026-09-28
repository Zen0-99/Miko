import { env, fetchMock, SELF } from 'cloudflare:test';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';

// CINEMETA_BASE / HENTAISTREAM_CATALOG_URL are bound to fake origins in
// vitest.config.ts so the shows minting path (15-02) is deterministic.
const CM = 'http://cinemeta.test';
const HS = 'http://hs.test';

async function resolve(body: unknown): Promise<Response> {
    return SELF.fetch('http://test/v1/works/resolve', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify(body),
    });
}

function mockCinemeta(type: 'series' | 'movie', ttid: string, meta: unknown | null) {
    const i = fetchMock.get(CM).intercept({ path: `/meta/${type}/${ttid}.json`, method: 'GET' });
    if (meta === null) i.reply(500, 'upstream dead');
    else i.reply(200, { meta });
}

// Plain JSON — the catalog loader sniffs the gzip magic, so a raw
// catalog.json body works for tests.
const HS_CATALOG = {
    version: 2,
    catalog: [
        {
            id: 'hmm-kaede-suzu-animation',
            type: 'series',
            name: 'Kaede to Suzu The Animation',
            year: '2022',
            genres: ['Mary Jane'], // studio-name leak — must NOT affect kind
            providers: ['hmm', 'hse', 'htv'],
            providerSlugs: {
                hmm: 'kaede-suzu-animation',
                hse: 'kaede-to-suzu-the-animation',
                htv: 'kaede-to-suzu',
            },
        },
        {
            id: 'hse-2x1',
            type: 'series',
            name: '2x1',
            year: 1998,
            providers: ['hse'],
            providerSlugs: { hse: '2x1' },
        },
    ],
    slugRegistry: {
        'kaede-suzu-animation': { hmm: { id: 'hmm-kaede-suzu-animation' } },
        'kaede-to-suzu-the-animation': { hse: { id: 'hmm-kaede-suzu-animation' } },
        'kaede-to-suzu': { htv: { id: 'hmm-kaede-suzu-animation' } },
        '21': { htv: { id: 'hse-2x1' } },
    },
};

interface WorkDto {
    miko_work_id: string;
    type: string | null;
    kind: string | null;
    medium: string | null;
    nsfw: boolean;
    external_ids: Record<string, string>;
}

async function cacheRow(workId: string) {
    return env.COMMENTS_DB.prepare(
        'SELECT type, medium, nsfw FROM works_cache WHERE miko_work_id = ?',
    ).bind(workId).first<{ type: string | null; medium: string | null; nsfw: number }>();
}

async function extId(provider: string, id: string) {
    return env.COMMENTS_DB.prepare(
        'SELECT miko_work_id FROM external_ids WHERE provider = ? AND external_id = ?',
    ).bind(provider, id).first<{ miko_work_id: string }>();
}

beforeAll(() => {
    fetchMock.activate();
    fetchMock.disableNetConnect();
    // One catalog for the whole file — loadCatalog caches per isolate;
    // persist() covers a reset if module state is re-created per test.
    fetchMock.get(HS).intercept({ path: '/catalog.json', method: 'GET' })
        .reply(200, HS_CATALOG).persist();
});

afterAll(() => {
    fetchMock.deactivate();
});

describe('resolve — mk:imdb: (Cinemeta)', () => {
    it('fetches meta and mints an imdb-keyed row with derived kind', async () => {
        mockCinemeta('series', 'tt0903747', {
            id: 'tt0903747', type: 'series', name: 'Game of Thrones',
            genres: ['Action', 'Drama'], country: 'United States', year: '2011',
        });
        const res = await resolve({
            title: 'Game of Thrones', medium: 'shows',
            provider: 'imdb', external_id: 'tt0903747',
        });
        expect(res.status).toBe(200);
        const body = await res.json<{ work: WorkDto }>();
        expect(body.work.miko_work_id).toBe('mk:imdb:tt0903747');
        expect(body.work.kind).toBe('series');
        expect(body.work.medium).toBe('shows');
        expect(body.work.nsfw).toBe(false);
        expect(body.work.external_ids.imdb).toBe('tt0903747');
        expect(await cacheRow('mk:imdb:tt0903747')).toMatchObject({
            type: 'series', medium: 'shows', nsfw: 0,
        });
    });

    it('Japan + Animation derives kind=anime', async () => {
        mockCinemeta('series', 'tt0877057', {
            id: 'tt0877057', type: 'series', name: 'Death Note',
            genres: ['Animation', 'Crime', 'Drama'], country: 'Japan', year: '2006',
        });
        const res = await resolve({
            title: 'Death Note', medium: 'shows', provider: 'imdb', external_id: 'tt0877057',
        });
        const body = await res.json<{ work: WorkDto }>();
        expect(body.work.kind).toBe('anime');
        expect(await cacheRow('mk:imdb:tt0877057')).toMatchObject({ type: 'anime' });
    });

    it('western Animation without Japan stays series', async () => {
        mockCinemeta('series', 'tt2861424', {
            id: 'tt2861424', type: 'series', name: 'Rick and Morty',
            genres: ['Animation', 'Comedy'], country: 'United States', year: '2013',
        });
        const res = await resolve({
            title: 'Rick and Morty', medium: 'shows', provider: 'imdb', external_id: 'tt2861424',
        });
        expect((await res.json<{ work: WorkDto }>()).work.kind).toBe('series');
    });

    it('movie metas derive kind=movie', async () => {
        mockCinemeta('movie', 'tt0113243', {
            id: 'tt0113243', type: 'movie', name: 'Ghost in the Shell',
            genres: ['Animation', 'Action'], country: 'Japan', year: '1995',
        });
        const res = await resolve({
            title: 'Ghost in the Shell', medium: 'shows',
            provider: 'imdb', external_id: 'tt0113243', kind_hint: 'movie',
        });
        const body = await res.json<{ work: WorkDto }>();
        expect(body.work.miko_work_id).toBe('mk:imdb:tt0113243');
        expect(body.work.kind).toBe('anime'); // Japan+Animation beats the 'movie' type
    });

    it("kind_hint='movie' tries the movie meta first", async () => {
        // Both endpoints answer differently — the hint picks movie first.
        mockCinemeta('series', 'tt1234567', {
            id: 'tt1234567', type: 'series', name: 'Series Meta', country: 'United States',
        });
        mockCinemeta('movie', 'tt1234567', {
            id: 'tt1234567', type: 'movie', name: 'Movie Meta', country: 'United States',
        });
        const hinted = await resolve({
            title: 'Ambiguous', medium: 'shows',
            provider: 'imdb', external_id: 'tt1234567', kind_hint: 'movie',
        });
        expect((await hinted.json<{ work: WorkDto }>()).work.kind).toBe('movie');
    });

    it('Cinemeta outage still mints with kind=null (honest-null)', async () => {
        mockCinemeta('series', 'tt7654321', null);
        mockCinemeta('movie', 'tt7654321', null);
        const res = await resolve({
            title: 'Gone Show', medium: 'shows', provider: 'imdb', external_id: 'tt7654321',
        });
        const body = await res.json<{ work: WorkDto }>();
        expect(body.work.miko_work_id).toBe('mk:imdb:tt7654321');
        expect(body.work.kind).toBeNull();
        expect(await cacheRow('mk:imdb:tt7654321')).toMatchObject({
            type: null, medium: 'shows',
        });
        // …and the identity survives: a repeat resolve is a local
        // external-id hit, no upstream fetch needed.
        const again = await resolve({
            title: 'Gone Show', medium: 'shows', provider: 'imdb', external_id: 'tt7654321',
        });
        expect((await again.json<{ work: WorkDto }>()).work.miko_work_id)
            .toBe('mk:imdb:tt7654321');
    });

    it('bare-title shows resolves return null (no MangaBaka)', async () => {
        const res = await resolve({ title: 'Some Random Show', medium: 'shows' });
        expect((await res.json<{ work: unknown }>()).work).toBeNull();
    });

    it('unknown provider on shows returns null', async () => {
        const res = await resolve({
            title: 'Some Show', medium: 'shows', provider: 'tmdb', external_id: '42',
        });
        expect((await res.json<{ work: unknown }>()).work).toBeNull();
    });
});

describe('resolve — mk:hs: (hentaistream)', () => {
    it('canonicalizes a provider slug to the catalog id', async () => {
        const res = await resolve({
            title: 'Kaede to Suzu', medium: 'shows', provider: 'hs', external_id: 'kaede-to-suzu',
        });
        const body = await res.json<{ work: WorkDto }>();
        expect(body.work.miko_work_id).toBe('mk:hs:hmm-kaede-suzu-animation');
        expect(body.work.kind).toBe('series');
        expect(body.work.medium).toBe('shows');
        expect(body.work.nsfw).toBe(true);
        expect(await cacheRow('mk:hs:hmm-kaede-suzu-animation')).toMatchObject({
            type: 'series', medium: 'shows', nsfw: 1,
        });
    });

    it('every provider alias lands in external_ids', async () => {
        // Tests are storage-isolated — resolve first to populate.
        await resolve({
            title: 'Kaede to Suzu', medium: 'shows', provider: 'hs', external_id: 'kaede-to-suzu',
        });
        for (const alias of [
            'hmm-kaede-suzu-animation',
            'kaede-suzu-animation',
            'kaede-to-suzu-the-animation',
            'hse-kaede-to-suzu-the-animation',
            'kaede-to-suzu',
            'htv-kaede-to-suzu',
        ]) {
            const hit = await extId('hs', alias);
            expect(hit?.miko_work_id, `alias '${alias}'`).toBe('mk:hs:hmm-kaede-suzu-animation');
        }
    });

    it('registry alias slugs resolve too', async () => {
        const res = await resolve({
            title: '2x1', medium: 'shows', provider: 'hs', external_id: '21',
        });
        expect((await res.json<{ work: WorkDto }>()).work.miko_work_id).toBe('mk:hs:hse-2x1');
    });

    it('a canonical id resolves directly', async () => {
        const res = await resolve({
            title: 'x', medium: 'shows', provider: 'hs', external_id: 'hse-2x1',
        });
        expect((await res.json<{ work: WorkDto }>()).work.miko_work_id).toBe('mk:hs:hse-2x1');
    });

    it('unknown slug returns null (app falls back to mk:m:)', async () => {
        const res = await resolve({
            title: 'Nothing', medium: 'shows', provider: 'hs', external_id: 'no-such-slug',
        });
        expect((await res.json<{ work: unknown }>()).work).toBeNull();
    });
});
