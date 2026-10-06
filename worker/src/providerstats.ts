import type { Env } from './env';
import { RULESET } from './ruleset';
import { now, ok } from './util';

/**
 * Provider trust aggregation (Phase 20-04 / NCM-014).
 *
 * `provider_stats` is ledger-derived: every stat column is a SELECT
 * over `external_ids` / `binding_confirms` / `binding_reports`,
 * recomputed on each ledger-mutating event touching the provider
 * (mint, confirm-flip, quarantine, report, unbind). Nothing ever
 * `+=`s — counters cannot drift because they are re-derived, not
 * accumulated. The only surviving write-local columns are
 * `served_count`/`failed_count` — client-telemetry accumulations with
 * no ledger behind them (observational ONLY, structurally excluded
 * from tier math) — and `tier_cap`, the 20-02 derank ceiling written
 * solely by `applyProviderDerank`.
 *
 * Tier credit counts DEVICE-ATTRIBUTED rows only: `minted_count` and
 * `solidified_count` filter `minter_device_hash IS NOT NULL` —
 * crawler/server/seed mints and anonymous NULL-minter rows are
 * catalog facts, not trust evidence (T-20-04-05). `quarantined_count`
 * intentionally stays unfiltered, matching the 20-02 write this
 * recompute subsumes (SD-33): it reports how much of a provider's
 * catalog is under quarantine, whoever minted it.
 */

/** Ledger-derived inputs to tier promotion — nothing else feeds it. */
export interface ProviderStatInputs {
    minted_count: number;
    solidified_count: number;
}

/**
 * Dual-floor promotion (SD-25): walking the ruleset ladder
 * highest-first, the first tier whose per-tier solidified floor is
 * cleared WHILE the provider also carries ≥ `min_minted`
 * device-attributed mints and an integer-scaled acceptance rate
 * `solidified*100 ≥ acceptance_floor_pct*minted` (boundary-inclusive —
 * no floats) becomes `dynamic_tier`. A missing ruleset floor yields
 * no promotion at all. The shipped ladder tops at 'A' — dynamic tier
 * never emits 'S'.
 */
export function evaluateTier(stats: ProviderStatInputs): string | null {
    const t = RULESET.bindings.tiers;
    if (t?.min_minted == null || t.acceptance_floor_pct == null || t.ladder == null) {
        return null;
    }
    const ladder = Object.entries(t.ladder)
        .filter((e): e is [string, number] => typeof e[1] === 'number')
        .sort((a, b) => b[1] - a[1]); // highest threshold = highest tier
    for (const [tier, floor] of ladder) {
        if (
            stats.solidified_count >= floor &&
            stats.minted_count >= t.min_minted &&
            stats.solidified_count * 100 >= t.acceptance_floor_pct * stats.minted_count
        ) {
            return tier;
        }
    }
    return null;
}

/**
 * Recompute one provider's `provider_stats` row from the ledgers
 * (SD-24). The upsert lists only ledger-derived columns —
 * `served_count`/`failed_count`/`tier_cap` are untouched, and
 * `dynamic_tier` lands on the same write so the row can never carry
 * stale promotion math past a ledger event.
 */
export async function recomputeProviderStats(env: Env, provider: string): Promise<void> {
    const [minted, solidified, quarantined, confirms, reports] = await Promise.all([
        env.COMMENTS_DB.prepare(
            `SELECT COUNT(*) AS n FROM external_ids
             WHERE provider = ? AND minter_device_hash IS NOT NULL`,
        ).bind(provider).first<{ n: number }>(),
        env.COMMENTS_DB.prepare(
            `SELECT COUNT(*) AS n FROM external_ids
             WHERE provider = ? AND minter_device_hash IS NOT NULL
               AND status = 'confirmed'`,
        ).bind(provider).first<{ n: number }>(),
        env.COMMENTS_DB.prepare(
            `SELECT COUNT(*) AS n FROM external_ids
             WHERE provider = ? AND status = 'quarantined'`,
        ).bind(provider).first<{ n: number }>(),
        env.COMMENTS_DB.prepare(
            'SELECT COUNT(*) AS n FROM binding_confirms WHERE provider = ?',
        ).bind(provider).first<{ n: number }>(),
        // Belt-and-braces (SD-24): 'unbind' rows are already weight=0
        // per SD-13 — the SUM must not depend on that discipline.
        env.COMMENTS_DB.prepare(
            `SELECT COALESCE(SUM(weight),0) AS w FROM binding_reports
             WHERE provider = ? AND kind != 'unbind'`,
        ).bind(provider).first<{ w: number }>(),
    ]);
    const stats = {
        minted_count: minted?.n ?? 0,
        solidified_count: solidified?.n ?? 0,
        quarantined_count: quarantined?.n ?? 0,
        confirm_count: confirms?.n ?? 0,
        report_weight: reports?.w ?? 0,
    };
    await env.COMMENTS_DB.prepare(
        `INSERT INTO provider_stats
         (provider, minted_count, solidified_count, quarantined_count,
          confirm_count, report_weight, dynamic_tier, updated_at)
         VALUES (?,?,?,?,?,?,?,?)
         ON CONFLICT(provider) DO UPDATE SET
            minted_count = excluded.minted_count,
            solidified_count = excluded.solidified_count,
            quarantined_count = excluded.quarantined_count,
            confirm_count = excluded.confirm_count,
            report_weight = excluded.report_weight,
            dynamic_tier = excluded.dynamic_tier,
            updated_at = excluded.updated_at`,
    ).bind(
        provider, stats.minted_count, stats.solidified_count,
        stats.quarantined_count, stats.confirm_count, stats.report_weight,
        evaluateTier(stats), now(),
    ).run();
}

/**
 * GET /v1/providers/tiers (SD-27) — the whole `provider_stats` table
 * as a pkg-keyed map (provider-count-sized; a full scan IS the
 * endpoint). The worker publishes the raw `dynamic_tier` + `tier_cap`
 * columns and never pre-composes — the client applies "dynamic
 * promotes, cap demotes" against the manifest tier in one place.
 * `Cache-Control: max-age=300` is a shared-cache hint; the client's
 * own overlay TTL is 1h.
 */
export async function handleGetProviderTiers(_req: Request, env: Env): Promise<Response> {
    const { results } = await env.COMMENTS_DB.prepare(
        `SELECT provider, dynamic_tier, tier_cap, minted_count, solidified_count
         FROM provider_stats`,
    ).all<{
        provider: string;
        dynamic_tier: string | null;
        tier_cap: string | null;
        minted_count: number;
        solidified_count: number;
    }>();
    const providers: Record<string, {
        dynamic_tier: string | null;
        tier_cap: string | null;
        minted_count: number;
        solidified_count: number;
    }> = {};
    for (const r of results) {
        providers[r.provider] = {
            dynamic_tier: r.dynamic_tier,
            tier_cap: r.tier_cap,
            minted_count: r.minted_count,
            solidified_count: r.solidified_count,
        };
    }
    const res = ok({ version: RULESET.version, providers });
    res.headers.set('Cache-Control', 'max-age=300');
    return res;
}
