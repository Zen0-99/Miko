import rulesetJson from '../ruleset.json';

export interface Ruleset {
    version: number;
    comment: {
        min_len: number;
        max_len: number;
        max_links: number;
        blocklist: string[];
        blocked_domains: string[];
        spam_patterns: string[];
        rate: {
            posts_per_min: number;
            posts_per_hour: number;
            /** Anonymous-device "trust level": identities younger than
             *  this many ms use the `new_device_*` hourly cap instead. */
            new_device_ms?: number;
            new_device_posts_per_hour?: number;
            /** Per-thread flood cap within `thread_burst_window_ms`. */
            thread_burst_per_window?: number;
            thread_burst_window_ms?: number;
        };
        duplicate_window_h: number;
    };
    name: {
        min_len: number;
        max_len: number;
        pattern: string;
        reserved: string[];
    };
    /** Phase 20 provider trust — the binding lifecycle's versioned
     *  constants. 20-01 reads confirm_points_required, margin_multiplier,
     *  age_bonus_* and telemetry.*; 20-02 reads rate/report_*; 20-04
     *  reads tiers. */
    bindings: {
        /** Distinct-device confirm floor for solidify — a raw COUNT,
         *  never the margin score. */
        confirm_points_required: number;
        /** Solidify margin: score ≥ margin × next-best sibling binding. */
        margin_multiplier: number;
        /** Cumulative report weight that quarantines a binding. */
        report_quarantine_weight: number;
        /** Reporter-age weighting (20-02): identities ≥ this old report
         *  at report_weight_established, younger at _new_device. */
        reporter_age_full_ms?: number;
        report_weight_established?: number;
        report_weight_new_device?: number;
        /** Time-without-reports bonus — enters the margin score only,
         *  never the raw confirm floor (no time-based auto-confirm). */
        age_bonus_per_day?: number;
        age_bonus_cap?: number;
        /** Cross-entry provider derank inputs (20-02). */
        provider_derank_weight?: number;
        provider_derank_cap?: string;
        rate?: {
            mints_per_hour?: number;
            new_device_mints_per_hour?: number;
            new_device_ms?: number;
            reports_per_hour?: number;
            new_device_reports_per_hour?: number;
            reports_per_min?: number;
            unbinds_per_hour?: number;
        };
        tiers?: {
            min_minted?: number;
            acceptance_floor_pct?: number;
            ladder?: Record<string, number>;
        };
        telemetry?: {
            max_entries?: number;
            max_count?: number;
        };
    };
}

export const RULESET = rulesetJson as unknown as Ruleset;
