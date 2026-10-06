import { env, SELF } from 'cloudflare:test';
import { describe, expect, it } from 'vitest';

async function canonize(
    body: unknown,
    crawlerKey?: string,
    token?: string,
): Promise<Response> {
    return SELF.fetch('http://test/v1/chapters/canonize', {
        method: 'POST',
        headers: {
            'content-type': 'application/json',
            ...(crawlerKey ? { 'x-crawler-key': crawlerKey } : {}),
            ...(token ? { authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify(body),
    });
}

async function get(path: string, token?: string): Promise<Response> {
    return SELF.fetch(`http://test${path}`, {
        headers: token ? { authorization: `Bearer ${token}` } : {},
    });
}

/** Phase 20: an untrusted mint is provisional — visible only to its
 *  minter. Register a device and carry its Bearer on reads that expect
 *  the mint's content. */
async function registerDevice(): Promise<string> {
    const hash = crypto.randomUUID().replaceAll('-', '').padEnd(64, 'f').slice(0, 64);
    const res = await SELF.fetch('http://test/v1/device/register', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ device_hash: hash }),
    });
    return (await res.json<{ token: string }>()).token;
}

const SRC = 'eu.kanade.tachiyomi.extension.en.weebcentral';

describe('bindings + work_merges', () => {
    it('canonize provider_ref writes a binding row with provenance', async () => {
        const work = 'mk:mb:btest1';
        await canonize({
            work_id: work,
            feeds: [{
                source_key: SRC, provider_ref: '/series/01J/opm',
                score: 0.9, provenance: 'crawler',
                chapters: [{ name: 'Chapter 1' }],
            }],
        }, 'test-crawler-key');
        const res = await get(`/v1/works/${work}/bindings`);
        const body = await res.json() as any;
        expect(body.bindings).toHaveLength(1);
        expect(body.bindings[0]).toMatchObject({
            source_key: SRC, provider_ref: '/series/01J/opm',
            provenance: 'crawler', confidence: 0.9,
        });
        expect(body.bindings[0].last_seen_at).toBeGreaterThan(0);
    });

    it('recanonize refreshes last_seen_at, keeps the binding', async () => {
        const work = 'mk:mb:btest2';
        const post = () => canonize({
            work_id: work,
            feeds: [{
                source_key: SRC, provider_ref: '/series/x',
                chapters: [{ name: 'Chapter 1' }],
            }],
        });
        await post();
        await post();
        const { results } = await env.COMMENTS_DB.prepare(
            `SELECT last_seen_at, provenance FROM external_ids
             WHERE provider = ? AND external_id = ?`,
        ).bind(SRC, '/series/x').all<{ last_seen_at: number; provenance: string }>();
        expect(results).toHaveLength(1);
        expect(results[0].provenance).toBe('device_feed');
        expect(results[0].last_seen_at).toBeGreaterThan(0);
    });

    it('unauthenticated crawler provenance degrades to device_feed', async () => {
        const work = 'mk:mb:btest_crawlclaim';
        await canonize({
            work_id: work,
            feeds: [{
                source_key: SRC, provider_ref: '/series/fake-claim',
                provenance: 'crawler',
                chapters: [{ name: 'Chapter 1' }],
            }],
        }); // no key
        const { results } = await env.COMMENTS_DB.prepare(
            `SELECT provenance FROM external_ids
             WHERE provider = ? AND external_id = ?`,
        ).bind(SRC, '/series/fake-claim').all<{ provenance: string }>();
        expect(results[0].provenance).toBe('device_feed');
    });

    it('a provider_ref bound to another work flags review, never rebinds', async () => {
        await canonize({
            work_id: 'mk:mb:ownera',
            feeds: [{
                source_key: SRC, provider_ref: '/series/shared',
                chapters: [{ name: 'Chapter 1' }],
            }],
        });
        await canonize({
            work_id: 'mk:mb:ownerb',
            feeds: [{
                source_key: SRC, provider_ref: '/series/shared',
                chapters: [{ name: 'Chapter 1' }],
            }],
        });
        const { results } = await env.COMMENTS_DB.prepare(
            `SELECT miko_work_id FROM external_ids
             WHERE provider = ? AND external_id = ?`,
        ).bind(SRC, '/series/shared').all<{ miko_work_id: string }>();
        expect(results).toHaveLength(1);
        expect(results[0].miko_work_id).toBe('mk:mb:ownera');
        const { results: rev } = await env.COMMENTS_DB.prepare(
            `SELECT kind FROM identity_review WHERE kind = 'provider_ref_conflict'`,
        ).all();
        expect(rev.length).toBeGreaterThan(0);
    });

    it('bindings endpoint hides __none__ tombstones', async () => {
        const work = 'mk:mb:btest3';
        await env.COMMENTS_DB.prepare(
            `INSERT INTO external_ids (provider, external_id, miko_work_id)
             VALUES ('mangadex', '__none__', ?)`,
        ).bind(work).run();
        const body = await (await get(`/v1/works/${work}/bindings`)).json() as any;
        expect(body.bindings).toHaveLength(0);
    });

    it('merged works: chapters and bindings answer on the dead id', async () => {
        const live = 'mk:mb:live1';
        const dead = 'mk:mb:dead1';
        await env.COMMENTS_DB.prepare(
            'INSERT INTO work_merges (dead_id, live_id, merged_at) VALUES (?,?,?)',
        ).bind(dead, live, Date.now()).run();

        // Canonize against the DEAD id — feed lands on the live work.
        // Bearer'd so the provisional binding + pending feed serve the
        // minter (anonymous reads correctly see nothing yet).
        const token = await registerDevice();
        await canonize({
            work_id: dead,
            feeds: [{
                source_key: SRC, provider_ref: '/series/live',
                chapters: [{ name: 'Chapter 1' }, { name: 'Chapter 2' }],
            }],
        }, undefined, token);
        const listDead = await (await get(`/v1/works/${dead}/chapters`, token)).json() as any;
        const listLive = await (await get(`/v1/works/${live}/chapters`, token)).json() as any;
        expect(listDead.chapters).toHaveLength(2);
        expect(listLive.chapters).toHaveLength(2);
        expect(listDead.work_id).toBe(live);

        const binds = await (await get(`/v1/works/${dead}/bindings`, token)).json() as any;
        expect(binds.work_id).toBe(live);
        expect(binds.bindings[0].provider_ref).toBe('/series/live');
        expect(binds.bindings[0].status).toBe('provisional');
        expect(binds.bindings[0].mine).toBe(true);
    });
});
