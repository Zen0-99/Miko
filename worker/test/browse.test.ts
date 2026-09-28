import { env, SELF } from 'cloudflare:test';
import { describe, expect, it } from 'vitest';

// /v1/works/browse — ranked catalog slices over works_cache (Phase 15
// Discovery tab). Tests are storage-isolated; each seeds its own rows.
async function browse(qs: string): Promise<Response> {
    return SELF.fetch(`http://test/v1/works/browse${qs}`);
}

interface BrowseWork {
    miko_work_id: string;
    kind: string | null;
    title: string;
    cover_url: string | null;
    rating: number | null;
    popularity: number | null;
    year: number | null;
    nsfw: boolean;
}

async function seedWork(w: {
    id: string;
    type: string | null;
    medium: string;
    title: string;
    rating?: number | null;
    popularity?: number | null;
    year?: number | null;
    nsfw?: number;
    cover_url?: string | null;
}) {
    await env.COMMENTS_DB.prepare(
        `INSERT INTO works_cache
         (miko_work_id, type, medium, nsfw, title, normalized_title, year,
          rating, popularity, cover_url, provider_links, fetched_at)
         VALUES (?,?,?,?,?,?,?,?,?,?, '[]', 0)`,
    )
        .bind(
            w.id, w.type, w.medium, w.nsfw ?? 0, w.title,
            w.title.toLowerCase(), w.year ?? null, w.rating ?? null,
            w.popularity ?? null, w.cover_url ?? null,
        )
        .run();
}

async function ids(res: Response): Promise<string[]> {
    const body = await res.json<{ works: BrowseWork[] }>();
    return body.works.map((w) => w.miko_work_id);
}

describe('works/browse — params', () => {
    it('rejects unknown or missing medium', async () => {
        for (const qs of ['?medium=bogus', '?medium=anime', '?medium=novel', '']) {
            const res = await browse(qs);
            expect(res.status, qs).toBe(400);
            expect(await res.json()).toMatchObject({ error: 'bad_medium' });
        }
    });

    it('rejects unknown sort', async () => {
        const res = await browse('?medium=manga&sort=bogus');
        expect(res.status).toBe(400);
        expect(await res.json()).toMatchObject({ error: 'bad_sort' });
    });

    it('an empty catalog returns an empty page, not an error', async () => {
        const res = await browse('?medium=shows');
        expect(res.status).toBe(200);
        const body = await res.json<{ works: unknown[]; page: number; has_more: boolean }>();
        expect(body).toEqual({ works: [], page: 1, has_more: false });
    });
});

describe('works/browse — sorting', () => {
    it('sort=rating orders DESC with NULLs last', async () => {
        await seedWork({ id: 'mk:mb:r2', type: 'manga', medium: 'manga', title: 'Mid', rating: 70 });
        await seedWork({ id: 'mk:mb:r3', type: 'manga', medium: 'manga', title: 'Unrated' });
        await seedWork({ id: 'mk:mb:r1', type: 'manga', medium: 'manga', title: 'Top', rating: 90 });
        const res = await browse('?medium=manga&sort=rating');
        expect(res.status).toBe(200);
        expect(await ids(res)).toEqual(['mk:mb:r1', 'mk:mb:r2', 'mk:mb:r3']);
    });

    it('sort=popularity orders rank ASC with NULLs last', async () => {
        await seedWork({ id: 'mk:mb:p2', type: 'manga', medium: 'manga', title: 'B', popularity: 40 });
        await seedWork({ id: 'mk:mb:p3', type: 'manga', medium: 'manga', title: 'Unranked' });
        await seedWork({ id: 'mk:mb:p1', type: 'manga', medium: 'manga', title: 'A', popularity: 3 });
        const res = await browse('?medium=manga&sort=popularity');
        expect(await ids(res)).toEqual(['mk:mb:p1', 'mk:mb:p2', 'mk:mb:p3']);
    });

    it('absent sort defaults to popularity', async () => {
        await seedWork({ id: 'mk:mb:d2', type: 'manga', medium: 'manga', title: 'B', popularity: 40, rating: 99 });
        await seedWork({ id: 'mk:mb:d1', type: 'manga', medium: 'manga', title: 'A', popularity: 3, rating: 10 });
        const res = await browse('?medium=manga');
        expect(await ids(res)).toEqual(['mk:mb:d1', 'mk:mb:d2']);
    });

    it('sort=newest orders year DESC with NULLs last', async () => {
        await seedWork({ id: 'mk:mb:y2', type: 'manga', medium: 'manga', title: 'Old', year: 1999 });
        await seedWork({ id: 'mk:mb:y3', type: 'manga', medium: 'manga', title: 'Undated' });
        await seedWork({ id: 'mk:mb:y1', type: 'manga', medium: 'manga', title: 'New', year: 2024 });
        const res = await browse('?medium=manga&sort=newest');
        expect(await ids(res)).toEqual(['mk:mb:y1', 'mk:mb:y2', 'mk:mb:y3']);
    });
});

describe('works/browse — filters', () => {
    it('comma-separated kind list narrows within the carrier', async () => {
        await seedWork({ id: 'mk:mb:k1', type: 'manga', medium: 'manga', title: 'M', popularity: 1 });
        await seedWork({ id: 'mk:mb:k2', type: 'manhwa', medium: 'manga', title: 'H', popularity: 2 });
        await seedWork({ id: 'mk:mb:k3', type: 'manhua', medium: 'manga', title: 'C', popularity: 3 });
        const res = await browse('?medium=manga&kind=manga,manhwa&sort=popularity');
        expect(await ids(res)).toEqual(['mk:mb:k1', 'mk:mb:k2']);
    });

    it('absent kind returns every kind under the carrier', async () => {
        await seedWork({ id: 'mk:mb:a1', type: 'manga', medium: 'manga', title: 'M', popularity: 1 });
        await seedWork({ id: 'mk:mb:a2', type: 'oel', medium: 'manga', title: 'O', popularity: 2 });
        await seedWork({ id: 'mk:mb:a3', type: 'webtoon', medium: 'manga', title: 'W', popularity: 3 });
        const res = await browse('?medium=manga&sort=popularity');
        expect((await ids(res)).sort()).toEqual(['mk:mb:a1', 'mk:mb:a2', 'mk:mb:a3']);
    });

    it('never crosses the carrier boundary', async () => {
        await seedWork({ id: 'mk:mb:x1', type: 'novel', medium: 'books', title: 'N', popularity: 1 });
        await seedWork({ id: 'mk:mb:x2', type: 'manga', medium: 'manga', title: 'M', popularity: 2 });
        const manga = await browse('?medium=manga&sort=popularity');
        expect(await ids(manga)).toEqual(['mk:mb:x2']);
        const books = await browse('?medium=books&sort=popularity');
        expect(await ids(books)).toEqual(['mk:mb:x1']);
    });

    it('a kind outside the carrier matches nothing (no cross-talk)', async () => {
        await seedWork({ id: 'mk:mb:c1', type: 'novel', medium: 'books', title: 'N', popularity: 1 });
        const res = await browse('?medium=manga&kind=novel');
        expect(await ids(res)).toEqual([]);
    });
});

describe('works/browse — pagination + shape', () => {
    it('pages with limit and has_more', async () => {
        for (let i = 1; i <= 3; i++) {
            await seedWork({
                id: `mk:mb:pg${i}`, type: 'manga', medium: 'manga',
                title: `P${i}`, popularity: i,
            });
        }
        const p1 = await browse('?medium=manga&sort=popularity&limit=2&page=1');
        const b1 = await p1.json<{ works: BrowseWork[]; page: number; has_more: boolean }>();
        expect(b1.works.map((w) => w.miko_work_id)).toEqual(['mk:mb:pg1', 'mk:mb:pg2']);
        expect(b1.page).toBe(1);
        expect(b1.has_more).toBe(true);

        const p2 = await browse('?medium=manga&sort=popularity&limit=2&page=2');
        const b2 = await p2.json<{ works: BrowseWork[]; page: number; has_more: boolean }>();
        expect(b2.works.map((w) => w.miko_work_id)).toEqual(['mk:mb:pg3']);
        expect(b2.page).toBe(2);
        expect(b2.has_more).toBe(false);
    });

    it('clamps limit to 100 and defaults page to 1', async () => {
        await seedWork({ id: 'mk:mb:cl1', type: 'manga', medium: 'manga', title: 'C', popularity: 1 });
        const res = await browse('?medium=manga&limit=9999&page=abc');
        const body = await res.json<{ works: BrowseWork[]; page: number }>();
        expect(body.page).toBe(1);
        expect(body.works).toHaveLength(1);
    });

    it('emits the card shape with kind + boolean nsfw', async () => {
        await seedWork({
            id: 'mk:mb:sh1', type: 'manhwa', medium: 'manga', title: 'Card',
            rating: 81.5, popularity: 7, year: 2019, nsfw: 1,
            cover_url: 'https://img.example/x250.webp',
        });
        const res = await browse('?medium=manga&sort=popularity');
        const body = await res.json<{ works: BrowseWork[] }>();
        expect(body.works[0]).toEqual({
            miko_work_id: 'mk:mb:sh1',
            kind: 'manhwa',
            title: 'Card',
            cover_url: 'https://img.example/x250.webp',
            rating: 81.5,
            popularity: 7,
            year: 2019,
            nsfw: true,
        });
    });
});
