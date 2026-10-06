import { env, SELF } from 'cloudflare:test';
import { describe, expect, it } from 'vitest';

// Phase 20 lifecycle goldens — the full provisional → confirmed arc is
// exercised end-to-end through SELF.fetch against real (local) D1.
// Storage is isolated per test; register fresh devices per test anyway
// so hashes read clearly in assertions.

const SRC = 'ext.test.alpha';

let seq = 0;
/** Register a fresh device, return {hash, token} for Bearer calls. */
async function newDevice(): Promise<{ hash: string; token: string }> {
    const hash = (++seq).toString(16).padStart(64, '0');
    const res = await SELF.fetch('http://test/v1/device/register', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ device_hash: hash }),
    });
    expect(res.status).toBe(200);
    const body = await res.json<{ token: string }>();
    return { hash, token: body.token };
}

function canonize(body: unknown, token?: string, crawlerKey?: string): Promise<Response> {
    return SELF.fetch('http://test/v1/chapters/canonize', {
        method: 'POST',
        headers: {
            'content-type': 'application/json',
            ...(token ? { authorization: `Bearer ${token}` } : {}),
            ...(crawlerKey ? { 'x-crawler-key': crawlerKey } : {}),
        },
        body: JSON.stringify(body),
    });
}

function getBindings(work: string, token?: string): Promise<Response> {
    return SELF.fetch(`http://test/v1/works/${encodeURIComponent(work)}/bindings`, {
        headers: token ? { authorization: `Bearer ${token}` } : {},
    });
}

function getChapters(work: string, token?: string): Promise<Response> {
    return SELF.fetch(`http://test/v1/works/${encodeURIComponent(work)}/chapters`, {
        headers: token ? { authorization: `Bearer ${token}` } : {},
    });
}

function canonBody(work: string, src: string, ref: string, names: string[] = ['Chapter 1']) {
    return {
        work_id: work,
        feeds: [{
            source_key: src,
            provider_ref: ref,
            chapters: names.map((name) => ({ name })),
        }],
    };
}

async function bindingRow(provider: string, ref: string) {
    return env.COMMENTS_DB.prepare(
        `SELECT status, minter_device_hash, points, confirmed_at
         FROM external_ids WHERE provider = ? AND external_id = ?`,
    ).bind(provider, ref).first<{
        status: string; minter_device_hash: string | null;
        points: number; confirmed_at: number | null;
    }>();
}

describe('binding lifecycle — mint → provisional → confirm → solidify', () => {
    it('the full tracer slice: mint hides, K devices solidify, everyone is served', async () => {
        const work = 'mk:mb:tracer1';
        const ref = '/series/tracer';
        const a = await newDevice();
        const b = await newDevice();
        const c = await newDevice();

        // Device A mints — provisional, self-seeded to 1 point.
        const mint = await canonize(canonBody(work, SRC, ref), a.token);
        expect(mint.status).toBe(200);
        const minted = await bindingRow(SRC, ref);
        expect(minted?.status).toBe('provisional');
        expect(minted?.minter_device_hash).toBe(a.hash);
        // points carry a fractional age bonus — compare within epsilon.
        expect(minted?.points).toBeCloseTo(1);
        expect(minted?.confirmed_at).toBeNull();

        // Minter sees it flagged; anonymous and other devices do not.
        const mine = await (await getBindings(work, a.token)).json() as any;
        expect(mine.bindings).toHaveLength(1);
        expect(mine.bindings[0]).toMatchObject({
            source_key: SRC, provider_ref: ref,
            status: 'provisional', mine: true,
        });
        expect(mine.bindings[0].points).toBeCloseTo(1);
        const anon = await (await getBindings(work)).json() as any;
        expect(anon.bindings).toHaveLength(0);
        const other = await (await getBindings(work, b.token)).json() as any;
        expect(other.bindings).toHaveLength(0);

        // Device B's canonize re-post adds a confirm — still short of K.
        await canonize(canonBody(work, SRC, ref), b.token);
        const at2 = await bindingRow(SRC, ref);
        expect(at2?.status).toBe('provisional');
        expect(at2?.points).toBeCloseTo(2);

        // Device C — third distinct device crosses the floor; with no
        // competitors the margin is trivially met → solidifies.
        const third = await canonize(canonBody(work, SRC, ref), c.token);
        const body3 = await third.json() as any;
        expect(body3.solidified).toContain(ref);
        const done = await bindingRow(SRC, ref);
        expect(done?.status).toBe('confirmed');
        expect(done?.confirmed_at).toBeGreaterThan(0);
        expect(done?.points).toBeCloseTo(3);

        // Now it serves everyone.
        const served = await (await getBindings(work)).json() as any;
        expect(served.bindings).toHaveLength(1);
        expect(served.bindings[0].status).toBe('confirmed');
    });

    it('a 2× margin must clear the next-best sibling binding', async () => {
        const work = 'mk:mb:margin1';
        // Competitor provisional binding at score 2 — its ledger
        // confirms are stamped ~now so no age bonus inflates the score.
        await env.COMMENTS_DB.prepare(
            `INSERT INTO external_ids
             (provider, external_id, miko_work_id, status, points)
             VALUES (?, '/series/competitor', ?, 'provisional', 2)`,
        ).bind(SRC, work).run();
        for (const d of ['aa', 'bb']) {
            await env.COMMENTS_DB.prepare(
                `INSERT OR IGNORE INTO binding_confirms
                 (provider, external_id, miko_work_id, device_hash, created_at)
                 VALUES (?,?,?,?,?)`,
            ).bind(SRC, '/series/competitor', work, d.repeat(32), Date.now()).run();
        }

        // Candidate reaches K=3 but 3 < 2×2 — stays provisional.
        for (const _ of [0, 1, 2]) {
            const d = await newDevice();
            await canonize(canonBody(work, SRC, '/series/challenger'), d.token);
        }
        const at3 = await bindingRow(SRC, '/series/challenger');
        expect(at3?.status).toBe('provisional');
        expect(at3?.points).toBeCloseTo(3);

        // Two more distinct devices → 5 > 2×2 → solidifies.
        for (const _ of [0, 1]) {
            const d = await newDevice();
            await canonize(canonBody(work, SRC, '/series/challenger'), d.token);
        }
        expect((await bindingRow(SRC, '/series/challenger'))?.status).toBe('confirmed');
    });

    it('tied leaders never solidify — a 3v3 pair stays provisional', async () => {
        const work = 'mk:mb:tied1';
        // ref2 already carries 3 seeded confirms (stamped ~now — no
        // age bonus, a true 3v3).
        await env.COMMENTS_DB.prepare(
            `INSERT INTO external_ids (provider, external_id, miko_work_id, status)
             VALUES (?, '/series/ref2', ?, 'provisional')`,
        ).bind(SRC, work).run();
        for (const d of ['aa', 'bb', 'cc']) {
            await env.COMMENTS_DB.prepare(
                `INSERT OR IGNORE INTO binding_confirms
                 (provider, external_id, miko_work_id, device_hash, created_at)
                 VALUES (?,?,?,?,?)`,
            ).bind(SRC, '/series/ref2', work, d.repeat(32), Date.now()).run();
        }
        // ref1 climbs to 3 — competitor is at 3, so 3 < 2×3 → provisional.
        for (const _ of [0, 1, 2]) {
            const d = await newDevice();
            await canonize(canonBody(work, SRC, '/series/ref1'), d.token);
        }
        const ref1 = await bindingRow(SRC, '/series/ref1');
        const ref2 = await bindingRow(SRC, '/series/ref2');
        expect(ref1?.status).toBe('provisional');
        expect(ref1?.points).toBeCloseTo(3);
        expect(ref2?.status).toBe('provisional');
    });

    it('repeat confirms from one device dedupe — only distinct devices count', async () => {
        const work = 'mk:mb:dedup1';
        const ref = '/series/dedup';
        const a = await newDevice();
        // The same device re-posts 5 times — the PK dedupes to ONE
        // confirm; the binding never approaches the 3-device floor.
        for (const _ of [0, 1, 2, 3, 4]) {
            await canonize(canonBody(work, SRC, ref), a.token);
        }
        const row = await bindingRow(SRC, ref);
        expect(row?.status).toBe('provisional');
        expect(row?.points).toBeCloseTo(1);
        const { n } = await env.COMMENTS_DB.prepare(
            `SELECT COUNT(*) AS n FROM binding_confirms
             WHERE provider = ? AND external_id = ?`,
        ).bind(SRC, ref).first<{ n: number }>() as { n: number };
        expect(n).toBe(1);
    });

    it('report weight ≥ quarantine threshold blocks solidify even at K confirms', async () => {
        const work = 'mk:mb:quar1';
        const ref = '/series/flagged';
        const a = await newDevice();
        await canonize(canonBody(work, SRC, ref), a.token);
        // Two heavy reports — cumulative weight 3.0 hits the cap.
        for (const d of ['rr', 'ss']) {
            await env.COMMENTS_DB.prepare(
                `INSERT OR IGNORE INTO binding_reports
                 (provider, external_id, miko_work_id, device_hash, weight, kind, created_at)
                 VALUES (?,?,?,?,1.5,'report',?)`,
            ).bind(SRC, ref, work, d.repeat(32), Date.now()).run();
        }
        // K=3 device confirms land — solidify must still refuse.
        for (const _ of [0, 1]) {
            const d = await newDevice();
            await canonize(canonBody(work, SRC, ref), d.token);
        }
        const row = await bindingRow(SRC, ref);
        expect(row?.status).toBe('provisional');
        // The report weight DID erode the margin score though.
        expect(row?.points).toBeLessThan(1);
    });

    it('a raced quarantine wins: the guarded UPDATE never revives the row', async () => {
        const work = 'mk:mb:race1';
        const ref = '/series/race';
        const { maybeSolidify } = await import('../src/trust');
        await env.COMMENTS_DB.prepare(
            `INSERT INTO external_ids (provider, external_id, miko_work_id, status)
             VALUES (?,?,?,'provisional')`,
        ).bind(SRC, ref, work).run();
        for (const d of ['aa', 'bb', 'cc']) {
            await env.COMMENTS_DB.prepare(
                `INSERT OR IGNORE INTO binding_confirms
                 (provider, external_id, miko_work_id, device_hash, created_at)
                 VALUES (?,?,?,?,?)`,
            ).bind(SRC, ref, work, d.repeat(32), Date.now()).run();
        }
        // Quarantine lands before the evaluate-side UPDATE.
        await env.COMMENTS_DB.prepare(
            `UPDATE external_ids SET status = 'quarantined', quarantined_at = ?
             WHERE provider = ? AND external_id = ?`,
        ).bind(Date.now(), SRC, ref).run();
        expect(await maybeSolidify(env, SRC, ref, work)).toBe(false);
        expect((await bindingRow(SRC, ref))?.status).toBe('quarantined');
    });

    it('user_picked confidence does not bypass the lifecycle', async () => {
        const work = 'mk:mb:up1';
        const ref = '/manga/picked';
        const a = await newDevice();
        await canonize({
            work_id: work,
            feeds: [{
                source_key: SRC, provider_ref: ref,
                verified: true, score: 0.6, provenance: 'user_picked',
                chapters: [{ name: 'Chapter 1' }],
            }],
        }, a.token);
        const row = await bindingRow(SRC, ref);
        expect(row?.status).toBe('provisional');
        const full = await env.COMMENTS_DB.prepare(
            'SELECT confidence FROM external_ids WHERE provider = ? AND external_id = ?',
        ).bind(SRC, ref).first<{ confidence: number }>();
        expect(full?.confidence).toBe(0.9);
    });

    it('trusted minters mint confirmed directly', async () => {
        const work = 'mk:mb:trusted1';
        const ref = '/series/trusted';
        const d = await newDevice();
        await env.COMMENTS_DB.prepare(
            'INSERT INTO trusted_minters (device_hash, added_at) VALUES (?,?)',
        ).bind(d.hash, Date.now()).run();
        await canonize(canonBody(work, SRC, ref), d.token);
        const row = await bindingRow(SRC, ref);
        expect(row?.status).toBe('confirmed');
        expect(row?.confirmed_at).toBeGreaterThan(0);
        // Confirmed from the first mint — no lifecycle gate, feed is shared.
        const anon = await (await getBindings(work)).json() as any;
        expect(anon.bindings[0]?.status).toBe('confirmed');
        const chapters = await (await getChapters(work)).json() as any;
        expect(chapters.chapters).toHaveLength(1);
    });

    it('an unauthenticated mint is provisional+NULL — visible to no one, confirmable blind', async () => {
        const work = 'mk:mb:nullminter1';
        const ref = '/series/anon';
        await canonize(canonBody(work, SRC, ref)); // no Bearer
        const row = await bindingRow(SRC, ref);
        expect(row?.status).toBe('provisional');
        expect(row?.minter_device_hash).toBeNull();
        expect(row?.points).toBe(0); // no self-seed without a device identity

        // Invisible to anonymous and to any Bearer.
        expect(((await (await getBindings(work)).json() as any).bindings)).toHaveLength(0);
        const d = await newDevice();
        expect(((await (await getBindings(work, d.token)).json() as any).bindings))
            .toHaveLength(0);

        // Identified devices still confirm it blind — K re-posts solidify.
        for (const _ of [0, 1, 2]) {
            const dd = await newDevice();
            await canonize(canonBody(work, SRC, ref), dd.token);
        }
        expect((await bindingRow(SRC, ref))?.status).toBe('confirmed');
    });

    it('a grandfathered incumbent blocks a K-point challenger via the margin', async () => {
        const work = 'mk:mb:grand1';
        // Emulated pre-0015 row — applyD1Migrations runs 0015's seed
        // UPDATE on an empty table, so the incumbency stamp is set
        // explicitly: bare INSERT rides DEFAULT 'confirmed' +
        // confirmed_at NULL, then the stamp.
        await env.COMMENTS_DB.prepare(
            `INSERT INTO external_ids (provider, external_id, miko_work_id)
             VALUES (?, '/series/incumbent', ?)`,
        ).bind(SRC, work).run();
        await env.COMMENTS_DB.prepare(
            `UPDATE external_ids SET points = 3.0
             WHERE provider = ? AND external_id = ?`,
        ).bind(SRC, '/series/incumbent').run();

        // Grandfathered rows serve everyone.
        const anon = await (await getBindings(work)).json() as any;
        expect(anon.bindings[0]?.status).toBe('confirmed');
        expect(anon.bindings[0]?.points).toBe(3);

        // A challenger at K=3 does NOT solidify against it (3 < 2×3).
        for (const _ of [0, 1, 2]) {
            const d = await newDevice();
            await canonize(canonBody(work, SRC, '/series/challenger'), d.token);
        }
        const challenger = await bindingRow(SRC, '/series/challenger');
        expect(challenger?.status).toBe('provisional');
        expect(challenger?.points).toBeCloseTo(3);
    });

    it('authed crawler provenance mints confirmed and shares the feed', async () => {
        const work = 'mk:mb:crawler1';
        const ref = '/series/crawled';
        await canonize({
            work_id: work,
            feeds: [{
                source_key: SRC, provider_ref: ref, provenance: 'crawler',
                chapters: [{ name: 'Chapter 1' }],
            }],
        }, undefined, 'test-crawler-key');
        const row = await bindingRow(SRC, ref);
        expect(row?.status).toBe('confirmed');
        expect(row?.minter_device_hash).toBeNull();
        const feed = await env.COMMENTS_DB.prepare(
            'SELECT pending FROM canonical_feeds WHERE work_id = ? AND source_key = ?',
        ).bind(work, SRC).first<{ pending: number }>();
        expect(feed?.pending).toBe(0);
        const anon = await (await getBindings(work)).json() as any;
        expect(anon.bindings[0]?.status).toBe('confirmed');
    });

    it('bindings order confirmed > provisional > quarantined, then points DESC, external_id ASC', async () => {
        const work = 'mk:mb:order1';
        const d = await newDevice();
        const rows: Array<[string, string, string, number]> = [
            // [provider, ref, status, points]
            ['p.confirmed', 'ref-b', 'confirmed', 2],
            ['p.confirmed', 'ref-a', 'confirmed', 5],
            ['p.prov', 'ref-m', 'provisional', 9],
            ['p.quar', 'ref-z', 'quarantined', 99],
        ];
        for (const [provider, ref, status, points] of rows) {
            await env.COMMENTS_DB.prepare(
                `INSERT INTO external_ids
                 (provider, external_id, miko_work_id, status, points, minter_device_hash)
                 VALUES (?,?,?,?,?,?)`,
            ).bind(provider, ref, work, status, points, d.hash).run();
        }
        const body = await (await getBindings(work, d.token)).json() as any;
        expect(body.bindings.map((x: any) => [x.provider_ref, x.status])).toEqual([
            ['ref-a', 'confirmed'],
            ['ref-b', 'confirmed'],
            ['ref-m', 'provisional'],
            ['ref-z', 'quarantined'],
        ]);
        // The provisional row is mine — the quarantined row is flagged
        // to everyone but the provisional one hides from strangers.
        const other = await newDevice();
        const theirs = await (await getBindings(work, other.token)).json() as any;
        expect(theirs.bindings.map((x: any) => x.provider_ref))
            .toEqual(['ref-a', 'ref-b', 'ref-z']);
    });

    it('a provisional feed stays out of shared canon; the minter sees the overlay; solidify publishes', async () => {
        const work = 'mk:mb:feedvis1';
        const ref = '/series/feed';
        const a = await newDevice();
        const b = await newDevice();
        const c = await newDevice();

        await canonize(canonBody(work, SRC, ref, ['Chapter 1', 'Chapter 2']), a.token);
        const feed = await env.COMMENTS_DB.prepare(
            'SELECT pending, submitter_device_hash FROM canonical_feeds WHERE work_id = ? AND source_key = ?',
        ).bind(work, SRC).first<{ pending: number; submitter_device_hash: string }>();
        expect(feed?.pending).toBe(1);
        expect(feed?.submitter_device_hash).toBe(a.hash);

        // Anonymous reads the materialized table — empty.
        expect(((await (await getChapters(work)).json() as any).chapters)).toHaveLength(0);
        // A stranger sees nothing either.
        expect(((await (await getChapters(work, b.token)).json() as any).chapters)).toHaveLength(0);
        // The minter gets the overlay merge.
        const mine = await (await getChapters(work, a.token)).json() as any;
        expect(mine.chapters).toHaveLength(2);
        expect(mine.chapters[0].confidence).toBe('single');

        // Two more devices confirm → solidify → the SAME request's
        // remerge publishes the feed for everyone.
        await canonize(canonBody(work, SRC, ref, ['Chapter 1', 'Chapter 2']), b.token);
        const res = await canonize(canonBody(work, SRC, ref, ['Chapter 1', 'Chapter 2']), c.token);
        expect((await res.json() as any).solidified).toContain(ref);
        const all = await (await getChapters(work)).json() as any;
        expect(all.chapters).toHaveLength(2);
        const after = await env.COMMENTS_DB.prepare(
            'SELECT pending FROM canonical_feeds WHERE work_id = ? AND source_key = ?',
        ).bind(work, SRC).first<{ pending: number }>();
        expect(after?.pending).toBe(0);
    });

    it('resolve by external id is gated: anonymous misses, the minter hits, quarantine hits nobody', async () => {
        const work = 'mk:mb:resgate1';
        const decoy = 'mk:mb:resdecoy';
        await env.COMMENTS_DB.prepare(
            `INSERT INTO works_cache
             (miko_work_id, type, title, normalized_title, provider_links, medium, fetched_at)
             VALUES (?, 'manga', 'Gated Work', 'gated work', '[]', 'manga', 0)`,
        ).bind(work).run();
        // A decoy work with its own title — fall-through title matching
        // lands HERE, so the gated join's miss is observable without an
        // upstream fetch.
        await env.COMMENTS_DB.prepare(
            `INSERT INTO works_cache
             (miko_work_id, type, title, normalized_title, provider_links, medium, fetched_at)
             VALUES (?, 'manga', 'Decoy Title', 'decoy title', '[]', 'manga', 0)`,
        ).bind(decoy).run();
        await env.COMMENTS_DB.prepare(
            `INSERT INTO works_titles (miko_work_id, title, normalized)
             VALUES (?, 'Decoy Title', 'decoy title')`,
        ).bind(decoy).run();
        const a = await newDevice();
        const b = await newDevice();
        await canonize(canonBody(work, SRC, '/series/gated'), a.token);

        const post = (token?: string) => SELF.fetch('http://test/v1/works/resolve', {
            method: 'POST',
            headers: {
                'content-type': 'application/json',
                ...(token ? { authorization: `Bearer ${token}` } : {}),
            },
            body: JSON.stringify({
                provider: SRC, external_id: '/series/gated',
                title: 'decoy title', medium: 'manga',
            }),
        });

        // Anonymous and other devices: the provisional row does not
        // vouch — resolution falls through to the title path's decoy.
        expect(((await (await post()).json()) as any).work?.miko_work_id).toBe(decoy);
        expect(((await (await post(b.token)).json()) as any).work?.miko_work_id).toBe(decoy);
        // The minter resolves their own provisional binding — the
        // external-id hit takes precedence over the title match.
        const mine = await (await post(a.token)).json() as any;
        expect(mine.work?.miko_work_id).toBe(work);
        expect(mine.work.external_ids[SRC]).toBe('/series/gated');

        // Quarantine the binding — even the minter falls through.
        await env.COMMENTS_DB.prepare(
            `UPDATE external_ids SET status = 'quarantined', quarantined_at = ?
             WHERE provider = ? AND external_id = ?`,
        ).bind(Date.now(), SRC, '/series/gated').run();
        expect(((await (await post(a.token)).json()) as any).work?.miko_work_id).toBe(decoy);
    });

    it('external_ids in the resolve DTO filter by the serve predicate', async () => {
        const work = 'mk:mb:resgate2';
        await env.COMMENTS_DB.prepare(
            `INSERT INTO works_cache
             (miko_work_id, type, title, normalized_title, provider_links, medium, fetched_at)
             VALUES (?, 'manga', 'Mixed Work', 'mixed work', '[]', 'manga', 0)`,
        ).bind(work).run();
        const a = await newDevice();
        // One confirmed binding, one provisional (minted by a).
        await env.COMMENTS_DB.prepare(
            `INSERT INTO external_ids (provider, external_id, miko_work_id)
             VALUES ('mangadex', 'md-1', ?)`,
        ).bind(work).run();
        await canonize(canonBody(work, SRC, '/series/mine'), a.token);

        const post = (token?: string) => SELF.fetch('http://test/v1/works/resolve', {
            method: 'POST',
            headers: {
                'content-type': 'application/json',
                ...(token ? { authorization: `Bearer ${token}` } : {}),
            },
            body: JSON.stringify({
                provider: 'mangadex', external_id: 'md-1',
                title: 'x', medium: 'manga',
            }),
        });
        const anon = await (await post()).json() as any;
        expect(anon.work.miko_work_id).toBe(work);
        expect(Object.keys(anon.work.external_ids)).toEqual(['mangadex']);
        const mine = await (await post(a.token)).json() as any;
        expect(mine.work.external_ids.mangadex).toBe('md-1');
        expect(mine.work.external_ids[SRC]).toBe('/series/mine');
    });

    it('episodes path mirrors chapters: pending feed overlays only for the minter', async () => {
        const work = 'mk:imdb:ttep1';
        const ref = '/show/ep';
        const a = await newDevice();
        const canonizeEps = (token?: string) => SELF.fetch('http://test/v1/episodes/canonize', {
            method: 'POST',
            headers: {
                'content-type': 'application/json',
                ...(token ? { authorization: `Bearer ${token}` } : {}),
            },
            body: JSON.stringify({
                work_id: work,
                feeds: [{
                    source_key: SRC, provider_ref: ref,
                    episodes: [{ name: 'Episode 1', kind: 'episode', number: 1 }],
                }],
            }),
        });
        const getEps = (token?: string) => SELF.fetch(
            `http://test/v1/works/${encodeURIComponent(work)}/episodes`,
            { headers: token ? { authorization: `Bearer ${token}` } : {} },
        );

        await canonizeEps(a.token);
        expect(((await (await getEps()).json()) as any).episodes).toHaveLength(0);
        expect(((await (await getEps(a.token)).json()) as any).episodes).toHaveLength(1);

        // K devices → solidify → episodes published for everyone.
        for (const _ of [0, 1]) {
            const d = await newDevice();
            await canonizeEps(d.token);
        }
        const all = await (await getEps()).json() as any;
        expect(all.episodes).toHaveLength(1);
    });

    it('a second device\'s re-post keeps the feed visible to the original minter', async () => {
        const work = 'mk:mb:feedvis2';
        const ref = '/series/repost';
        const a = await newDevice();
        const b = await newDevice();

        await canonize(canonBody(work, SRC, ref), a.token);
        // B re-posts the same source_key — REPLACE moves the feed's
        // submitter to B, but the binding minter clause keeps it
        // visible to A (the provisional binding still names A).
        await canonize(canonBody(work, SRC, ref), b.token);
        const feed = await env.COMMENTS_DB.prepare(
            'SELECT pending, submitter_device_hash FROM canonical_feeds WHERE work_id = ? AND source_key = ?',
        ).bind(work, SRC).first<{ pending: number; submitter_device_hash: string }>();
        expect(feed?.pending).toBe(1);
        expect(feed?.submitter_device_hash).toBe(b.hash);

        const mineA = await (await getChapters(work, a.token)).json() as any;
        expect(mineA.chapters).toHaveLength(1);
        const mineB = await (await getChapters(work, b.token)).json() as any;
        expect(mineB.chapters).toHaveLength(1);
        const anon = await (await getChapters(work)).json() as any;
        expect(anon.chapters).toHaveLength(0);
    });
});

describe('mint telemetry — provider_stats observational counters', () => {
    const stat = async (pkg: string) =>
        env.COMMENTS_DB.prepare(
            'SELECT served_count, failed_count FROM provider_stats WHERE provider = ?',
        ).bind(pkg).first<{ served_count: number; failed_count: number }>();

    it('canonize telemetry upserts served/failed counts; repeats accumulate', async () => {
        const work = 'mk:mb:tel1';
        const tel = [{ pkg: SRC, served: 12, failed: 3 }];
        const res = await canonize({ ...canonBody(work, SRC, '/series/tel'), telemetry: tel });
        expect(res.status).toBe(200);
        expect(await stat(SRC)).toEqual({ served_count: 12, failed_count: 3 });
        await canonize({ ...canonBody(work, SRC, '/series/tel'), telemetry: tel });
        expect(await stat(SRC)).toEqual({ served_count: 24, failed_count: 6 });
    });

    it('resolve and ids/seen ingest telemetry too', async () => {
        const seen = await SELF.fetch('http://test/v1/ids/seen', {
            method: 'POST',
            headers: { 'content-type': 'application/json' },
            body: JSON.stringify({
                id: 'mk:local:t1', medium: 'manga',
                telemetry: [{ pkg: 'ext.tel.two', served: 5 }],
            }),
        });
        expect(seen.status).toBe(200);
        expect(await stat('ext.tel.two')).toEqual({ served_count: 5, failed_count: 0 });

        const resolve = await SELF.fetch('http://test/v1/works/resolve', {
            method: 'POST',
            headers: { 'content-type': 'application/json' },
            body: JSON.stringify({
                provider: 'mangadex', external_id: 'tel-md',
                title: 'x', medium: 'manga',
                telemetry: [{ pkg: 'ext.tel.two', failed: 2 }],
            }),
        });
        expect(resolve.status).toBe(200);
        expect(await stat('ext.tel.two')).toEqual({ served_count: 5, failed_count: 2 });
    });

    it('malformed entries clamp out; over-cap batches are dropped whole; requests never fail', async () => {
        const work = 'mk:mb:tel2';
        const res = await canonize({
            ...canonBody(work, SRC, '/series/tel2'),
            telemetry: [
                { pkg: 'ext.tel.ok', served: 1 },
                { served: 9 },                       // no pkg
                { pkg: 'ext.tel.neg', served: -1 },  // negative
                { pkg: 'ext.tel.zero' },             // nothing to count
                'junk',
                { pkg: 'ext.tel.big', failed: 200000 }, // > max_count
            ],
        });
        expect(res.status).toBe(200);
        expect(await stat('ext.tel.ok')).toEqual({ served_count: 1, failed_count: 0 });
        expect(await stat('ext.tel.neg')).toBeNull();
        expect(await stat('ext.tel.big')).toBeNull();

        // 51 entries > telemetry.max_entries (50) → whole batch ignored,
        // the carrier request still succeeds.
        const res2 = await canonize({
            ...canonBody(work, SRC, '/series/tel2'),
            telemetry: Array.from({ length: 51 }, (_, i) => ({
                pkg: `ext.tel.cap${i}`, served: 1,
            })),
        });
        expect(res2.status).toBe(200);
        expect(await stat('ext.tel.cap0')).toBeNull();
    });

    it('telemetry never touches lifecycle math', async () => {
        const work = 'mk:mb:tel3';
        const a = await newDevice();
        await canonize({
            ...canonBody(work, SRC, '/series/tel3'),
            telemetry: [{ pkg: SRC, served: 9999 }],
        }, a.token);
        const row = await bindingRow(SRC, '/series/tel3');
        expect(row?.status).toBe('provisional');
        expect(row?.points).toBeCloseTo(1);
        // Telemetry columns accumulate; the ledger-derived columns now
        // reflect the mint itself (20-04) — minted_count counts the
        // attributed row, but telemetry cannot solidify or promote.
        const stats = await env.COMMENTS_DB.prepare(
            `SELECT minted_count, solidified_count, served_count, dynamic_tier
             FROM provider_stats WHERE provider = ?`,
        ).bind(SRC).first<{
            minted_count: number; solidified_count: number;
            served_count: number; dynamic_tier: string | null;
        }>();
        expect(stats?.served_count).toBe(9999);
        expect(stats?.minted_count).toBe(1);
        expect(stats?.solidified_count).toBe(0);
        expect(stats?.dynamic_tier).toBeNull();
    });
});
