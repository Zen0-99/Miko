import { env, SELF } from 'cloudflare:test';
import { describe, expect, it } from 'vitest';
import { parseChapter } from '../src/chapterparse';
import golden from './chapter_parser_golden.json';

interface Vector {
    in: string;
    number: number | null;
    volume: number | null;
    part: number | null;
    kind: string;
}

describe('chapter parser golden vectors', () => {
    for (const v of golden as Vector[]) {
        it(`parses ${JSON.stringify(v.in)}`, () => {
            const p = parseChapter(v.in);
            expect(p.number).toBe(v.number);
            expect(p.volume).toBe(v.volume);
            expect(p.part).toBe(v.part);
            expect(p.kind).toBe(v.kind);
        });
    }
});

async function canonize(body: unknown): Promise<Response> {
    return SELF.fetch('http://test/v1/chapters/canonize', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify(body),
    });
}

async function getChapters(workId: string): Promise<Response> {
    return SELF.fetch(`http://test/v1/works/${encodeURIComponent(workId)}/chapters`);
}

function feed(src: string, names: string[]) {
    return { source_key: src, chapters: names.map((name) => ({ name })) };
}

describe('chapters/canonize', () => {
    it('rejects malformed bodies', async () => {
        expect((await canonize({})).status).toBe(400);
        expect((await canonize({ work_id: 'mk:mb:1' })).status).toBe(400);
        expect(
            (await canonize({ work_id: 'mk:mb:1', feeds: [{ chapters: [] }] })).status,
        ).toBe(400);
    });

    it('merges two golden feeds into one verified sequence', async () => {
        const work = 'mk:mb:merge1';
        const res = await canonize({
            work_id: work,
            feeds: [
                feed('mangadex', ['Prologue', 'Chapter 1', 'Chapter 2', 'Chapter 3']),
                feed('webtoons', ['Prologue', 'Ch. 1', 'Ch. 2', 'Ch. 3']),
            ],
        });
        expect(res.status).toBe(200);
        const body = await res.json() as any;
        expect(body.count).toBe(4);
        expect(body.verified).toBe(4);

        const list = await (await getChapters(work)).json() as any;
        expect(list.chapters.map((c: { number: number | null }) => c.number))
            .toEqual([null, 1, 2, 3]);
        expect(list.chapters[0].kind).toBe('prologue');
        expect(list.chapters[1].sources).toContain('mangadex');
        expect(list.chapters[1].sources).toContain('webtoons');
    });

    it('keeps single-source chapters with single confidence', async () => {
        const res = await canonize({
            work_id: 'mk:mb:single1',
            feeds: [feed('solo', ['Chapter 1', 'Chapter 2'])],
        });
        const body = await res.json() as any;
        expect(body.count).toBe(2);
        expect(body.verified).toBe(0);
    });

    it('never merges unknown/garbage names', async () => {
        const res = await canonize({
            work_id: 'mk:mb:garbage1',
            feeds: [
                feed('a', ['Chapter 1', 'Cahapteare1', 'afkj23ds']),
                feed('b', ['Ch. 1', 'x9f!@#', 'garbage name']),
            ],
        });
        const body = await res.json() as any;
        expect(body.count).toBe(1);
        expect(body.skipped).toBe(4);
        const list = await (await getChapters('mk:mb:garbage1')).json() as any;
        expect(list.chapters).toHaveLength(1);
    });

    it('parts and decimals survive the merge', async () => {
        const work = 'mk:mb:parts1';
        await canonize({
            work_id: work,
            feeds: [
                feed('a', ['Chapter 30 Part 1', 'Ch. 30 Part 2', 'Chap. 142.5']),
                feed('b', ['30a', '30b', 'Chapter 142.5']),
            ],
        });
        const list = await (await getChapters(work)).json() as any;
        const nums = list.chapters.map((c: { number: number | null; part: number | null }) => [c.number, c.part]);
        expect(nums).toContainEqual([30, 1]);
        expect(nums).toContainEqual([30, 2]);
        expect(nums).toContainEqual([142.5, null]);
        // 30 Part 1 ↔ "30a" and Part 2 ↔ "30b" merge cross-source.
        expect(list.chapters.every((c: { confidence: string }) => c.confidence === 'verified'))
            .toBe(true);
    });

    it('flags a divergent feed to identity_review', async () => {
        const work = 'mk:mb:diverge1';
        const common = Array.from({ length: 12 }, (_, i) => `Chapter ${i + 1}`);
        const off = Array.from({ length: 12 }, (_, i) => `Chapter ${i + 100}`);
        await canonize({ work_id: work, feeds: [feed('a', common), feed('b', common), feed('bad', off)] });
        const { results } = await env.COMMENTS_DB.prepare(
            "SELECT kind FROM identity_review WHERE kind = 'chapter_feed_divergent'",
        ).all<{ kind: string }>();
        expect(results.length).toBeGreaterThan(0);
    });

    it('tentative feeds merge but flag identity_review', async () => {
        const work = 'mk:mb:tent1';
        const res = await canonize({
            work_id: work,
            feeds: [
                feed('mangadex', ['Chapter 1', 'Chapter 2']),
                {
                    source_key: 'asura',
                    verified: false,
                    score: 0.83,
                    chapters: [{ name: 'Chapter 1' }, { name: 'Chapter 2' }],
                },
            ],
        });
        expect(res.status).toBe(200);
        const body = await res.json() as any;
        expect(body.count).toBe(2);
        const { results } = await env.COMMENTS_DB.prepare(
            "SELECT kind, payload FROM identity_review WHERE kind = 'chapter_feed_tentative'",
        ).all<{ kind: string; payload: string }>();
        expect(results.length).toBeGreaterThan(0);
        const payload = JSON.parse(results[0].payload) as { work_id: string; feeds: { source_key: string }[] };
        expect(payload.work_id).toBe(work);
        expect(payload.feeds.map((f) => f.source_key)).toContain('asura');
    });

    it('fully verified feeds write no tentative review', async () => {
        const work = 'mk:mb:notent1';
        await canonize({
            work_id: work,
            feeds: [
                feed('mangadex', ['Chapter 1']),
                { source_key: 'asura', verified: true, chapters: [{ name: 'Ch. 1' }] },
            ],
        });
        const { results } = await env.COMMENTS_DB.prepare(
            "SELECT kind FROM identity_review WHERE kind = 'chapter_feed_tentative' AND payload LIKE ?",
        ).bind(`%${work}%`).all<{ kind: string }>();
        expect(results).toHaveLength(0);
    });

    it('returns an empty list for an uncanonized work', async () => {
        const list = await (await getChapters('mk:mb:never')).json() as any;
        expect(list.chapters).toEqual([]);
    });

    it('recanonize replaces the sequence', async () => {
        const work = 'mk:mb:rebuild1';
        await canonize({ work_id: work, feeds: [feed('a', ['Chapter 1', 'Chapter 2'])] });
        await canonize({ work_id: work, feeds: [feed('a', ['Chapter 1'])] });
        const list = await (await getChapters(work)).json() as any;
        expect(list.chapters).toHaveLength(1);
    });
});
