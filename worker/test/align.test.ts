import { env, SELF } from 'cloudflare:test';
import { describe, expect, it } from 'vitest';

async function align(body: unknown): Promise<Response> {
    return SELF.fetch('http://test/v1/chapters/align', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify(body),
    });
}

const BASE = { work_id: 'mk:mb:1', medium: 'books' };

/** Seed canonical episode mains for a shows work. */
async function seedCanonEpisodes(workId: string, absNumbers: number[]) {
    const ts = Date.now();
    const stmts = absNumbers.map((n, i) =>
        env.COMMENTS_DB.prepare(
            `INSERT INTO canonical_episodes
             (work_id, seq, kind, number, abs_number, label, confidence,
              sources_json, refs_json, updated_at)
             VALUES (?,?,?,?,?,?,?,?,?,?)`,
        ).bind(workId, i, 'episode', n, n, `Episode ${n}`, 'verified',
            '["mikodb:map"]', '{}', ts),
    );
    await env.COMMENTS_DB.batch(stmts);
}

describe('chapters/align', () => {
    it('rejects malformed bodies', async () => {
        expect((await align({})).status).toBe(400);
        expect((await align({ ...BASE, source_key: 's1', numbers: [] })).status).toBe(400);
        expect(
            (await align({ ...BASE, source_key: 's1', numbers: [NaN] })).status,
        ).toBe(400);
    });

    it('shows: aligns against canonical_episodes abs numbers', async () => {
        const work = 'mk:imdb:tt0944947';
        await seedCanonEpisodes(work, [1, 2, 3]);
        const res = await align({
            work_id: work, source_key: 's1',
            medium: 'shows', numbers: [1, 2, 3],
        });
        expect(res.status).toBe(200);
        expect(await res.json()).toMatchObject({
            status: 'aligned', score: 1, reference: 'canonical_episodes',
        });
    });

    it('shows: disjoint numbers misalign against canonical_episodes', async () => {
        const work = 'mk:imdb:tt-misaligned';
        await seedCanonEpisodes(work, [1, 2, 3]);
        const res = await align({
            work_id: work, source_key: 's1',
            medium: 'shows', numbers: [50, 51, 52],
        });
        expect(res.status).toBe(200);
        expect(await res.json()).toMatchObject({
            status: 'misaligned', score: 0, reference: 'canonical_episodes',
        });
    });

    it('shows: no canonical rows and <2 fingerprints → unknown', async () => {
        const res = await align({
            work_id: 'mk:imdb:tt-nocanon', source_key: 's1',
            medium: 'shows', numbers: [1, 2, 3],
        });
        expect(res.status).toBe(200);
        expect(await res.json()).toMatchObject({ status: 'unknown', reference: 'none' });
    });

    it('shows: falls back to episode_fingerprints consensus', async () => {
        const work = 'mk:imdb:tt-consensus';
        await align({ work_id: work, source_key: 'a', medium: 'shows', numbers: [1, 2, 3] });
        await align({ work_id: work, source_key: 'b', medium: 'shows', numbers: [1, 2, 3] });
        const res = await align({
            work_id: work, source_key: 'c', medium: 'shows', numbers: [1, 2, 3],
        });
        expect(await res.json()).toMatchObject({
            status: 'aligned', score: 1, reference: 'consensus',
        });
    });

    it('shows: fingerprints and verdicts stay out of chapter tables', async () => {
        const work = 'mk:imdb:tt-sibling';
        await align({ work_id: work, source_key: 'a', medium: 'shows', numbers: [1, 2] });
        const fp = await env.COMMENTS_DB.prepare(
            'SELECT COUNT(*) AS c FROM episode_fingerprints WHERE work_id = ?',
        ).bind(work).first<{ c: number }>();
        expect(fp?.c).toBe(1);
        const al = await env.COMMENTS_DB.prepare(
            'SELECT COUNT(*) AS c FROM episode_alignments WHERE work_id = ?',
        ).bind(work).first<{ c: number }>();
        expect(al?.c).toBe(1);
        const cfp = await env.COMMENTS_DB.prepare(
            'SELECT COUNT(*) AS c FROM chapter_fingerprints WHERE work_id = ?',
        ).bind(work).first<{ c: number }>();
        expect(cfp?.c).toBe(0);
        const cal = await env.COMMENTS_DB.prepare(
            'SELECT COUNT(*) AS c FROM chapter_alignments WHERE work_id = ?',
        ).bind(work).first<{ c: number }>();
        expect(cal?.c).toBe(0);
    });

    it('legacy anime medium no longer aliases to shows (window closed)', async () => {
        // 'anime' is an unrecognized medium now — it doesn't trigger the
        // shows skip, it just runs the normal alignment path.
        const res = await align({
            work_id: 'mk:imdb:tt0944947', source_key: 's1',
            medium: 'anime', numbers: [1, 2, 3],
        });
        expect(res.status).toBe(200);
        expect(await res.json()).toMatchObject({ status: 'unknown' });
    });

    it('returns unknown with no reference and no consensus', async () => {
        const res = await align({ ...BASE, source_key: 'first', numbers: [1, 2, 3] });
        expect(res.status).toBe(200);
        expect(await res.json()).toMatchObject({ status: 'unknown', reference: 'none' });
    });

    it('aligns against consensus once two other sources agree', async () => {
        const work = 'mk:mb:consensus';
        await align({ ...BASE, work_id: work, source_key: 'a', numbers: [1, 2, 3, 4] });
        await align({ ...BASE, work_id: work, source_key: 'b', numbers: [1, 2, 3, 4] });
        const res = await align({ ...BASE, work_id: work, source_key: 'c', numbers: [1, 2, 3, 4] });
        expect(await res.json()).toMatchObject({ status: 'aligned', score: 1, reference: 'consensus' });
    });

    it('marks disjoint numbers misaligned', async () => {
        const work = 'mk:mb:misaligned';
        await align({ ...BASE, work_id: work, source_key: 'a', numbers: [1, 2, 3] });
        await align({ ...BASE, work_id: work, source_key: 'b', numbers: [1, 2, 3] });
        const res = await align({ ...BASE, work_id: work, source_key: 'c', numbers: [50, 51, 52] });
        expect(await res.json()).toMatchObject({ status: 'misaligned', score: 0 });
    });

    it('a source cannot self-confirm consensus', async () => {
        const work = 'mk:mb:solo';
        await align({ ...BASE, work_id: work, source_key: 'a', numbers: [1, 2] });
        const res = await align({ ...BASE, work_id: work, source_key: 'b', numbers: [1, 2] });
        // Only one other source — no consensus, so unknown.
        expect(await res.json()).toMatchObject({ status: 'unknown' });
    });

    it('applies aliases before scoring', async () => {
        const work = 'mk:mb:alias';
        await align({ ...BASE, work_id: work, source_key: 'a', numbers: [1, 2, 3] });
        await align({ ...BASE, work_id: work, source_key: 'b', numbers: [1, 2, 3] });
        // Source 'c' reports a prologue as 0 which canonically is 1.
        await env.COMMENTS_DB.prepare(
            'INSERT INTO chapter_aliases (work_id, source_key, reported, canonical) VALUES (?,?,?,?)',
        ).bind(work, 'c', 0, 1).run();
        const res = await align({ ...BASE, work_id: work, source_key: 'c', numbers: [0, 2, 3] });
        expect(await res.json()).toMatchObject({ status: 'aligned', score: 1 });
    });

    it('caches the verdict per fingerprint', async () => {
        const work = 'mk:mb:cached';
        await align({ ...BASE, work_id: work, source_key: 'a', numbers: [1, 2] });
        const first = await (await align({ ...BASE, work_id: work, source_key: 'b', numbers: [1, 2] })).json() as { status: string };
        // Same fingerprint again — served from chapter_alignments.
        const second = await (await align({ ...BASE, work_id: work, source_key: 'b', numbers: [1, 2] })).json() as { status: string };
        expect(second.status).toBe(first.status);
    });
});
