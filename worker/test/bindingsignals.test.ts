import { env, SELF } from 'cloudflare:test';
import { describe, expect, it } from 'vitest';

// Phase 20-05 goldens — the carrier vote slot, the stackable
// downvote/remove signals, the `my` state block on GET bindings, and
// the quarantine-kind isolation that keeps weak signals from ever
// hiding a binding. Report taxonomy rides the `reason` column so the
// kind-scoped PK still dedupes one report per device.

const SRC = 'ext.test.sig';
const DAY_MS = 86_400_000;
const HOUR_MS = 3_600_000;

let seq = 0;
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

async function establishedDevice(): Promise<{ hash: string; token: string }> {
    const d = await newDevice();
    await env.COMMENTS_DB.prepare(
        'UPDATE identities SET created_at = ? WHERE device_hash = ?',
    ).bind(Date.now() - 31 * DAY_MS, d.hash).run();
    return d;
}

function vote(body: unknown, token?: string): Promise<Response> {
    return SELF.fetch('http://test/v1/bindings/vote', {
        method: 'POST',
        headers: {
            'content-type': 'application/json',
            ...(token ? { authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify(body),
    });
}

function remove(body: unknown, token?: string): Promise<Response> {
    return SELF.fetch('http://test/v1/bindings/remove', {
        method: 'POST',
        headers: {
            'content-type': 'application/json',
            ...(token ? { authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify(body),
    });
}

function report(body: unknown, token?: string): Promise<Response> {
    return SELF.fetch('http://test/v1/bindings/report', {
        method: 'POST',
        headers: {
            'content-type': 'application/json',
            ...(token ? { authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify(body),
    });
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

function getBindings(work: string, token?: string): Promise<Response> {
    return SELF.fetch(`http://test/v1/works/${encodeURIComponent(work)}/bindings`, {
        headers: token ? { authorization: `Bearer ${token}` } : {},
    });
}

const canonBody = (work: string, src: string, ref: string) => ({
    work_id: work,
    feeds: [{ source_key: src, provider_ref: ref, chapters: [{ name: 'Chapter 1' }] }],
});

const voteBody = (work: string, provider: string, ref: string, v: string) => ({
    work_id: work, provider, provider_ref: ref, vote: v,
});

const remBody = (work: string, provider: string, ref: string, removed: boolean) => ({
    work_id: work, provider, provider_ref: ref, removed,
});

const repBody = (work: string, provider: string, ref: string, kind?: string) => ({
    work_id: work, provider, provider_ref: ref, ...(kind ? { kind } : {}),
});

async function signalRows(provider: string, ref: string) {
    const { results } = await env.COMMENTS_DB.prepare(
        `SELECT device_hash, kind, weight FROM binding_reports
         WHERE provider = ? AND external_id = ? ORDER BY kind`,
    ).bind(provider, ref).all<{ device_hash: string; kind: string; weight: number }>();
    return results;
}

async function points(provider: string, ref: string): Promise<number> {
    const row = await env.COMMENTS_DB.prepare(
        'SELECT points FROM external_ids WHERE provider = ? AND external_id = ?',
    ).bind(provider, ref).first<{ points: number }>();
    return row?.points ?? NaN;
}

async function status(provider: string, ref: string): Promise<string | null> {
    const row = await env.COMMENTS_DB.prepare(
        'SELECT status FROM external_ids WHERE provider = ? AND external_id = ?',
    ).bind(provider, ref).first<{ status: string }>();
    return row?.status ?? null;
}

async function seedRateRows(hash: string, action: string, n: number) {
    await env.COMMENTS_DB.batch(
        Array.from({ length: n }, () =>
            env.COMMENTS_DB.prepare(
                'INSERT INTO binding_rate_log (device_hash, action, created_at) VALUES (?,?,?)',
            ).bind(hash, action, Date.now())),
    );
}

describe('POST /v1/bindings/vote — the exclusive slot', () => {
    it('up inserts a confirm; down deletes the confirm and lands the flat signal; clear empties both', async () => {
        const work = 'mk:mb:vote-slot';
        const ref = '/series/slot';
        const minter = await newDevice();
        await canonize(canonBody(work, SRC, ref), minter.token);
        // Self-seed: the minter's own confirm — points start at 1 (+ a
        // millisecond-scale age bonus — asserts are closeTo).
        expect(await points(SRC, ref)).toBeCloseTo(1, 3);

        const v = await newDevice();
        expect((await vote(voteBody(work, SRC, ref, 'up'), v.token)).status).toBe(200);
        const { n: confirms } = await env.COMMENTS_DB.prepare(
            `SELECT COUNT(*) AS n FROM binding_confirms
             WHERE provider = ? AND external_id = ?`,
        ).bind(SRC, ref).first<{ n: number }>() as { n: number };
        expect(confirms).toBe(2);
        expect(await points(SRC, ref)).toBeCloseTo(2, 3);

        // Switch to down — the confirm row is deleted, downvote lands.
        await vote(voteBody(work, SRC, ref, 'down'), v.token);
        const rows = await signalRows(SRC, ref);
        expect(rows).toEqual([
            { device_hash: v.hash, kind: 'downvote', weight: 0.25 },
        ]);
        const { n: afterDown } = await env.COMMENTS_DB.prepare(
            `SELECT COUNT(*) AS n FROM binding_confirms
             WHERE provider = ? AND external_id = ? AND device_hash = ?`,
        ).bind(SRC, ref, v.hash).first<{ n: number }>() as { n: number };
        expect(afterDown).toBe(0);
        // minter confirm − downvote; a live signal kills the age bonus,
        // so this is exact.
        expect(await points(SRC, ref)).toBe(1 - 0.25);

        // Switch back to up — downvote row dies, confirm returns.
        await vote(voteBody(work, SRC, ref, 'up'), v.token);
        expect(await signalRows(SRC, ref)).toHaveLength(0);
        expect(await points(SRC, ref)).toBeCloseTo(2, 3);

        // Clear — slot empty again.
        await vote(voteBody(work, SRC, ref, 'clear'), v.token);
        expect(await points(SRC, ref)).toBeCloseTo(1, 3);
        const { n: cleared } = await env.COMMENTS_DB.prepare(
            `SELECT COUNT(*) AS n FROM binding_confirms
             WHERE provider = ? AND external_id = ?`,
        ).bind(SRC, ref).first<{ n: number }>() as { n: number };
        expect(cleared).toBe(1); // the minter's self-seed stands
    });

    it('an upvote can solidify a provisional over the margin; clear cannot demote', async () => {
        const work = 'mk:mb:vote-solid';
        const ref = '/series/margin';
        const minter = await newDevice();
        await canonize(canonBody(work, SRC, ref), minter.token);
        expect(await status(SRC, ref)).toBe('provisional');

        // Two more confirms reach the floor of 3.
        const b = await newDevice();
        const c = await newDevice();
        expect(
            await (await vote(voteBody(work, SRC, ref, 'up'), b.token)).json() as any,
        ).toEqual({ voted: true, vote: 'up' });
        const flip = await vote(voteBody(work, SRC, ref, 'up'), c.token);
        expect(await flip.json() as any).toEqual({ voted: true, vote: 'up', solidified: true });
        expect(await status(SRC, ref)).toBe('confirmed');

        // Post-solidify a clear erodes score but never reverts status.
        await vote(voteBody(work, SRC, ref, 'clear'), c.token);
        expect(await status(SRC, ref)).toBe('confirmed');
    });
});

describe('signal stacking & quarantine isolation', () => {
    it('down + report + remove stack to −1.5; repeats of a kind dedupe', async () => {
        const work = 'mk:mb:sig-stack';
        const ref = '/series/stack';
        const minter = await newDevice();
        await canonize(canonBody(work, SRC, ref), minter.token);

        const d = await establishedDevice(); // report at full 1.0
        await vote(voteBody(work, SRC, ref, 'down'), d.token);
        await report(repBody(work, SRC, ref, 'dead_feed'), d.token);
        await remove(remBody(work, SRC, ref, true), d.token);

        const rows = await signalRows(SRC, ref);
        expect(rows).toEqual([
            { device_hash: d.hash, kind: 'downvote', weight: 0.25 },
            { device_hash: d.hash, kind: 'remove', weight: 0.25 },
            { device_hash: d.hash, kind: 'report', weight: 1.0 },
        ]);
        // 1 confirm − 1.5 stacked signals.
        expect(await points(SRC, ref)).toBe(1 - 1.5);

        // Same-kind repeats dedupe on the kind-scoped PK.
        await vote(voteBody(work, SRC, ref, 'down'), d.token);
        await remove(remBody(work, SRC, ref, true), d.token);
        expect(await signalRows(SRC, ref)).toHaveLength(3);

        // A second report from the same device still dedupes — the
        // reason column didn't reopen the dedupe window.
        const dup = await report(repBody(work, SRC, ref, 'low_quality'), d.token);
        expect(await dup.json() as any).toEqual({ reported: true, already: true });
        expect(await signalRows(SRC, ref)).toHaveLength(3);
    });

    it('downvotes+removes alone never quarantine — only report weight does', async () => {
        const work = 'mk:mb:sig-quar';
        const ref = '/series/weakonly';
        const minter = await newDevice();
        await canonize(canonBody(work, SRC, ref), minter.token);

        // 12 devices × (down 0.25 + remove 0.25) = −6 signal weight —
        // far over the 3.0 threshold if weak signals counted.
        for (let i = 0; i < 12; i++) {
            const d = await newDevice();
            await vote(voteBody(work, SRC, ref, 'down'), d.token);
            await remove(remBody(work, SRC, ref, true), d.token);
        }
        expect(await status(SRC, ref)).toBe('provisional');
        expect(await points(SRC, ref)).toBe(1 - 6);
    });

    it('downvotes erode a grandfathered incumbent floor', async () => {
        const work = 'mk:mb:sig-incumbent';
        const ref = '/series/legacy';
        // Emulate the 0015 grandfather state: confirmed, NULL
        // confirmed_at, seeded at the confirm floor.
        await env.COMMENTS_DB.prepare(
            `INSERT INTO external_ids
             (provider, external_id, miko_work_id, status, points)
             VALUES (?,?,?,'confirmed',3.0)`,
        ).bind(SRC, ref, work).run();

        const a = await newDevice();
        const b = await newDevice();
        await vote(voteBody(work, SRC, ref, 'down'), a.token);
        await vote(voteBody(work, SRC, ref, 'down'), b.token);
        // Floor = MAX(0, 3 − 0.5) = 2.5.
        expect(await points(SRC, ref)).toBe(2.5);
    });
});

describe('POST /v1/bindings/remove — reversibility', () => {
    it('removed=true writes the signal; removed=false retracts it', async () => {
        const work = 'mk:mb:sig-remove';
        const ref = '/series/undo';
        const minter = await newDevice();
        await canonize(canonBody(work, SRC, ref), minter.token);

        const d = await newDevice();
        expect(
            await (await remove(remBody(work, SRC, ref, true), d.token)).json() as any,
        ).toEqual({ removed: true });
        expect(await points(SRC, ref)).toBe(0.75);

        expect(
            await (await remove(remBody(work, SRC, ref, false), d.token)).json() as any,
        ).toEqual({ removed: false });
        expect(await signalRows(SRC, ref)).toHaveLength(0);
        // Signals cleared → the age bonus resumes accruing.
        expect(await points(SRC, ref)).toBeCloseTo(1, 3);
    });
});

describe('GET bindings — per-device `my` state', () => {
    it('Bearer callers see vote/reported/removed per row; anonymous gets nulls', async () => {
        const work = 'mk:mb:sig-my';
        const ref = '/series/mine';
        const minter = await newDevice();
        await canonize(canonBody(work, SRC, ref), minter.token);
        // Solidify — a provisional row lists only for its minter, so the
        // signal test needs a binding visible to the voter's GET.
        for (const _ of [0, 1]) {
            const d = await newDevice();
            await canonize(canonBody(work, SRC, ref), d.token);
        }
        expect(await status(SRC, ref)).toBe('confirmed');

        const voter = await newDevice();
        await vote(voteBody(work, SRC, ref, 'down'), voter.token);
        await report(repBody(work, SRC, ref, 'wrong_work'), voter.token);
        await remove(remBody(work, SRC, ref, true), voter.token);

        const asVoter = await (await getBindings(work, voter.token)).json() as any;
        const row = asVoter.bindings.find((b: any) => b.provider_ref === ref);
        expect(row.my).toEqual({ vote: 'down', reported: true, removed: true });

        const asMinter = await (await getBindings(work, minter.token)).json() as any;
        const mrow = asMinter.bindings.find((b: any) => b.provider_ref === ref);
        expect(mrow.mine).toBe(true);
        expect(mrow.my).toEqual({ vote: 'up', reported: false, removed: false });

        const anon = await (await getBindings(work)).json() as any;
        expect(anon.bindings.find((b: any) => b.provider_ref === ref).my).toBeNull();
    });
});

describe('vote/remove guards', () => {
    it('401 unauthenticated, 403 suspended, 404 missing/tombstone, 429 rate-capped', async () => {
        const work = 'mk:mb:sig-guard';
        const ref = '/series/guards';
        const minter = await newDevice();
        await canonize(canonBody(work, SRC, ref), minter.token);

        expect((await vote(voteBody(work, SRC, ref, 'up'))).status).toBe(401);
        expect((await remove(remBody(work, SRC, ref, true))).status).toBe(401);

        const sus = await newDevice();
        await env.COMMENTS_DB.prepare(
            `INSERT INTO strikes (device_hash, strike_level, suspended_until, last_strike_at, updated_at)
             VALUES (?,1,?,0,0)`,
        ).bind(sus.hash, Date.now() + 60_000).run();
        expect((await vote(voteBody(work, SRC, ref, 'up'), sus.token)).status).toBe(403);
        expect((await remove(remBody(work, SRC, ref, true), sus.token)).status).toBe(403);

        const d = await newDevice();
        expect((await vote(voteBody(work, SRC, '/r/nope', 'up'), d.token)).status).toBe(404);
        await env.COMMENTS_DB.prepare(
            `INSERT INTO external_ids (provider, external_id, miko_work_id)
             VALUES (?,'__none__',?)`,
        ).bind(SRC, work).run();
        expect((await vote(voteBody(work, SRC, '__none__', 'up'), d.token)).status).toBe(404);
        expect((await vote(voteBody(work, SRC, ref, 'sideways'), d.token)).status).toBe(400);
        expect((await remove(remBody(work, SRC, ref, 'yes' as any), d.token)).status).toBe(400);

        const spam = await newDevice();
        await seedRateRows(spam.hash, 'vote', 10);
        expect((await vote(voteBody(work, SRC, ref, 'up'), spam.token)).status).toBe(429);
    });
});
