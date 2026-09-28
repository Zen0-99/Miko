import { env, fetchMock, SELF } from 'cloudflare:test';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';

const OL = 'http://ol.test';

async function resolve(body: unknown): Promise<Response> {
    return SELF.fetch('http://test/v1/works/resolve', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify(body),
    });
}

function mockIsbn(isbn: string, workKey: string | null, title = 'Dune') {
    fetchMock
        .get(OL)
        .intercept({ path: `/isbn/${isbn}.json`, method: 'GET' })
        .reply(200, {
            key: `/books/OL1M`,
            title,
            isbn_13: [isbn],
            works: workKey ? [{ key: workKey }] : [],
        });
}

function mockSearch(docs: unknown[]) {
    fetchMock
        .get(OL)
        .intercept({ path: /^\/search\.json/, method: 'GET' })
        .reply(200, { docs });
}

async function reviewCount(): Promise<number> {
    const row = await env.COMMENTS_DB.prepare(
        'SELECT COUNT(*) AS n FROM identity_review',
    ).first<{ n: number }>();
    return row?.n ?? 0;
}

beforeAll(() => {
    fetchMock.activate();
    fetchMock.disableNetConnect();
});

afterAll(() => {
    fetchMock.deactivate();
});

// MangaBaka is bound to an unreachable origin in tests, so book requests
// fall through to OpenLibrary — the path under test.
describe('resolve — books (OpenLibrary)', () => {
    it('ISBN lookup rolls an edition up to a work', async () => {
        mockIsbn('9780441172719', '/works/OL362427W');
        const res = await resolve({
            title: 'Dune', medium: 'books', kind_hint: 'book', isbn: '9780441172719',
        });
        expect(res.status).toBe(200);
        const body = await res.json<{ work: { miko_work_id: string; type: string; external_ids: Record<string, string> } }>();
        expect(body.work.miko_work_id).toBe('mk:ol:OL362427W');
        expect(body.work.type).toBe('book');
        expect(body.work.external_ids.openlibrary).toBe('OL362427W');
        expect(body.work.external_ids.isbn).toBe('9780441172719');
    });

    it('legacy medium=novel + kind=book still gates OpenLibrary (alias window)', async () => {
        mockIsbn('9780553418026', '/works/OL888W');
        const res = await resolve({
            title: 'The Martian', medium: 'novel', kind: 'book', isbn: '9780553418026',
        });
        expect(res.status).toBe(200);
        const body = await res.json<{ work: { miko_work_id: string } }>();
        expect(body.work.miko_work_id).toBe('mk:ol:OL888W');
    });

    it('two editions of the same book converge on one work', async () => {
        // Second request uses a distinct title so it can't hit the
        // normalized-title cache — it must converge via its own ISBN lookup.
        mockIsbn('9780593099322', '/works/OL999W');
        mockIsbn('9780399153280', '/works/OL999W', 'Foundation (reissue)');
        const a = await resolve({ title: 'Foundation', medium: 'books', kind_hint: 'book', isbn: '9780593099322' });
        const b = await resolve({ title: 'Foundation (reissue)', medium: 'books', kind_hint: 'book', isbn: '9780399153280' });
        const wa = (await a.json<{ work: { miko_work_id: string } }>()).work;
        const wb = (await b.json<{ work: { miko_work_id: string } }>()).work;
        expect(wa.miko_work_id).toBe('mk:ol:OL999W');
        expect(wb.miko_work_id).toBe('mk:ol:OL999W');
    });

    it('title+author search mints a work from a doc', async () => {
        mockSearch([{
            key: '/works/OL123W', title: 'Neuromancer',
            author_name: ['William Gibson'], first_publish_year: 1984,
            isbn: ['9780441569595'],
        }]);
        const res = await resolve({
            title: 'Neuromancer', medium: 'books', kind_hint: 'book',
            author: 'William Gibson', year: 1984,
        });
        const body = await res.json<{ work: { miko_work_id: string; type: string; year: number } }>();
        expect(body.work.miko_work_id).toBe('mk:ol:OL123W');
        expect(body.work.type).toBe('book');
        expect(body.work.year).toBe(1984);
    });

    it('weak title-only matches do not mint', async () => {
        mockSearch([{ key: '/works/OL5W', title: 'Completely Different' }]);
        const res = await resolve({ title: 'Ringworld', medium: 'books', kind_hint: 'book', author: 'Niven' });
        const body = await res.json<{ work: unknown }>();
        expect(body.work).toBeNull();
    });

    it('tied candidates are reviewed, not minted', async () => {
        mockSearch([
            { key: '/works/OL10W', title: 'Hyperion', author_name: ['Dan Simmons'] },
            { key: '/works/OL11W', title: 'Hyperion', author_name: ['Dan Simmons'] },
        ]);
        const res = await resolve({ title: 'Hyperion', medium: 'books', kind_hint: 'book', author: 'Dan Simmons' });
        const body = await res.json<{ work: unknown }>();
        expect(body.work).toBeNull();
        expect(await reviewCount()).toBeGreaterThan(0);
    });

    it('an edition with no work rollup is reviewed, not minted', async () => {
        mockIsbn('9780000000001', null);
        const res = await resolve({ title: 'Orphan Book', medium: 'books', kind_hint: 'book', isbn: '9780000000001' });
        const body = await res.json<{ work: unknown }>();
        expect(body.work).toBeNull();
    });

    it('plain webnovel requests never reach OpenLibrary', async () => {
        // No kind_hint='book', no ISBN — MangaBaka unreachable → null without OL.
        const res = await resolve({ title: 'Shadow Slave', medium: 'books' });
        const body = await res.json<{ work: unknown }>();
        expect(body.work).toBeNull();
    });

    it('manga medium never reaches OpenLibrary even with an ISBN', async () => {
        const res = await resolve({ title: 'Berserk', medium: 'manga', isbn: '9780441172719' });
        const body = await res.json<{ work: unknown }>();
        expect(body.work).toBeNull();
    });

    it('cached book rows satisfy later books resolves', async () => {
        await env.COMMENTS_DB.prepare(
            `INSERT INTO works_cache
             (miko_work_id, type, title, normalized_title, provider_links, fetched_at)
             VALUES ('mk:ol:OL42W', 'book', 'Dune Messiah', 'dune messiah', '[]', 0)`,
        ).run();
        await env.COMMENTS_DB.prepare(
            `INSERT INTO works_titles (miko_work_id, title, normalized)
             VALUES ('mk:ol:OL42W', 'Dune Messiah', 'dune messiah')`,
        ).run();
        // Plain books resolve — 'book' kind is inside the books allowed set.
        const res = await resolve({ title: 'dune messiah!', medium: 'books' });
        const body = await res.json<{ work: { miko_work_id: string } }>();
        expect(body.work.miko_work_id).toBe('mk:ol:OL42W');
    });

    it('ISBN external_id hits the cache without fetching', async () => {
        await env.COMMENTS_DB.prepare(
            `INSERT INTO works_cache
             (miko_work_id, type, title, normalized_title, provider_links, fetched_at)
             VALUES ('mk:ol:OL77W', 'book', 'Dune', 'dune', '[]', 0)`,
        ).run();
        await env.COMMENTS_DB.prepare(
            `INSERT INTO external_ids (provider, external_id, miko_work_id)
             VALUES ('isbn', '9780441172799', 'mk:ol:OL77W')`,
        ).run();
        const res = await resolve({
            title: 'anything', medium: 'books', kind_hint: 'book',
            provider: 'isbn', external_id: '9780441172799',
        });
        const body = await res.json<{ work: { miko_work_id: string } }>();
        expect(body.work.miko_work_id).toBe('mk:ol:OL77W');
    });
});

describe('book comment threads', () => {
    async function postAuth(path: string, body: unknown, token: string) {
        return SELF.fetch(`http://test${path}`, {
            method: 'POST',
            headers: { 'content-type': 'application/json', Authorization: `Bearer ${token}` },
            body: JSON.stringify(body),
        });
    }

    it('two editions share one comment thread', async () => {
        // Resolve edition A → post; resolve edition B → same thread id.
        mockIsbn('9781501111125', '/works/OL555W');
        mockIsbn('9781501199947', '/works/OL555W');
        const a = await resolve({ title: 'The Way of Kings', medium: 'books', kind_hint: 'book', isbn: '9781501111125' });
        const b = await resolve({ title: 'The Way of Kings (ebook)', medium: 'books', kind_hint: 'book', isbn: '9781501199947' });
        const wa = (await a.json<{ work: { miko_work_id: string } }>()).work;
        const wb = (await b.json<{ work: { miko_work_id: string } }>()).work;
        expect(wa.miko_work_id).toBe(wb.miko_work_id);

        const thread = `books:${wa.miko_work_id}`;
        const regA = await SELF.fetch('http://test/v1/device/register', {
            method: 'POST',
            headers: { 'content-type': 'application/json' },
            body: JSON.stringify({ device_hash: 'c'.repeat(64) }),
        });
        const { token: ta } = await regA.json<{ token: string }>();
        await postAuth('/v1/names', { name: 'Wit' }, ta);
        const posted = await postAuth('/v1/comments', { thread_id: thread, body: 'book thread!' }, ta);
        expect(posted.status).toBe(201);

        const res = await SELF.fetch(`http://test/v1/comments?thread_id=${thread}`);
        const body = await res.json<{ comments: Array<{ body: string }> }>();
        expect(body.comments.map((c) => c.body)).toContain('book thread!');
    });
});
