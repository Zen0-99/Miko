import { env, SELF } from 'cloudflare:test';
import { describe, expect, it } from 'vitest';

async function canonize(body: unknown): Promise<Response> {
    return SELF.fetch('http://test/v1/episodes/canonize', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify(body),
    });
}

async function getEpisodes(workId: string): Promise<Response> {
    return SELF.fetch(`http://test/v1/works/${encodeURIComponent(workId)}/episodes`);
}

/** Artifact ('mikodb:map') main-episode rows — the full structured set
 *  an honest device emits from its on-device anime-map rows. */
function artifactEps(n: number) {
    return Array.from({ length: n }, (_, i) => ({
        name: `Canon Episode ${i + 1}`,
        key: `${i + 1}`,
        kind: 'episode',
        number: i + 1,
        abs: i + 1,
        season: 1,
        ep_in_season: i + 1,
        doc: 'anidb:1',
        anidb_eid: 100 + i,
        tvdb_eid: 200 + i,
        tvdb_show_id: 300,
        air_date: `2024-01-${String(i + 1).padStart(2, '0')}`,
        image: `https://img/e${i + 1}.jpg`,
        overview: `Overview ${i + 1}`,
        runtime: 24,
    }));
}

/** Carrier main episodes — name/url/number + optional canonicalized abs. */
function carrierEps(n: number, { abs = true }: { abs?: boolean } = {}) {
    return Array.from({ length: n }, (_, i) => ({
        name: `Episode ${i + 1}`,
        url: `https://kage/e${i + 1}`,
        kind: 'episode',
        number: i + 1,
        ...(abs ? { abs: i + 1 } : {}),
    }));
}

describe('episodes/canonize', () => {
    it('rejects malformed bodies', async () => {
        expect((await canonize({})).status).toBe(400);
        expect((await canonize({ work_id: 'mk:imdb:tt1' })).status).toBe(400);
        expect(
            (await canonize({ work_id: 'mk:imdb:tt1', feeds: [{ episodes: [] }] })).status,
        ).toBe(400);
        expect(
            (await canonize({
                work_id: 'mk:imdb:tt1',
                feeds: [{ source_key: 's', episodes: [{ url: 'x' }] }],
            })).status,
        ).toBe(400);
    });

    it('artifact + carrier feed merge into verified mains', async () => {
        const work = 'mk:imdb:tt900001';
        const res = await canonize({
            work_id: work,
            feeds: [
                { source_key: 'mikodb:map', verified: true, episodes: artifactEps(3) },
                { source_key: 'aniyomi.en.kage', episodes: carrierEps(3) },
            ],
        });
        expect(res.status).toBe(200);
        const body = await res.json() as any;
        expect(body.count).toBe(3);
        expect(body.verified).toBe(3);

        const list = await (await getEpisodes(work)).json() as any;
        expect(list.episodes).toHaveLength(3);
        const ep1 = list.episodes[0];
        expect(ep1.kind).toBe('episode');
        expect(ep1.abs).toBe(1);
        expect(ep1.number).toBe(1);
        expect(ep1.ep_key).toBe('1');
        expect(ep1.doc).toBe('anidb:1');
        expect(ep1.confidence).toBe('verified');
        expect(ep1.sources).toContain('mikodb:map');
        expect(ep1.sources).toContain('aniyomi.en.kage');
        expect(ep1.refs['aniyomi.en.kage']).toBe('https://kage/e1');
        // Artifact metadata lands on the merged row (D-02 surface).
        expect(ep1.air_date).toBe('2024-01-01');
        expect(ep1.date_upload).toBe(Date.parse('2024-01-01'));
        expect(ep1.image).toBe('https://img/e1.jpg');
        expect(ep1.runtime).toBe(24);
        expect(ep1.anidb_eid).toBe(100);
    });

    it('carrier-only feeds never reach verified', async () => {
        const work = 'mk:imdb:tt900002';
        const res = await canonize({
            work_id: work,
            feeds: [
                { source_key: 'kage', episodes: carrierEps(3) },
                { source_key: 'animepahe', episodes: carrierEps(3, { abs: false }) },
            ],
        });
        expect(res.status).toBe(200);
        const body = await res.json() as any;
        // Two carriers agreeing on numbers merge — but no artifact.
        expect(body.count).toBe(3);
        expect(body.verified).toBe(0);
        const list = await (await getEpisodes(work)).json() as any;
        expect(list.episodes.every((e: { confidence: string }) => e.confidence === 'single'))
            .toBe(true);
    });

    it('specials and nc rows merge on their own keys, never into mains', async () => {
        const work = 'mk:imdb:tt900003';
        const res = await canonize({
            work_id: work,
            feeds: [{
                source_key: 'mikodb:map',
                verified: true,
                episodes: [
                    ...artifactEps(2),
                    {
                        name: 'Special 1', key: 'S1', kind: 'special',
                        season: 0, ep_in_season: 1, doc: 'anidb:1', anidb_eid: 500,
                    },
                    {
                        name: 'NCOP 1', key: 'C1', kind: 'nc',
                        number: 1, doc: 'anidb:1', anidb_eid: 501,
                    },
                ],
            }],
        });
        expect(res.status).toBe(200);
        const body = await res.json() as any;
        expect(body.count).toBe(4);
        const list = await (await getEpisodes(work)).json() as any;
        expect(list.episodes).toHaveLength(4);
        expect(list.episodes.map((e: { kind: string }) => e.kind))
            .toEqual(['episode', 'episode', 'special', 'nc']);
        const sp = list.episodes[2];
        expect(sp.season).toBe(0);
        expect(sp.ep_in_season).toBe(1);
        expect(sp.ep_key).toBe('S1');
        const nc = list.episodes[3];
        expect(nc.ep_key).toBe('C1');
        // Mains untouched — seq order keeps mains first.
        expect(list.episodes[0].abs).toBe(1);
        expect(list.episodes[1].abs).toBe(2);
    });

    it('a forged mikodb:map feed demotes to feed:mikodb:map and grants no authority', async () => {
        const work = 'mk:imdb:tt900004';
        const res = await canonize({
            work_id: work,
            feeds: [{
                source_key: 'mikodb:map',
                verified: true,
                // No ep_key/doc/anidb_eid — not artifact-shaped.
                episodes: [
                    { name: 'Ep 1', url: 'https://evil/1', number: 1 },
                    { name: 'Ep 2', url: 'https://evil/2', number: 2 },
                ],
            }],
        });
        expect(res.status).toBe(200);
        const { results: feedRows } = await env.COMMENTS_DB.prepare(
            'SELECT source_key FROM canonical_feeds WHERE work_id = ?',
        ).bind(work).all<{ source_key: string }>();
        expect(feedRows.map((r) => r.source_key)).toEqual(['feed:mikodb:map']);
        const list = await (await getEpisodes(work)).json() as any;
        expect(list.episodes.every((e: { confidence: string }) => e.confidence === 'single'))
            .toBe(true);
        const { results: reviews } = await env.COMMENTS_DB.prepare(
            "SELECT payload FROM identity_review WHERE kind = 'artifact_feed_unstructured'",
        ).all<{ payload: string }>();
        expect(reviews.length).toBeGreaterThan(0);
        expect(reviews[0].payload).toContain(work);
    });

    it('flags divergent feeds disagreeing on an episode abs to identity_review', async () => {
        const work = 'mk:imdb:tt900005';
        const epsA = carrierEps(12);
        const epsB = carrierEps(12).map((e, i) => (i === 4 ? { ...e, abs: 105 } : e));
        const res = await canonize({
            work_id: work,
            feeds: [
                { source_key: 'a', episodes: epsA },
                { source_key: 'b', episodes: epsB },
            ],
        });
        expect(res.status).toBe(200);
        const body = await res.json() as any;
        // e|5 and e|105 land as separate single-source rows.
        expect(body.count).toBe(13);
        const { results } = await env.COMMENTS_DB.prepare(
            "SELECT payload FROM identity_review WHERE kind = 'episode_feed_divergent'",
        ).all<{ payload: string }>();
        expect(results.length).toBeGreaterThan(0);
        const payload = JSON.parse(results[0].payload) as {
            work_id: string;
            conflicts: Array<{ at: string; keys: string[]; sources: string[] }>;
        };
        expect(payload.work_id).toBe(work);
        expect(payload.conflicts.some((c) => c.at === 'n:5' && c.keys.includes('e|105')))
            .toBe(true);
    });

    it('an episodeless feed still mints the binding (chapterless path)', async () => {
        const work = 'mk:imdb:tt900006';
        const res = await canonize({
            work_id: work,
            feeds: [{
                source_key: 'aniyomi.en.kage',
                provider_ref: 'kryiqaEgBJ',
                verified: true,
                provenance: 'user_picked',
                episodes: [],
            }],
        });
        expect(res.status).toBe(200);
        const body = await res.json() as any;
        expect(body.count).toBe(0);
        const row = await env.COMMENTS_DB.prepare(
            `SELECT external_id, provenance, confidence FROM external_ids
             WHERE provider = 'aniyomi.en.kage' AND miko_work_id = ?`,
        ).bind(work).first<{ external_id: string; provenance: string; confidence: number }>();
        expect(row?.external_id).toBe('kryiqaEgBJ');
        expect(row?.provenance).toBe('user_picked');
        expect(row?.confidence).toBe(0.9);
        const list = await (await getEpisodes(work)).json() as any;
        expect(list.episodes).toEqual([]);
    });

    it('rejects an oversize feed', async () => {
        const work = 'mk:imdb:tt900007';
        const res = await canonize({
            work_id: work,
            feeds: [{
                source_key: 'a',
                episodes: Array.from({ length: 5001 }, (_, i) => ({ name: `E${i}` })),
            }],
        });
        expect(res.status).toBe(400);
    });

    it('returns an empty list for an uncanonized work', async () => {
        const list = await (await getEpisodes('mk:imdb:tt-never')).json() as any;
        expect(list.episodes).toEqual([]);
    });

    it('recanonize replaces the sequence and keeps unclaimed sources', async () => {
        const work = 'mk:imdb:tt900008';
        await canonize({
            work_id: work,
            feeds: [
                { source_key: 'mikodb:map', verified: true, episodes: artifactEps(2) },
                { source_key: 'kage', episodes: carrierEps(2) },
            ],
        });
        // A later POST with only a third source preserves the first two.
        await canonize({
            work_id: work,
            feeds: [{ source_key: 'pahe', episodes: carrierEps(2) }],
        });
        const list = await (await getEpisodes(work)).json() as any;
        expect(list.episodes).toHaveLength(2);
        expect(list.episodes[0].sources).toEqual(
            expect.arrayContaining(['mikodb:map', 'kage', 'pahe']),
        );
        expect(list.episodes[0].confidence).toBe('verified');
    });
});
