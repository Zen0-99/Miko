import { env, SELF } from 'cloudflare:test';
import { describe, expect, it } from 'vitest';

// Phase 20-02 goldens — report/unbind endpoints, the weighted ledger,
// inclusive quarantine threshold, feed re-pend + remerge, provider
// derank ceilings, and D1 window-count rate caps. All exercised
// end-to-end through SELF.fetch against real (local) D1; storage is
// isolated per test.

const SRC = 'ext.test.alpha';
const DAY_MS = 86_400_000;
const HOUR_MS = 3_600_000;

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

/** Register + backdate past reporter_age_full_ms (30d) — reports then
 *  carry the established 1.0 weight instead of the new-device 0.25. */
async function establishedDevice(): Promise<{ hash: string; token: string }> {
    const d = await newDevice();
    await env.COMMENTS_DB.prepare(
        'UPDATE identities SET created_at = ? WHERE device_hash = ?',
    ).bind(Date.now() - 31 * DAY_MS, d.hash).run();
    return d;
}

/** The remerge dispatcher needs a works_cache row to read medium off. */
async function seedWork(work: string, medium = 'manga'): Promise<void> {
    await env.COMMENTS_DB.prepare(
        `INSERT INTO works_cache
         (miko_work_id, type, title, normalized_title, provider_links, medium, fetched_at)
         VALUES (?, 'manga', 'T', 't', '[]', ?, 0)`,
    ).bind(work, medium).run();
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

function canonizeEps(body: unknown, token?: string): Promise<Response> {
    return SELF.fetch('http://test/v1/episodes/canonize', {
        method: 'POST',
        headers: {
            'content-type': 'application/json',
            ...(token ? { authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify(body),
    });
}

function getChapters(work: string, token?: string): Promise<Response> {
    return SELF.fetch(`http://test/v1/works/${encodeURIComponent(work)}/chapters`, {
        headers: token ? { authorization: `Bearer ${token}` } : {},
    });
}

function getBindings(work: string, token?: string): Promise<Response> {
    return SELF.fetch(`http://test/v1/works/${encodeURIComponent(work)}/bindings`, {
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

const repBody = (work: string, provider: string, ref: string, kind?: string) => ({
    work_id: work,
    provider,
    provider_ref: ref,
    ...(kind ? { kind } : {}),
});

async function bindingRow(provider: string, ref: string) {
    return env.COMMENTS_DB.prepare(
        `SELECT status, minter_device_hash, quarantined_at
         FROM external_ids WHERE provider = ? AND external_id = ?`,
    ).bind(provider, ref).first<{
        status: string; minter_device_hash: string | null; quarantined_at: number | null;
    }>();
}

/** Mint via Bearer then two more devices → a solidified confirmed
 *  binding whose feed is published into shared canon. */
async function mintAndSolidify(work: string, ref: string, names = ['Chapter 1']) {
    const a = await newDevice();
    await canonize(canonBody(work, SRC, ref, names), a.token);
    for (const _ of [0, 1]) {
        const d = await newDevice();
        await canonize(canonBody(work, SRC, ref, names), d.token);
    }
    return a;
}

/** Rate-log seeds — direct rows so caps trip without paying the calls. */
async function seedRateRows(hash: string, action: string, n: number, ageMs = 0) {
    const ts = Date.now() - ageMs;
    await env.COMMENTS_DB.batch(
        Array.from({ length: n }, () =>
            env.COMMENTS_DB.prepare(
                'INSERT INTO binding_rate_log (device_hash, action, created_at) VALUES (?,?,?)',
            ).bind(hash, action, ts)),
    );
}

describe('POST /v1/bindings/report — guards', () => {
    it('401 unauthenticated, 403 suspended, 404 missing target and __none__', async () => {
        const work = 'mk:mb:rep-guard';
        expect((await report(repBody(work, SRC, '/r/g'))).status).toBe(401);

        // Suspended devices get the uniform write-block.
        const a = await newDevice();
        await env.COMMENTS_DB.prepare(
            `INSERT INTO strikes (device_hash, strike_level, suspended_until, last_strike_at, updated_at)
             VALUES (?,1,?,0,0)`,
        ).bind(a.hash, Date.now() + 60_000).run();
        const sus = await report(repBody(work, SRC, '/r/g'), a.token);
        expect(sus.status).toBe(403);
        const susBody = await sus.json() as any;
        expect(susBody.error).toBe('suspended');
        expect(susBody.suspended_until).toBeGreaterThan(0);

        // Missing target → 404; tombstone refs are never reportable
        // even though the (provider, '__none__') row can exist.
        const b = await newDevice();
        expect((await report(repBody(work, SRC, '/r/missing'), b.token)).status).toBe(404);
        await env.COMMENTS_DB.prepare(
            `INSERT INTO external_ids (provider, external_id, miko_work_id)
             VALUES (?,'__none__',?)`,
        ).bind(SRC, work).run();
        expect((await report(repBody(work, SRC, '__none__'), b.token)).status).toBe(404);
    });
});

describe('POST /v1/bindings/report — ledger', () => {
    it('new devices weigh 0.25, established 1.0, repeats dedupe on the PK', async () => {
        const work = 'mk:mb:rep-weight';
        const ref = '/series/weighted';
        const a = await newDevice();
        await canonize(canonBody(work, SRC, ref), a.token);

        const fresh = await newDevice();
        const r1 = await report(repBody(work, SRC, ref, 'wrong_work'), fresh.token);
        expect(r1.status).toBe(200);
        expect(await r1.json() as any).toEqual({ reported: true });
        const row = await env.COMMENTS_DB.prepare(
            `SELECT weight, kind, reason FROM binding_reports
             WHERE provider = ? AND external_id = ? AND device_hash = ?`,
        ).bind(SRC, ref, fresh.hash).first<{
            weight: number; kind: string; reason: string | null;
        }>();
        // 0016: the request `kind` lands in `reason`; ledger kind is
        // always 'report' so one-report-per-device dedupe survives the
        // kind-scoped PK.
        expect(row).toEqual({ weight: 0.25, kind: 'report', reason: 'wrong_work' });

        // Same device again → PK dedupe; the weight never doubles.
        const dup = await report(repBody(work, SRC, ref), fresh.token);
        expect(await dup.json() as any).toEqual({ reported: true, already: true });
        const { n } = await env.COMMENTS_DB.prepare(
            `SELECT COUNT(*) AS n FROM binding_reports
             WHERE provider = ? AND external_id = ?`,
        ).bind(SRC, ref).first<{ n: number }>() as { n: number };
        expect(n).toBe(1);

        const vet = await establishedDevice();
        await report(repBody(work, SRC, ref), vet.token);
        const { w } = await env.COMMENTS_DB.prepare(
            `SELECT COALESCE(SUM(weight),0) AS w FROM binding_reports
             WHERE provider = ? AND external_id = ?`,
        ).bind(SRC, ref).first<{ w: number }>() as { w: number };
        expect(w).toBe(1.25);
        // 1.25 < 3.0 — the binding keeps its status.
        expect((await bindingRow(SRC, ref))?.status).toBe('provisional');
    });

    it('quarantine is inclusive at 3.0: two established reports do not, the third does', async () => {
        const work = 'mk:mb:rep-threshold';
        const ref = '/series/qline';
        const a = await newDevice();
        await canonize(canonBody(work, SRC, ref), a.token);

        const d1 = await establishedDevice();
        const d2 = await establishedDevice();
        const d3 = await establishedDevice();
        await report(repBody(work, SRC, ref), d1.token);
        await report(repBody(work, SRC, ref, 'dead_feed'), d2.token);
        expect((await bindingRow(SRC, ref))?.status).toBe('provisional');

        const third = await report(repBody(work, SRC, ref, 'low_quality'), d3.token);
        expect(await third.json() as any).toEqual({ reported: true, quarantined: true });
        const row = await bindingRow(SRC, ref);
        expect(row?.status).toBe('quarantined');
        expect(row?.quarantined_at).toBeGreaterThan(0);

        // Any status stays reportable (SD-14) — the row records, but
        // the guarded UPDATE never re-fires the transition.
        const d4 = await establishedDevice();
        const extra = await report(repBody(work, SRC, ref), d4.token);
        expect(await extra.json() as any).toEqual({ reported: true });
        expect((await bindingRow(SRC, ref))?.status).toBe('quarantined');
    });

    it('quarantine re-pends the feed and drops its chapters from canon', async () => {
        const work = 'mk:mb:rep-canon';
        const ref = '/series/content';
        await seedWork(work);
        await mintAndSolidify(work, ref, ['Chapter 1', 'Chapter 2']);

        // Published for everyone pre-quarantine.
        expect(((await (await getChapters(work)).json() as any).chapters)).toHaveLength(2);
        const pre = await env.COMMENTS_DB.prepare(
            'SELECT pending FROM canonical_feeds WHERE work_id = ? AND source_key = ?',
        ).bind(work, SRC).first<{ pending: number }>();
        expect(pre?.pending).toBe(0);

        for (const _ of [0, 1, 2]) {
            const d = await establishedDevice();
            await report(repBody(work, SRC, ref), d.token);
        }
        expect((await bindingRow(SRC, ref))?.status).toBe('quarantined');
        const post = await env.COMMENTS_DB.prepare(
            'SELECT pending FROM canonical_feeds WHERE work_id = ? AND source_key = ?',
        ).bind(work, SRC).first<{ pending: number }>();
        expect(post?.pending).toBe(1);
        // Its content left shared canon — nobody is served it.
        expect(((await (await getChapters(work)).json() as any).chapters)).toHaveLength(0);

        // Listed flagged for transparency (never serveable).
        const binds = await (await getBindings(work)).json() as any;
        expect(binds.bindings[0].status).toBe('quarantined');

        // Audit trail: recomputed stats, deduped review, moderation row.
        const stats = await env.COMMENTS_DB.prepare(
            'SELECT quarantined_count, report_weight FROM provider_stats WHERE provider = ?',
        ).bind(SRC).first<{ quarantined_count: number; report_weight: number }>();
        expect(stats?.quarantined_count).toBe(1);
        expect(stats?.report_weight).toBe(3);
        const rev = await env.COMMENTS_DB.prepare(
            `SELECT payload FROM identity_review WHERE kind = 'binding_quarantined'`,
        ).first<{ payload: string }>();
        expect(JSON.parse(rev!.payload)).toMatchObject({ work_id: work, provider: SRC, ref });
        const mod = await env.COMMENTS_DB.prepare(
            `SELECT action FROM moderation_log WHERE rule_id = 'binding_quarantine'`,
        ).first<{ action: string }>();
        expect(mod?.action).toBe('quarantine');
    });

    it('a second confirmed ref on the same provider keeps the feed published', async () => {
        const work = 'mk:mb:rep-cover';
        const ref = '/series/victim';
        await seedWork(work);
        await mintAndSolidify(work, ref);
        // A sibling ref confirmed on the same (work, provider) — the
        // coverage rule keeps its feed joined to canon (SD-12).
        await env.COMMENTS_DB.prepare(
            `INSERT INTO external_ids
             (provider, external_id, miko_work_id, status, confirmed_at)
             VALUES (?,?,?,'confirmed',?)`,
        ).bind(SRC, '/series/cover', work, Date.now()).run();

        for (const _ of [0, 1, 2]) {
            const d = await establishedDevice();
            await report(repBody(work, SRC, ref), d.token);
        }
        expect((await bindingRow(SRC, ref))?.status).toBe('quarantined');
        const feed = await env.COMMENTS_DB.prepare(
            'SELECT pending FROM canonical_feeds WHERE work_id = ? AND source_key = ?',
        ).bind(work, SRC).first<{ pending: number }>();
        expect(feed?.pending).toBe(0);
        expect(((await (await getChapters(work)).json() as any).chapters)).toHaveLength(1);
    });

    it('cross-entry report volume writes the provider tier_cap once', async () => {
        const provider = 'ext.bad';
        const work = 'mk:mb:rep-derank';
        // 11.0 weight across other entries + one established report
        // lands the provider at exactly provider_derank_weight (12.0).
        await env.COMMENTS_DB.batch(
            Array.from({ length: 11 }, (_, i) =>
                env.COMMENTS_DB.prepare(
                    `INSERT INTO binding_reports
                     (provider, external_id, miko_work_id, device_hash, weight, kind, created_at)
                     VALUES (?,?,?,?,1.0,'report',?)`,
                ).bind(provider, `ref-${i}`, work, `d${i}`.padEnd(64, 'x'), Date.now())),
        );
        // A real binding to report on.
        await env.COMMENTS_DB.prepare(
            `INSERT INTO external_ids
             (provider, external_id, miko_work_id, status, confirmed_at)
             VALUES (?,?,?,'confirmed',?)`,
        ).bind(provider, 'ref-real', work, Date.now()).run();

        const d = await establishedDevice();
        await report(repBody(work, provider, 'ref-real'), d.token);
        const stats = await env.COMMENTS_DB.prepare(
            'SELECT report_weight, tier_cap FROM provider_stats WHERE provider = ?',
        ).bind(provider).first<{ report_weight: number; tier_cap: string | null }>();
        expect(stats?.report_weight).toBe(12);
        expect(stats?.tier_cap).toBe('C');
        const rev = await env.COMMENTS_DB.prepare(
            `SELECT payload FROM identity_review WHERE kind = 'provider_deranked'`,
        ).first<{ payload: string }>();
        expect(JSON.parse(rev!.payload)).toMatchObject({ provider, tier_cap: 'C' });

        // The cap is written once — further report volume adds no
        // duplicate review row.
        const d2 = await establishedDevice();
        await report(repBody(work, provider, 'ref-real'), d2.token);
        const { n } = await env.COMMENTS_DB.prepare(
            `SELECT COUNT(*) AS n FROM identity_review WHERE kind = 'provider_deranked'`,
        ).first<{ n: number }>() as { n: number };
        expect(n).toBe(1);
    });

    it('over-cap reports 429; allowed attempts log a rate row and prune >24h rows', async () => {
        const work = 'mk:mb:rep-rate';
        const ref = '/series/rated';
        const a = await newDevice();
        await canonize(canonBody(work, SRC, ref), a.token);

        // 5 report rows inside the minute → the next report is capped.
        const spammer = await newDevice();
        await seedRateRows(spammer.hash, 'report', 5);
        const capped = await report(repBody(work, SRC, ref), spammer.token);
        expect(capped.status).toBe(429);
        expect((await capped.json() as any).error).toBe('rate_report');

        // A passing attempt writes a rate row and prunes the caller's
        // own >24h rows — nobody else's (SD-15).
        const clean = await newDevice();
        await env.COMMENTS_DB.prepare(
            'INSERT INTO binding_rate_log (device_hash, action, created_at) VALUES (?,?,?)',
        ).bind(clean.hash, 'report', Date.now() - 25 * HOUR_MS).run();
        await env.COMMENTS_DB.prepare(
            'INSERT INTO binding_rate_log (device_hash, action, created_at) VALUES (?,?,?)',
        ).bind('f'.repeat(64), 'report', Date.now() - 25 * HOUR_MS).run();
        const okRes = await report(repBody(work, SRC, ref), clean.token);
        expect(okRes.status).toBe(200);
        const rows = await env.COMMENTS_DB.prepare(
            `SELECT created_at FROM binding_rate_log
             WHERE device_hash = ? AND action = 'report'`,
        ).bind(clean.hash).all<{ created_at: number }>();
        expect(rows.results).toHaveLength(1); // only the fresh attempt
        const survivor = await env.COMMENTS_DB.prepare(
            'SELECT COUNT(*) AS n FROM binding_rate_log WHERE device_hash = ?',
        ).bind('f'.repeat(64)).first<{ n: number }>();
        expect(survivor?.n).toBe(1);
    });

    it('medium dispatch: quarantine on a shows work remerges episodes out', async () => {
        const work = 'mk:imdb:ep-quar';
        const ref = '/show/bad';
        await seedWork(work, 'shows');
        const epsBody = {
            work_id: work,
            feeds: [{
                source_key: SRC, provider_ref: ref,
                episodes: [{ name: 'Episode 1', kind: 'episode', number: 1 }],
            }],
        };
        const a = await newDevice();
        await canonizeEps(epsBody, a.token);
        for (const _ of [0, 1]) {
            const d = await newDevice();
            await canonizeEps(epsBody, d.token);
        }
        const pre = await env.COMMENTS_DB.prepare(
            'SELECT COUNT(*) AS n FROM canonical_episodes WHERE work_id = ?',
        ).bind(work).first<{ n: number }>();
        expect(pre?.n).toBe(1);

        for (const _ of [0, 1, 2]) {
            const d = await establishedDevice();
            await report(repBody(work, SRC, ref), d.token);
        }
        expect((await bindingRow(SRC, ref))?.status).toBe('quarantined');
        const post = await env.COMMENTS_DB.prepare(
            'SELECT COUNT(*) AS n FROM canonical_episodes WHERE work_id = ?',
        ).bind(work).first<{ n: number }>();
        expect(post?.n).toBe(0);
    });
});

describe('POST /v1/bindings/unbind', () => {
    it('the minter deletes their own provisional row and the audit row lands', async () => {
        const work = 'mk:mb:unbind-own';
        const ref = '/series/oops';
        await seedWork(work);
        const a = await newDevice();
        await canonize(canonBody(work, SRC, ref), a.token);

        const res = await unbind(repBody(work, SRC, ref), a.token);
        expect(res.status).toBe(200);
        expect(await res.json() as any).toEqual({ unbound: true });

        // Row, confirms, and the caller-submitted feed are all gone.
        expect(await bindingRow(SRC, ref)).toBeNull();
        const confirms = await env.COMMENTS_DB.prepare(
            'SELECT COUNT(*) AS n FROM binding_confirms WHERE provider = ? AND external_id = ?',
        ).bind(SRC, ref).first<{ n: number }>();
        expect(confirms?.n).toBe(0);
        const feed = await env.COMMENTS_DB.prepare(
            'SELECT COUNT(*) AS n FROM canonical_feeds WHERE work_id = ? AND source_key = ?',
        ).bind(work, SRC).first<{ n: number }>();
        expect(feed?.n).toBe(0);
        const audit = await env.COMMENTS_DB.prepare(
            `SELECT weight, kind FROM binding_reports
             WHERE provider = ? AND external_id = ? AND device_hash = ?`,
        ).bind(SRC, ref, a.hash).first<{ weight: number; kind: string }>();
        expect(audit).toEqual({ weight: 0, kind: 'unbind' });

        // The freed (provider, ref) PK takes a correct re-mint cleanly.
        const b = await newDevice();
        await canonize(canonBody(work, SRC, ref), b.token);
        const remint = await bindingRow(SRC, ref);
        expect(remint?.status).toBe('provisional');
        expect(remint?.minter_device_hash).toBe(b.hash);
    });

    it('404s on rows the caller cannot revoke; 401/403/429 guards hold', async () => {
        const work = 'mk:mb:unbind-guard';
        const a = await newDevice();
        const b = await newDevice();
        await canonize(canonBody(work, SRC, '/r/mine'), a.token);

        // Another device can't revoke a's mint.
        expect((await unbind(repBody(work, SRC, '/r/mine'), b.token)).status).toBe(404);
        // A confirmed row can't be unbound — even by its minter.
        await env.COMMENTS_DB.prepare(
            `INSERT INTO external_ids
             (provider, external_id, miko_work_id, status, confirmed_at, minter_device_hash)
             VALUES (?,?,?,'confirmed',?,?)`,
        ).bind(SRC, '/r/conf', work, Date.now(), a.hash).run();
        expect((await unbind(repBody(work, SRC, '/r/conf'), a.token)).status).toBe(404);
        // NULL-minter rows belong to nobody.
        await canonize(canonBody(work, SRC, '/r/anon')); // no Bearer
        expect((await unbind(repBody(work, SRC, '/r/anon'), a.token)).status).toBe(404);
        // Missing row.
        expect((await unbind(repBody(work, SRC, '/r/nope'), a.token)).status).toBe(404);
        // No Bearer.
        expect((await unbind(repBody(work, SRC, '/r/mine'))).status).toBe(401);
        // Suspended — checkWrite applies to unbind too.
        await env.COMMENTS_DB.prepare(
            `INSERT INTO strikes (device_hash, strike_level, suspended_until, last_strike_at, updated_at)
             VALUES (?,1,?,0,0)`,
        ).bind(b.hash, Date.now() + 60_000).run();
        expect((await unbind(repBody(work, SRC, '/r/mine'), b.token)).status).toBe(403);
        // Over the unbinds_per_hour cap → 429 (rate fires before the
        // ownership check — denied attempts need no valid target).
        const spam = await newDevice();
        await seedRateRows(spam.hash, 'unbind', 10);
        const capped = await unbind(repBody(work, SRC, '/r/mine'), spam.token);
        expect(capped.status).toBe(429);
        expect((await capped.json() as any).error).toBe('rate_unbind');
    });

    it('a feed submitted by another device is never deleted on unbind', async () => {
        const work = 'mk:mb:unbind-feed';
        const ref = '/series/sharedfeed';
        await seedWork(work);
        const a = await newDevice();
        const b = await newDevice();
        await canonize(canonBody(work, SRC, ref), a.token);
        // b's re-post REPLACEs the feed row — b becomes the submitter.
        await canonize(canonBody(work, SRC, ref), b.token);
        const pre = await env.COMMENTS_DB.prepare(
            'SELECT pending, submitter_device_hash FROM canonical_feeds WHERE work_id = ? AND source_key = ?',
        ).bind(work, SRC).first<{ pending: number; submitter_device_hash: string }>();
        expect(pre?.submitter_device_hash).toBe(b.hash);

        expect((await unbind(repBody(work, SRC, ref), a.token)).status).toBe(200);
        const feed = await env.COMMENTS_DB.prepare(
            'SELECT pending FROM canonical_feeds WHERE work_id = ? AND source_key = ?',
        ).bind(work, SRC).first<{ pending: number }>();
        // The feed SURVIVES — b owns it. syncFeedPending re-pends it:
        // no confirmed binding covers (work, SRC) anymore.
        expect(feed?.pending).toBe(1);
    });

    it('the audit INSERT OR IGNORE keeps unbind idempotent on the PK', async () => {
        const work = 'mk:mb:unbind-idem';
        // report-then-unbind by the same device — self-report is
        // allowed, and the unbind audit insert OR-IGNOREs into the
        // existing PK row instead of throwing post-delete.
        const a = await newDevice();
        await canonize(canonBody(work, SRC, '/r/self'), a.token);
        await report(repBody(work, SRC, '/r/self', 'wrong_work'), a.token);
        const res = await unbind(repBody(work, SRC, '/r/self'), a.token);
        expect(await res.json() as any).toEqual({ unbound: true });
        const { n } = await env.COMMENTS_DB.prepare(
            `SELECT COUNT(*) AS n FROM binding_reports
             WHERE provider = ? AND external_id = ? AND device_hash = ?`,
        ).bind(SRC, '/r/self', a.hash).first<{ n: number }>() as { n: number };
        // 0016 kind-scoped PK: the 'report' row and the 'unbind' audit
        // row coexist (stacked signals are the design) — the insert
        // still OR-IGNOREs cleanly post-delete.
        expect(n).toBe(2);

        // unbind → re-mint → unbind: the second audit write dedupes too.
        const b = await newDevice();
        await canonize(canonBody(work, SRC, '/r/cycle'), b.token);
        expect((await unbind(repBody(work, SRC, '/r/cycle'), b.token)).status).toBe(200);
        await canonize(canonBody(work, SRC, '/r/cycle'), b.token);
        expect((await unbind(repBody(work, SRC, '/r/cycle'), b.token)).status).toBe(200);
        const audit = await env.COMMENTS_DB.prepare(
            `SELECT kind, weight, COUNT(*) AS n FROM binding_reports
             WHERE provider = ? AND external_id = ? AND device_hash = ?`,
        ).bind(SRC, '/r/cycle', b.hash).first<{ kind: string; weight: number; n: number }>();
        expect(audit).toEqual({ kind: 'unbind', weight: 0, n: 1 });

        // Unbind writes no provider report weight — self-revocation is
        // not derank evidence (the 0.25 came from a's self-report).
        const stats = await env.COMMENTS_DB.prepare(
            'SELECT report_weight FROM provider_stats WHERE provider = ?',
        ).bind(SRC).first<{ report_weight: number }>();
        expect(stats?.report_weight).toBe(0.25);
    });
});

describe('mint rate gate on canonize', () => {
    it('established devices cap at mints_per_hour; fresh at the new-device tier', async () => {
        const work = 'mk:mb:mintrate';
        // Established device: base cap 60 — the 61st gated post denies.
        const vet = await establishedDevice();
        await seedRateRows(vet.hash, 'mint', 60);
        const capped = await canonize(canonBody(work, SRC, '/r/cap60'), vet.token);
        expect(capped.status).toBe(429);
        expect((await capped.json() as any).error).toBe('rate_mint');

        // Fresh device: new_device_mints_per_hour (15).
        const fresh = await newDevice();
        await seedRateRows(fresh.hash, 'mint', 15);
        const capped2 = await canonize(canonBody(work, SRC, '/r/cap15'), fresh.token);
        expect(capped2.status).toBe(429);
        expect((await capped2.json() as any).error).toBe('rate_mint');

        // A canonize with no provider_ref isn't a mint — not gated.
        const feedOnly = await canonize({
            work_id: work,
            feeds: [{ source_key: SRC, chapters: [{ name: 'Chapter 1' }] }],
        }, fresh.token);
        expect(feedOnly.status).toBe(200);

        // Unauthenticated canonize can't be device-capped — the
        // provisional NULL-minter dead write stands (20-01).
        const anon = await canonize(canonBody(work, SRC, '/r/uncapped'));
        expect(anon.status).toBe(200);
        const row = await bindingRow(SRC, '/r/uncapped');
        expect(row?.status).toBe('provisional');
        expect(row?.minter_device_hash).toBeNull();

        // Under the cap the attempt logs a rate row.
        const under = await newDevice();
        await seedRateRows(under.hash, 'mint', 14);
        const okRes = await canonize(canonBody(work, SRC, '/r/under'), under.token);
        expect(okRes.status).toBe(200);
        const { n } = await env.COMMENTS_DB.prepare(
            `SELECT COUNT(*) AS n FROM binding_rate_log
             WHERE device_hash = ? AND action = 'mint'`,
        ).bind(under.hash).first<{ n: number }>() as { n: number };
        expect(n).toBe(15);
    });
});
