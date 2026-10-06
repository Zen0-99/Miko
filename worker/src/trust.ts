import type { Identity } from './auth';
import { remergeChapters } from './chaptermap';
import { remergeEpisodes } from './episodemap';
import type { Env } from './env';
import { recomputeProviderStats } from './providerstats';
import { RULESET } from './ruleset';
import { logModeration } from './strikes';
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
    // Same coverage invariant as quarantine's re-pend (SD-12): the
    // just-confirmed binding covers (work, provider) → pending = 0.
    await syncFeedPending(env, workId, provider);
    // The flip moved solidified_count — provider tier credit recomputes
    // from the ledgers (20-04).
    await recomputeProviderStats(env, provider);
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
            // Provider stats move only on a NEW device-attributed row
            // (20-04) — direct-confirmed mints (crawler/trusted) write
            // NULL minter and earn no tier credit.
            if (!confirmed && a.deviceHash != null) {
                await recomputeProviderStats(env, a.provider);
            }
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

// ---------- 20-02: reports, quarantine, provider derank, rate caps ----------

const HOUR_MS = 60 * 60 * 1000;
const MIN_MS = 60 * 1000;

/**
 * D1 window-count rate caps for binding mutations — the rules.ts
 * shape over `binding_rate_log` keyed (device_hash, action). Caps are
 * versioned `RULESET.bindings.rate` entries (no KV/Rate-Limit binding
 * exists — the CF dashboard rule documented in worker/README is the
 * cost ceiling; this is the soft in-worker complement). 'mint' tiers
 * by device age like comment posts: identities younger than
 * `new_device_ms` get the tighter `new_device_*` cap. A missing key
 * disables that check. Returns the rule id the caller 429s with.
 */
export async function evaluateBindingRate(
    env: Env,
    id: Identity,
    action: 'mint' | 'report' | 'unbind',
): Promise<{ ok: true } | { ok: false; rule: string }> {
    const ts = now();
    const r = RULESET.bindings.rate ?? {};
    const isNew = ts - id.created_at < (r.new_device_ms ?? 0);
    const count = async (windowMs: number): Promise<number> =>
        (await env.COMMENTS_DB.prepare(
            `SELECT COUNT(*) AS n FROM binding_rate_log
             WHERE device_hash = ? AND action = ? AND created_at > ?`,
        ).bind(id.device_hash, action, ts - windowMs).first<{ n: number }>())?.n ?? 0;

    if (action === 'mint') {
        const cap = isNew
            ? (r.new_device_mints_per_hour ?? r.mints_per_hour)
            : r.mints_per_hour;
        if (cap != null && (await count(HOUR_MS)) >= cap) {
            return { ok: false, rule: 'rate_mint' };
        }
        return { ok: true };
    }
    if (action === 'report') {
        if (r.reports_per_min != null && (await count(MIN_MS)) >= r.reports_per_min) {
            return { ok: false, rule: 'rate_report' };
        }
        const perHour = isNew
            ? (r.new_device_reports_per_hour ?? r.reports_per_hour)
            : r.reports_per_hour;
        if (perHour != null && (await count(HOUR_MS)) >= perHour) {
            return { ok: false, rule: 'rate_report' };
        }
        return { ok: true };
    }
    if (r.unbinds_per_hour != null && (await count(HOUR_MS)) >= r.unbinds_per_hour) {
        return { ok: false, rule: 'rate_unbind' };
    }
    return { ok: true };
}

/**
 * Stamp an allowed attempt — rate rows are written on the attempt
 * (SD-15: after the cap check passes, before the mutation work) and
 * each event prunes the CALLER's own >24h rows — bounded and
 * index-friendly, no global sweep.
 */
export async function recordBindingRate(
    env: Env,
    deviceHash: string,
    action: 'mint' | 'report' | 'unbind',
): Promise<void> {
    const ts = now();
    await env.COMMENTS_DB.batch([
        env.COMMENTS_DB.prepare(
            'INSERT INTO binding_rate_log (device_hash, action, created_at) VALUES (?,?,?)',
        ).bind(deviceHash, action, ts),
        env.COMMENTS_DB.prepare(
            'DELETE FROM binding_rate_log WHERE device_hash = ? AND created_at < ?',
        ).bind(deviceHash, ts - DAY_MS),
    ]);
}

/**
 * Re-derive a feed's pending flag from binding coverage (SD-12): the
 * (work, provider) feed joins shared canon iff SOME confirmed binding
 * covers the pair — quarantining the only covering ref re-pends it; a
 * second confirmed ref keeps it published. No canonical_feeds row →
 * no-op.
 */
export async function syncFeedPending(env: Env, workId: string, provider: string): Promise<void> {
    await env.COMMENTS_DB.prepare(
        `UPDATE canonical_feeds SET pending = CASE WHEN EXISTS (
             SELECT 1 FROM external_ids
             WHERE miko_work_id = ? AND provider = ? AND status = 'confirmed'
         ) THEN 0 ELSE 1 END
         WHERE work_id = ? AND source_key = ?`,
    ).bind(workId, provider, workId, provider).run();
}

/**
 * Remerge dispatch on works_cache.medium — 'shows' merges episodes,
 * everything else merges chapters; a work we don't know can't remerge.
 */
export async function remergeForWork(env: Env, workId: string): Promise<void> {
    const work = await env.COMMENTS_DB.prepare(
        'SELECT medium FROM works_cache WHERE miko_work_id = ?',
    ).bind(workId).first<{ medium: string | null }>();
    if (work == null) return;
    if (work.medium === 'shows') await remergeEpisodes(env, workId);
    else await remergeChapters(env, workId);
}

/** identity_review dedupe — chaptermap's review() shape: an
 *  already-known (kind, payload) never enqueues twice. */
async function review(env: Env, kind: string, payload: unknown): Promise<void> {
    const json = JSON.stringify(payload);
    const { results } = await env.COMMENTS_DB.prepare(
        'SELECT 1 AS x FROM identity_review WHERE kind = ? AND payload = ? LIMIT 1',
    ).bind(kind, json).all();
    if (results.length > 0) return;
    await env.COMMENTS_DB.prepare(
        'INSERT INTO identity_review (kind, payload, created_at) VALUES (?,?,?)',
    ).bind(kind, json, now()).run();
}

/**
 * Quarantine transition (20-02): the guarded UPDATE flips any
 * non-quarantined row — provisional AND confirmed (a bad incumbent's
 * escape hatch, SD-14) — while a racing solidify can't win (its own
 * `status='provisional'` guard). On the flip: the feed re-pends by
 * coverage (SD-12) and remerges out of shared canon, `provider_stats`
 * is recomputed wholesale from the ledgers (SD-33 — never `+=`),
 * and the audit trail is a deduped identity_review row + a
 * moderation_log entry. Returns whether the row flipped.
 */
export async function quarantineBinding(
    env: Env,
    provider: string,
    ref: string,
    workId: string,
): Promise<boolean> {
    const res = await env.COMMENTS_DB.prepare(
        `UPDATE external_ids
         SET status = 'quarantined', quarantined_at = ?
         WHERE provider = ? AND external_id = ? AND status != 'quarantined'`,
    ).bind(now(), provider, ref).run();
    if ((res.meta.changes ?? 0) !== 1) return false;

    await syncFeedPending(env, workId, provider);
    await remergeForWork(env, workId);

    // Full ledger recompute (20-04) — subsumes 20-02's two-column
    // quarantined_count/report_weight write; tier_cap and telemetry
    // counters are preserved by omission (SD-33).
    await recomputeProviderStats(env, provider);

    await review(env, 'binding_quarantined', { work_id: workId, provider, ref });
    await logModeration(env, {
        layer: 'report_threshold', rule_id: 'binding_quarantine', action: 'quarantine',
    }, RULESET.version);
    return true;
}

/**
 * Cross-entry provider derank: recompute `provider_stats.report_weight`
 * over every non-'unbind' report row (recompute-on-write, SD-33 —
 * self-revocation is the minter fixing their own mistake, not provider
 * malfeasance, SD-13) and write the demotion ceiling once the sum
 * reaches `provider_derank_weight`. The tier_cap is a ceiling consumed
 * by 20-04's promotion math — it deletes no bindings and resets no
 * stats.
 */
export async function applyProviderDerank(env: Env, provider: string): Promise<void> {
    const weight = (await env.COMMENTS_DB.prepare(
        `SELECT COALESCE(SUM(weight),0) AS w FROM binding_reports
         WHERE provider = ? AND kind != 'unbind'`,
    ).bind(provider).first<{ w: number }>())?.w ?? 0;
    await env.COMMENTS_DB.prepare(
        `INSERT INTO provider_stats (provider, report_weight, updated_at)
         VALUES (?,?,?)
         ON CONFLICT(provider) DO UPDATE SET
            report_weight = excluded.report_weight,
            updated_at = excluded.updated_at`,
    ).bind(provider, weight, now()).run();

    const threshold = RULESET.bindings.provider_derank_weight;
    const cap = RULESET.bindings.provider_derank_cap;
    if (threshold == null || cap == null || weight < threshold) return;
    const existing = await env.COMMENTS_DB.prepare(
        'SELECT tier_cap FROM provider_stats WHERE provider = ?',
    ).bind(provider).first<{ tier_cap: string | null }>();
    // Written once — a cap already at the ruleset value stands.
    if (existing?.tier_cap === cap) return;
    await env.COMMENTS_DB.prepare(
        'UPDATE provider_stats SET tier_cap = ?, updated_at = ? WHERE provider = ?',
    ).bind(cap, now(), provider).run();
    await review(env, 'provider_deranked', { provider, tier_cap: cap });
}
