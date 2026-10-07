import { env, SELF } from 'cloudflare:test';
import { describe, expect, it } from 'vitest';

// Phase 21-01 goldens — work_volumes covers/meta, work_volume_chapters
// consensus boundaries, volume_seed_queue tail-feed, and the
// trusted-minter seed lane.

const WORK = 'mk:mb:270'; // Naruto-class test id — no remote fetch needed.

let seq = 0;
async function newDevice(): Promise<{ hash: string; token: string }> {
    const hash = (++seq).toString(16).padStart(64, 'f'); // pure-hex
    const res = await SELF.fetch('http://test/v1/device/register', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ device_hash: hash }),
    });
    expect(res.status).toBe(200);
    const body = await res.json<{ token: string }>();
    return { hash, token: body.token };
}

async function trustedDevice(): Promise<{ hash: string; token: string }> {
    const d = await newDevice();
    await env.COMMENTS_DB.prepare(
        'INSERT INTO trusted_minters (device_hash, added_at) VALUES (?, ?)',
    ).bind(d.hash, Date.now()).run();
    return d;
}

async function ensureWork(id: string): Promise<void> {
    await env.COMMENTS_DB.prepare(
        `INSERT OR IGNORE INTO works_cache
         (miko_work_id, type, title, normalized_title, medium, fetched_at)
         VALUES (?, 'manga', 'NARUTO', 'naruto', 'manga', ?)`,
    ).bind(id, Date.now()).run();
}

function getVolumes(id: string, token?: string): Promise<Response> {
    return SELF.fetch(`http://test/v1/works/${id}/volumes`, {
        headers: token ? { authorization: `Bearer ${token}` } : {},
    });
}

function seed(id: string, body: unknown, token: string): Promise<Response> {
    return SELF.fetch(`http://test/v1/works/${id}/volumes/seed`, {
        method: 'POST',
        headers: {
            'content-type': 'application/json',
            authorization: `Bearer ${token}`,
        },
        body: JSON.stringify(body),
    });
}

describe('work volumes', () => {
    it('404s unknown work ids', async () => {
        const res = await getVolumes('mk:mb:99999999');
        expect(res.status).toBe(404);
    });

    it('returns empty shape for unmapped works and queues Bearer reads', async () => {
        await ensureWork(WORK);
        const d = await newDevice();
        const res = await getVolumes(WORK, d.token);
        expect(res.status).toBe(200);
        const body = await res.json<Record<string, unknown>>();
        expect(body.volumes).toEqual([]);
        expect(body.boundaries).toEqual({});
        expect(body.boundary_confidence).toBeNull();
        expect(body.queued_for_seed).toBe(true);

        const q = await env.COMMENTS_DB.prepare(
            'SELECT work_id FROM volume_seed_queue WHERE work_id = ?',
        ).bind(WORK).first();
        expect(q).toBeTruthy();

        // Second read is idempotent — one queue row.
        await getVolumes(WORK, d.token);
        const n = await env.COMMENTS_DB.prepare(
            'SELECT COUNT(*) AS c FROM volume_seed_queue WHERE work_id = ?',
        ).bind(WORK).first<{ c: number }>();
        expect(n?.c).toBe(1);
    });

    it('anonymous reads do not enqueue', async () => {
        await ensureWork('mk:mb:271');
        const res = await getVolumes('mk:mb:271');
        expect(res.status).toBe(200);
        const q = await env.COMMENTS_DB.prepare(
            'SELECT work_id FROM volume_seed_queue WHERE work_id = ?',
        ).bind('mk:mb:271').first();
        expect(q).toBeNull();
    });

    it('rejects seeds from non-trusted devices', async () => {
        await ensureWork(WORK);
        const d = await newDevice();
        const res = await seed(WORK, {
            volumes: [{ volume_no: 1, language: 'en', source: 'mangabaka', cover_url: 'x' }],
        }, d.token);
        expect(res.status).toBe(403);
    });

    it('trusted seed upserts covers + boundaries and dequeues the work', async () => {
        await ensureWork(WORK);
        const d = await trustedDevice();
        const res = await seed(WORK, {
            final_volume: 72,
            covers_count: 400,
            volumes: [
                { volume_no: 1, language: 'en', source: 'mangabaka', cover_url: 'https://img/1.jpg', isbn: '9781421544328' },
                { volume_no: 1, language: 'ja', source: 'mangabaka', cover_url: 'https://img/1ja.jpg' },
            ],
            boundaries: [
                { chapter: 1, volume: 1, confidence: 0.9, sources: ['comick', 'wiki'] },
                { chapter: 7, volume: 1, confidence: 1.0, sources: ['comick'] },
                { chapter: 8, volume: 2, confidence: 0.67, sources: ['comick', 'feed_markers'] },
            ],
        }, d.token);
        expect(res.status).toBe(200);
        const body = await res.json<Record<string, number>>();
        expect(body.volumes_upserted).toBe(2);
        expect(body.boundaries_upserted).toBe(3);

        const got = await (await getVolumes(WORK)).json<{
            volumes: Array<{ volume_no: number; language: string; isbn?: string }>;
            boundaries: Record<string, number>;
            boundary_confidence: number;
            final_volume: number;
            queued_for_seed: boolean;
        }>();
        expect(got.volumes).toHaveLength(2);
        expect(got.volumes.find((v) => v.language === 'en')?.isbn).toBe('9781421544328');
        expect(got.boundaries['8']).toBe(2);
        expect(got.boundary_confidence).toBeCloseTo((0.9 + 1.0 + 0.67) / 3, 5);
        expect(got.final_volume).toBe(72);
        expect(got.queued_for_seed).toBe(false);

        // Seeding dequeues the earlier lazy enqueue.
        const q = await env.COMMENTS_DB.prepare(
            'SELECT work_id FROM volume_seed_queue WHERE work_id = ?',
        ).bind(WORK).first();
        expect(q).toBeNull();
    });

    it('re-seed is an upsert — same payload is idempotent', async () => {
        await ensureWork('mk:mb:272');
        const d = await trustedDevice();
        const payload = {
            volumes: [{ volume_no: 3, language: 'en', source: 'mangabaka', cover_url: 'a' }],
            boundaries: [{ chapter: 20, volume: 3, confidence: 0.5, sources: ['feed_markers'] }],
        };
        expect((await seed('mk:mb:272', payload, d.token)).status).toBe(200);
        expect((await seed('mk:mb:272', payload, d.token)).status).toBe(200);
        const got = await (await getVolumes('mk:mb:272')).json<{
            volumes: unknown[];
            boundaries: Record<string, number>;
        }>();
        expect(got.volumes).toHaveLength(1);
        expect(Object.keys(got.boundaries)).toHaveLength(1);
    });

    it('re-seed never regresses confidence and unions provenance', async () => {
        await ensureWork('mk:mb:274');
        const d = await trustedDevice();
        await seed('mk:mb:274', {
            boundaries: [{ chapter: 5, volume: 1, confidence: 0.9, sources: ['comick', 'wiki'] }],
        }, d.token);
        // Weaker re-vote disagreeing on the volume — must lose.
        await seed('mk:mb:274', {
            boundaries: [{ chapter: 5, volume: 2, confidence: 0.4, sources: ['feed_markers'] }],
        }, d.token);
        const row = await env.COMMENTS_DB.prepare(
            'SELECT volume_no, confidence, sources FROM work_volume_chapters WHERE work_id = ? AND chapter = 5',
        ).bind('mk:mb:274').first<{ volume_no: number; confidence: number; sources: string }>();
        expect(row?.volume_no).toBe(1);
        expect(row?.confidence).toBe(0.9);
        expect(JSON.parse(row?.sources ?? '[]').sort()).toEqual(['comick', 'feed_markers', 'wiki']);

        // Equal-confidence re-vote may legitimately flip the winner.
        await seed('mk:mb:274', {
            boundaries: [{ chapter: 5, volume: 2, confidence: 0.9, sources: ['cubari'] }],
        }, d.token);
        const flipped = await env.COMMENTS_DB.prepare(
            'SELECT volume_no FROM work_volume_chapters WHERE work_id = ? AND chapter = 5',
        ).bind('mk:mb:274').first<{ volume_no: number }>();
        expect(flipped?.volume_no).toBe(2);
    });

    it('rejects malformed seeds', async () => {
        await ensureWork('mk:mb:273');
        const d = await trustedDevice();
        expect((await seed('mk:mb:273', {
            volumes: [{ volume_no: 1, language: 'en', source: 'evil-source' }],
        }, d.token)).status).toBe(400);
        expect((await seed('mk:mb:273', {
            boundaries: [{ chapter: 1, volume: 1, confidence: 1.5 }],
        }, d.token)).status).toBe(400);
        expect((await seed('mk:mb:273', {}, d.token)).status).toBe(400);
    });
});
