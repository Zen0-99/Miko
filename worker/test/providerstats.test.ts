import { env, SELF } from 'cloudflare:test';
import { describe, expect, it } from 'vitest';
import { evaluateTier, recomputeProviderStats } from '../src/providerstats';
import { RULESET } from '../src/ruleset';

// Phase 20-04 goldens — the pure evaluateTier dual-floor ladder, the
// ledger-derived recomputeProviderStats (minted/solidified filter on
// minter_device_hash; quarantined/report_weight full-table; telemetry
// + tier_cap preserved), the unbind/mint/report/solidify call-sites,
// and the keyed GET /v1/providers/tiers map. Ruleset under test:
// min_minted=20, acceptance_floor_pct=80, ladder {C:10, B:50, A:200}.

const SRC = 'ext.test.stats';
const HOUR_MS = 3_600_000;

let seq = 0;
/** Register a fresh device, return {hash, token} for Bearer calls. */
async function newDevice(): Promise<{ hash: string; token: string }> {
    const hash = `9${(++seq).toString(16).padStart(63, '0')}`;
    const res = await SELF.fetch('http://test/v1/device/register', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ device_hash: hash }),
    });
    expect(res.status).toBe(200);
    const body = await res.json<{ token: string }>();
    return { hash, token: body.token };
}

function canonize(body: unknown, token?: string): Promise<Response> {
    return SELF.fetch('http://test/v1/chapters/canonize', {
        method: 'POST',
        headers: {
            'content-type': 'application/json',
            ...(token ? { authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify(body),
    });
}

function unbind(body: unknown, token?: string): Promise<Response> {
    return SELF.fetch('http://test/v1/bindings/unbind', {
        method: 'POST',
        headers: {
            'content-type': 'application/json',
            ...(token ? { authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify(body),
    });
}

function canonBody(work: string, src: string, ref: string, names: string[] = ['Chapter 1']) {
    return {
        work_id: work,
        feeds: [{ source_key: src, provider_ref: ref, chapters: names.map((name) => ({ name })) }],
    };
}

const repBody = (work: string, provider: string, ref: string) => ({
    work_id: work, provider, provider_ref: ref,
});

/** Direct external_ids seed — status and minter varied per row. */
async function seedBinding(
    provider: string,
    ref: string,
    work: string,
    status: string | null,
    minter: string | null,
): Promise<void> {
    await env.COMMENTS_DB.prepare(
        `INSERT INTO external_ids
         (provider, external_id, miko_work_id, status, minter_device_hash, confirmed_at)
         VALUES (?,?,?,?,?,?)`,
    ).bind(provider, ref, work, status, minter, status === 'confirmed' ? Date.now() : null).run();
}

async function statsRow(provider: string) {
    return env.COMMENTS_DB.prepare(
        'SELECT * FROM provider_stats WHERE provider = ?',
    ).bind(provider).first<{
        minted_count: number; solidified_count: number; quarantined_count: number;
        confirm_count: number; report_weight: number; served_count: number;
        failed_count: number; dynamic_tier: string | null; tier_cap: string | null;
    }>();
}

describe('evaluateTier — dual-floor promotion ladder', () => {
    // [solidified, minted, expected] — ladder {C:10, B:50, A:200},
    // min_minted=20, acceptance ≥ 80% (integer-scaled, inclusive).
    const cases: Array<[number, number, string | null]> = [
        [200, 240, 'A'],   // A floor + both global floors (≈83%)
        [50, 60, 'B'],     // B floor + floors (≈83%)
        [60, 30, 'B'],     // sparse but consistent: every mint solidified
        [10, 20, null],    // C floor met but acceptance 50% — global gate
        [200, 400, null],  // A volume, 50% acceptance — volume can't buy it
        [200, 10, null],   // minted floor fails at every rung
        [80, 100, 'B'],    // exactly 80% acceptance promotes (inclusive)
        [79, 100, null],   // one short — 7900 < 8000 integer-scaled
        [16, 20, 'C'],     // exactly the minted floor + exactly 80%
        [15, 20, null],    // 75% acceptance under the floor
        [9, 20, null],     // under the bottom rung even at any rate
        [0, 0, null],      // empty provider
    ];
    for (const [solidified, minted, want] of cases) {
        it(`solidified=${solidified} minted=${minted} → ${want ?? 'null'}`, () => {
            expect(evaluateTier({ minted_count: minted, solidified_count: solidified }))
                .toBe(want);
        });
    }

    it('the ladder never emits S no matter the counts', () => {
        expect(evaluateTier({ minted_count: 100_000, solidified_count: 100_000 })).toBe('A');
    });
});

describe('recomputeProviderStats — ledger-derived columns', () => {
    it('counts only device-attributed rows toward minted/solidified', async () => {
        const p = 'ext.test.attribution';
        // 30 catalog/seed mints — NULL minter → zero tier credit.
        await env.COMMENTS_DB.batch(
            Array.from({ length: 30 }, (_, i) =>
                env.COMMENTS_DB.prepare(
                    `INSERT INTO external_ids
                     (provider, external_id, miko_work_id, status, minter_device_hash)
                     VALUES (?,?,?, 'confirmed', NULL)`,
                ).bind(p, `/seed/${i}`, `mk:w:seed${i}`)),
        );
        // 3 attributed mints, 1 confirmed.
        await seedBinding(p, '/a/1', 'mk:w:a1', 'confirmed', 'a'.repeat(64));
        await seedBinding(p, '/a/2', 'mk:w:a2', 'provisional', 'b'.repeat(64));
        await seedBinding(p, '/a/3', 'mk:w:a3', 'quarantined', 'c'.repeat(64));

        await recomputeProviderStats(env, p);
        const row = await statsRow(p);
        expect(row?.minted_count).toBe(3);
        expect(row?.solidified_count).toBe(1);
        // quarantined_count is the catalog-wide report, not attributed.
        expect(row?.quarantined_count).toBe(1);
        expect(row?.dynamic_tier).toBeNull();
    });

    it('derives the C tier when both floors clear', async () => {
        const p = 'ext.test.ctier';
        // 16 confirmed of 20 attributed mints — exactly 80% acceptance.
        await env.COMMENTS_DB.batch(
            Array.from({ length: 20 }, (_, i) =>
                env.COMMENTS_DB.prepare(
                    `INSERT INTO external_ids
                     (provider, external_id, miko_work_id, status, minter_device_hash)
                     VALUES (?,?,?,?,?)`,
                ).bind(
                    p, `/c/${i}`, `mk:w:c${i}`,
                    i < 16 ? 'confirmed' : 'provisional',
                    'a'.repeat(64),
                )),
        );
        await recomputeProviderStats(env, p);
        const row = await statsRow(p);
        expect(row?.minted_count).toBe(20);
        expect(row?.solidified_count).toBe(16);
        expect(row?.dynamic_tier).toBe('C');
    });

    it('sums confirms and report weight; unbind rows never count', async () => {
        const p = 'ext.test.weight';
        await seedBinding(p, '/r/1', 'mk:w:r1', 'confirmed', 'a'.repeat(64));
        await env.COMMENTS_DB.batch([
            // 2 confirm rows across devices.
            env.COMMENTS_DB.prepare(
                `INSERT INTO binding_confirms (provider, external_id, miko_work_id, device_hash, created_at)
                 VALUES (?,?,?,?,?)`,
            ).bind(p, '/r/1', 'mk:w:r1', 'a'.repeat(64), Date.now()),
            env.COMMENTS_DB.prepare(
                `INSERT INTO binding_confirms (provider, external_id, miko_work_id, device_hash, created_at)
                 VALUES (?,?,?,?,?)`,
            ).bind(p, '/r/1', 'mk:w:r1', 'b'.repeat(64), Date.now()),
            // A real report + a self-revoke audit row that (artificially)
            // carries weight — the kind filter must still exclude it.
            env.COMMENTS_DB.prepare(
                `INSERT INTO binding_reports
                 (provider, external_id, miko_work_id, device_hash, weight, kind, created_at)
                 VALUES (?,?,?,?,2.0,'report',?)`,
            ).bind(p, '/r/1', 'mk:w:r1', 'c'.repeat(64), Date.now()),
            env.COMMENTS_DB.prepare(
                `INSERT INTO binding_reports
                 (provider, external_id, miko_work_id, device_hash, weight, kind, created_at)
                 VALUES (?,?,?,?,5.0,'unbind',?)`,
            ).bind(p, '/r/1', 'mk:w:r1', 'a'.repeat(64), Date.now()),
        ]);
        await recomputeProviderStats(env, p);
        const row = await statsRow(p);
        expect(row?.confirm_count).toBe(2);
        expect(row?.report_weight).toBe(2);
    });

    it('preserves tier_cap and the telemetry counters across recompute', async () => {
        const p = 'ext.test.capped';
        await env.COMMENTS_DB.prepare(
            `INSERT INTO provider_stats
             (provider, served_count, failed_count, tier_cap, updated_at)
             VALUES (?, 7, 3, 'C', ?)`,
        ).bind(p, Date.now()).run();
        await seedBinding(p, '/q/1', 'mk:w:q1', 'confirmed', 'a'.repeat(64));
        await recomputeProviderStats(env, p);
        const row = await statsRow(p);
        expect(row?.tier_cap).toBe('C');          // 20-02 derank survives
        expect(row?.served_count).toBe(7);        // telemetry untouched
        expect(row?.failed_count).toBe(3);
        expect(row?.minted_count).toBe(1);
        expect(row?.solidified_count).toBe(1);
    });
});

describe('call-site hooks — mint / solidify / unbind', () => {
    it('a device-attributed canonize mint recomputes provider_stats', async () => {
        const work = 'mk:w:statmint';
        const a = await newDevice();
        const res = await canonize(canonBody(work, SRC, '/s/mint'), a.token);
        expect(res.status).toBe(200);
        const row = await statsRow(SRC);
        expect(row?.minted_count).toBe(1);
        expect(row?.solidified_count).toBe(0);
        expect(row?.confirm_count).toBe(1); // minter self-seed
        expect(row?.dynamic_tier).toBeNull();
    });

    it('a same-work re-post confirm does NOT recompute (new-row gate)', async () => {
        const work = 'mk:w:statrefresh';
        const ref = '/s/refresh';
        const a = await newDevice();
        await canonize(canonBody(work, SRC, ref), a.token);
        const b = await newDevice();
        await canonize(canonBody(work, SRC, ref), b.token); // refresh path
        const row = await statsRow(SRC);
        // The refresh inserted a confirm row but no new external_ids
        // row — confirm_count catches up on the next ledger event.
        expect(row?.minted_count).toBe(1);
        expect(row?.confirm_count).toBe(1);
        expect(row?.solidified_count).toBe(0);
    });

    it('a solidify flip recomputes — solidified_count follows', async () => {
        const work = 'mk:w:statsolid';
        const ref = '/s/solid';
        const a = await newDevice();
        await canonize(canonBody(work, SRC, ref), a.token);
        // Two more devices → 3 distinct confirms → solidified.
        for (const _ of [0, 1]) {
            const d = await newDevice();
            await canonize(canonBody(work, SRC, ref), d.token);
        }
        const binding = await env.COMMENTS_DB.prepare(
            'SELECT status FROM external_ids WHERE provider = ? AND external_id = ?',
        ).bind(SRC, ref).first<{ status: string }>();
        expect(binding?.status).toBe('confirmed');
        const row = await statsRow(SRC);
        expect(row?.solidified_count).toBe(1);
        expect(row?.confirm_count).toBe(3);
    });

    it('unbind deletes the row and drops minted_count back to zero', async () => {
        const work = 'mk:w:statunbind';
        const ref = '/s/gone';
        const a = await newDevice();
        await canonize(canonBody(work, SRC, ref), a.token);
        expect((await statsRow(SRC))?.minted_count).toBe(1);

        const res = await unbind(repBody(work, SRC, ref), a.token);
        expect((await res.json() as any)).toEqual({ unbound: true });
        const row = await statsRow(SRC);
        expect(row?.minted_count).toBe(0);
        expect(row?.confirm_count).toBe(0);
    });
});

describe('GET /v1/providers/tiers', () => {
    it('returns the keyed dynamic/cap map with a shared-cache hint', async () => {
        const p = 'ext.test.endpoint';
        await env.COMMENTS_DB.prepare(
            `INSERT INTO provider_stats
             (provider, minted_count, solidified_count, tier_cap, updated_at)
             VALUES (?, 1, 1, 'B', ?)`,
        ).bind(p, Date.now()).run();
        const res = await SELF.fetch('http://test/v1/providers/tiers');
        expect(res.status).toBe(200);
        expect(res.headers.get('cache-control')).toContain('max-age=300');
        const body = await res.json<{
            version: number;
            providers: Record<string, {
                dynamic_tier: string | null; tier_cap: string | null;
                minted_count: number; solidified_count: number;
            }>;
        }>();
        expect(body.version).toBe(RULESET.version);
        expect(body.providers[p]).toEqual({
            dynamic_tier: null, tier_cap: 'B',
            minted_count: 1, solidified_count: 1,
        });
    });

    it('an empty table yields an empty providers map, not an error', async () => {
        const res = await SELF.fetch('http://test/v1/providers/tiers');
        expect(res.status).toBe(200);
        const body = await res.json<{ providers: Record<string, unknown> }>();
        expect(body.providers['ext.test.neverseen']).toBeUndefined();
    });
});
