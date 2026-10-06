import type { Env } from './env';
import { RULESET } from './ruleset';
import { now } from './util';

/**
 * Binding lifecycle engine (Phase 20 / NCM-014).
 *
 * `external_ids` rows carry a lifecycle — provisional → confirmed |
 * quarantined. A device mint lands provisional (served only to its
 * minter); it solidifies when `confirm_points_required` DISTINCT devices
 * have confirmed it AND its score clears `margin_multiplier` × the
 * next-best sibling binding on the same (work, provider). Reports count
 * against the margin score, never against the raw confirm floor — and
 * cumulative report weight ≥ `report_quarantine_weight` blocks solidify
 * outright (SD-32).
 *
 * `points` on the row is a denormalized cache of the effective score,
 * recomputed on every confirm/report event — never incremented.
 * Un-lifecycle-stamped confirmed rows (grandfathered pre-0015 rows and
 * resolve-side catalog mints: `confirmed_at IS NULL`) floor at
 * `confirm_points_required − reportWeight` — catalog facts keep
 * incumbent standing, eroding under reports (SD-31).
 */

const DAY_MS = 24 * 60 * 60 * 1000;

interface Ledger {
    /** Raw distinct-device confirm COUNT — the solidify floor. */
    confirmCount: number;
    /** SUM(binding_reports.weight) — subtracts from the margin score,
     *  erodes the incumbency stamp, blocks solidify at the cap. */
    reportWeight: number;
    /** Pure-ledger score (SD-4): confirms − reports + age bonus. */
    score: number;
}

async function bindingLedger(env: Env, provider: string, ref: string): Promise<Ledger> {
    const [confirms, reports, first] = await Promise.all([
        env.COMMENTS_DB.prepare(
            `SELECT COUNT(*) AS n FROM binding_confirms
             WHERE provider = ? AND external_id = ?`,
        ).bind(provider, ref).first<{ n: number }>(),
        env.COMMENTS_DB.prepare(
            `SELECT COALESCE(SUM(weight), 0) AS w FROM binding_reports
             WHERE provider = ? AND external_id = ?`,
        ).bind(provider, ref).first<{ w: number }>(),
        env.COMMENTS_DB.prepare(
            `SELECT MIN(created_at) AS t FROM binding_confirms
             WHERE provider = ? AND external_id = ?`,
        ).bind(provider, ref).first<{ t: number | null }>(),
    ]);
    const confirmCount = confirms?.n ?? 0;
    const reportWeight = reports?.w ?? 0;
    // Age bonus (SD-4): only while the binding carries zero report
    // weight; NULL-minter rows have no first-confirm stamp and earn none.
    let ageBonus = 0;
    if (reportWeight === 0 && first?.t != null) {
        const days = (now() - first.t) / DAY_MS;
        ageBonus = Math.min(
            days * (RULESET.bindings.age_bonus_per_day ?? 0),
            RULESET.bindings.age_bonus_cap ?? 0,
        );
    }
    return { confirmCount, reportWeight, score: confirmCount - reportWeight + ageBonus };
}

/** Pure-ledger binding score (SD-4) — no incumbency floor. */
export async function bindingScore(env: Env, provider: string, ref: string): Promise<number> {
    return (await bindingLedger(env, provider, ref)).score;
}

/**
 * Recompute the denormalized `points` cache on a binding row. For an
 * un-lifecycle-stamped confirmed row (`confirmed_at IS NULL` — every
 * pre-0015 grandfather and every resolve-side catalog mint, SD-31) the
 * effective score floors at `MAX(0, confirm_floor − reportWeight)`;
 * all other rows carry the pure-ledger score. Returns the value written.
 */
export async function recomputePoints(env: Env, provider: string, ref: string): Promise<number> {
    const row = await env.COMMENTS_DB.prepare(
        `SELECT status, confirmed_at FROM external_ids
         WHERE provider = ? AND external_id = ?`,
    ).bind(provider, ref).first<{ status: string; confirmed_at: number | null }>();
    if (!row) return 0;
    const ledger = await bindingLedger(env, provider, ref);
    let effective = ledger.score;
    if (row.status === 'confirmed' && row.confirmed_at === null) {
        effective = Math.max(
            ledger.score,
            Math.max(0, RULESET.bindings.confirm_points_required - ledger.reportWeight),
        );
    }
    await env.COMMENTS_DB.prepare(
        `UPDATE external_ids SET points = ?
         WHERE provider = ? AND external_id = ?`,
    ).bind(effective, provider, ref).run();
    return effective;
}

/** Trusted-minter allowlist (SD-5) — maintainer devices mint confirmed
 *  directly. D1-only write path (`wrangler d1 execute`); a trusted
 *  device's confirms of others' mints carry normal weight. */
export async function isTrustedMinter(env: Env, deviceHash: string): Promise<boolean> {
    const row = await env.COMMENTS_DB.prepare(
        'SELECT 1 AS x FROM trusted_minters WHERE device_hash = ?',
    ).bind(deviceHash).first<{ x: number }>();
    return row != null;
}

/**
 * Solidify gate (SD-3/SD-32): a provisional binding confirms when the
 * raw distinct-device confirm COUNT reaches the floor, its report weight
 * is below the quarantine threshold, and its effective score clears
 * `margin ×` the next-best sibling binding on the same (work, provider).
 * Competitors score `MAX(points, bindingScore)` so grandfathered rows
 * keep their seeded incumbency; '__none__' tombstones assert absence,
 * not a competing claim, and never compete. The UPDATE's
 * `AND status='provisional'` guard keeps a racing quarantine
 * authoritative. On the flip, the provider's pending feeds for this work
 * publish — the same request's remerge then lands them in canon.
 * Returns whether the row flipped.
 */
export async function maybeSolidify(
    env: Env,
    provider: string,
    ref: string,
    workId: string,
): Promise<boolean> {
    const row = await env.COMMENTS_DB.prepare(
        `SELECT status FROM external_ids
         WHERE provider = ? AND external_id = ?`,
    ).bind(provider, ref).first<{ status: string }>();
    if (row?.status !== 'provisional') return false;
    const ledger = await bindingLedger(env, provider, ref);
    if (ledger.confirmCount < RULESET.bindings.confirm_points_required) return false;
    if (ledger.reportWeight >= RULESET.bindings.report_quarantine_weight) return false;

    const { results: siblings } = await env.COMMENTS_DB.prepare(
        `SELECT external_id, points FROM external_ids
         WHERE miko_work_id = ? AND provider = ?
           AND external_id != ? AND external_id != '__none__'
           AND status != 'quarantined'`,
    ).bind(workId, provider, ref).all<{ external_id: string; points: number }>();
    let nextBest = 0;
    for (const s of siblings) {
        nextBest = Math.max(nextBest, s.points, await bindingScore(env, provider, s.external_id));
    }
    // Provisional rows carry no incumbency floor — effective = ledger.
    if (ledger.score < RULESET.bindings.margin_multiplier * nextBest) return false;

    const res = await env.COMMENTS_DB.prepare(
        `UPDATE external_ids
         SET status = 'confirmed', confirmed_at = ?, points = ?
         WHERE provider = ? AND external_id = ? AND status = 'provisional'`,
    ).bind(now(), ledger.score, provider, ref).run();
    if ((res.meta.changes ?? 0) !== 1) return false;
    await env.COMMENTS_DB.prepare(
        `UPDATE canonical_feeds SET pending = 0
         WHERE work_id = ? AND source_key = ?`,
    ).bind(workId, provider).run();
    return true;
}

export interface BindingWriteArgs {
    provider: string;
    ref: string;
    workId: string;
    confidence: number | null;
    /** Post-normalization provenance — the caller's crawler demote and
     *  user_picked floor run BEFORE this call. 'crawler' here is always
     *  an authenticated claim (it mints confirmed). */
    provenance: string;
    /** Bearer-resolved device hash, NULL for unauthenticated canonize —
     *  unauthed mints land provisional+NULL (invisible, SD-6). */
    deviceHash: string | null;
    ts: number;
}

export interface BindingWritePlan {
    provider: string;
    ref: string;
    /** The ref already binds to a DIFFERENT work — the caller keeps the
     *  provider_ref_conflict review path; no writes were queued. */
    conflict?: string;
    /** Whether this ref's carrier feed persists pending=1 (withheld
     *  from shared canon until the binding solidifies — SD-1). */
    pendingFeed: boolean;
    /** Statements to append to the request's shared batch. */
    writes: D1PreparedStatement[];
    /** Post-batch effects — run AFTER `env.COMMENTS_DB.batch(writes)` so
     *  the just-queued confirm rows are visible to the ledger (SD-10). */
    post: Array<() => Promise<void>>;
    /** Flipped when a post effect solidifies this ref. */
    solidified: boolean;
}

/**
 * The shared lifecycle decision behind both canonize provider_ref
 * blocks: mint (provisional-or-confirmed), refresh + confirm, or
 * different-work conflict. Never touches status/minter/points on the
 * conflict update arm — a refresh can confirm but never demote.
 */
export async function planBindingWrite(env: Env, a: BindingWriteArgs): Promise<BindingWritePlan> {
    // The binding is keyed (provider, ref) → a ref already bound to a
    // DIFFERENT work is an identity conflict: flag it, never rebind
    // silently (OPM sibling-work collision).
    const existing = await env.COMMENTS_DB.prepare(
        `SELECT miko_work_id, status, minter_device_hash FROM external_ids
         WHERE provider = ? AND external_id = ?`,
    ).bind(a.provider, a.ref).first<{
        miko_work_id: string;
        status: string;
        minter_device_hash: string | null;
    }>();

    const plan: BindingWritePlan = {
        provider: a.provider,
        ref: a.ref,
        pendingFeed: false,
        writes: [],
        post: [],
        solidified: false,
    };

    if (existing && existing.miko_work_id !== a.workId) {
        plan.conflict = existing.miko_work_id;
        return plan;
    }

    if (!existing) {
        // Fresh mint — trusted minters and authed crawlers write
        // confirmed directly; every other mint is provisional, visible
        // only to its minter (NULL minter → visible to no one, SD-6).
        const confirmed = a.provenance === 'crawler' ||
            (a.deviceHash != null && (await isTrustedMinter(env, a.deviceHash)));
        plan.writes.push(env.COMMENTS_DB.prepare(
            `INSERT INTO external_ids
             (provider, external_id, miko_work_id, confidence, provenance,
              last_seen_at, status, minter_device_hash, confirmed_at)
             VALUES (?,?,?,?,?,?,?,?,?)
             ON CONFLICT(provider, external_id) DO UPDATE SET
                confidence = COALESCE(excluded.confidence, external_ids.confidence),
                provenance = excluded.provenance,
                last_seen_at = excluded.last_seen_at`,
        ).bind(
            a.provider, a.ref, a.workId, a.confidence, a.provenance, a.ts,
            confirmed ? 'confirmed' : 'provisional',
            confirmed ? null : a.deviceHash,
            confirmed ? a.ts : null,
        ));
        if (a.deviceHash) {
            // The minter's own mint self-seeds the ledger — first-sight
            // stamp and first confirm point in one row (SD-4). NULL
            // minter → no seed; the row earns nothing until identified
            // devices confirm it.
            plan.writes.push(env.COMMENTS_DB.prepare(
                `INSERT OR IGNORE INTO binding_confirms
                 (provider, external_id, miko_work_id, device_hash, created_at)
                 VALUES (?,?,?,?,?)`,
            ).bind(a.provider, a.ref, a.workId, a.deviceHash, a.ts));
        }
        plan.pendingFeed = !confirmed;
        // Keep the points cache honest from the first event — the
        // recompute runs post-batch so it sees the self-seed.
        plan.post.push(async () => {
            await recomputePoints(env, a.provider, a.ref);
        });
        return plan;
    }

    // Same-work re-post: refresh the carrier stamp (status/minter/
    // points are never touched by a refresh), record the caller's
    // confirm — INSERT OR IGNORE dedupes repeat devices via the PK.
    plan.writes.push(env.COMMENTS_DB.prepare(
        `UPDATE external_ids
         SET confidence = COALESCE(?, confidence),
             provenance = ?,
             last_seen_at = ?
         WHERE provider = ? AND external_id = ?`,
    ).bind(a.confidence, a.provenance, a.ts, a.provider, a.ref));
    if (a.deviceHash) {
        plan.writes.push(env.COMMENTS_DB.prepare(
            `INSERT OR IGNORE INTO binding_confirms
             (provider, external_id, miko_work_id, device_hash, created_at)
             VALUES (?,?,?,?,?)`,
        ).bind(a.provider, a.ref, a.workId, a.deviceHash, a.ts));
    }
    plan.pendingFeed = existing.status !== 'confirmed';
    const wasProvisional = existing.status === 'provisional';
    plan.post.push(async () => {
        await recomputePoints(env, a.provider, a.ref);
        // Re-evaluate the lifecycle only while the row could still flip —
        // maybeSolidify's own status guard covers races regardless.
        if (wasProvisional &&
            (await maybeSolidify(env, a.provider, a.ref, a.workId))) {
            plan.solidified = true;
        }
    });
    return plan;
}

/**
 * Bundled mint telemetry (locked: zero new per-event requests — this
 * rides resolve/canonize/canonizeEpisodes/ids-seen bodies). Clamps into
 * `provider_stats.served_count`/`failed_count` — observational only,
 * never an input to lifecycle or tier math. Over-cap batches are skipped
 * whole and malformed entries clamped out, so a bad payload can never
 * fail its carrier request.
 */
export async function ingestTelemetry(env: Env, telemetry: unknown): Promise<void> {
    if (!Array.isArray(telemetry) || telemetry.length === 0) return;
    const t = RULESET.bindings.telemetry;
    const maxEntries = t?.max_entries ?? 50;
    const maxCount = t?.max_count ?? 100000;
    if (telemetry.length > maxEntries) return;
    const ts = now();
    const stmts: D1PreparedStatement[] = [];
    for (const e of telemetry) {
        if (typeof e !== 'object' || e === null) continue;
        const pkg = (e as Record<string, unknown>).pkg;
        const served = (e as Record<string, unknown>).served;
        const failed = (e as Record<string, unknown>).failed;
        if (typeof pkg !== 'string' || !pkg || pkg.length > 200) continue;
        const s = typeof served === 'number' && Number.isFinite(served) ? Math.floor(served) : 0;
        const f = typeof failed === 'number' && Number.isFinite(failed) ? Math.floor(failed) : 0;
        if (s < 0 || s > maxCount || f < 0 || f > maxCount) continue;
        if (s === 0 && f === 0) continue;
        stmts.push(env.COMMENTS_DB.prepare(
            `INSERT INTO provider_stats (provider, served_count, failed_count, updated_at)
             VALUES (?,?,?,?)
             ON CONFLICT(provider) DO UPDATE SET
                served_count = provider_stats.served_count + excluded.served_count,
                failed_count = provider_stats.failed_count + excluded.failed_count,
                updated_at = excluded.updated_at`,
        ).bind(pkg, s, f, ts));
    }
    if (stmts.length > 0) await env.COMMENTS_DB.batch(stmts);
}
