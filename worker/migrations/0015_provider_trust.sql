-- 0015: provider trust & mint integrity (Phase 20 / NCM-014).
--
-- Every device mint becomes a point-claim, not a fact: external_ids gains
-- a lifecycle (provisional → confirmed | quarantined), a minter
-- attribution column, and a denormalized points cache. Ledger tables
-- (binding_confirms / binding_reports) are the authoritative score;
-- external_ids.points is recomputed, never incremented. DEFAULT
-- 'confirmed' grandfathers every existing row (locked decision); the
-- points seed gives incumbents the confirm floor so a challenger must
-- still lead by the 2× margin.
--
-- canonical_feeds gains pending/submitter_device_hash — a provisional
-- binding's feed content is withheld from shared canon (pending=1)
-- until solidification (SD-1).

ALTER TABLE external_ids ADD COLUMN status TEXT NOT NULL DEFAULT 'confirmed';
ALTER TABLE external_ids ADD COLUMN minter_device_hash TEXT;
ALTER TABLE external_ids ADD COLUMN points REAL NOT NULL DEFAULT 0;
ALTER TABLE external_ids ADD COLUMN confirmed_at INTEGER;
ALTER TABLE external_ids ADD COLUMN quarantined_at INTEGER;
CREATE INDEX IF NOT EXISTS external_ids_work_status
    ON external_ids(miko_work_id, status);

-- Incumbency stamp: grandfathered rows hold the confirm floor (mirrors
-- RULESET.bindings.confirm_points_required = 3 in ruleset.json v2 — a
-- point-in-time value; later ruleset bumps do not re-seed).
UPDATE external_ids SET points = 3.0 WHERE status = 'confirmed';

CREATE TABLE IF NOT EXISTS binding_confirms (
    provider TEXT NOT NULL,
    external_id TEXT NOT NULL,
    miko_work_id TEXT NOT NULL,
    device_hash TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (provider, external_id, device_hash)
);
CREATE INDEX IF NOT EXISTS binding_confirms_work
    ON binding_confirms(miko_work_id, provider);

CREATE TABLE IF NOT EXISTS binding_reports (
    provider TEXT NOT NULL,
    external_id TEXT NOT NULL,
    miko_work_id TEXT NOT NULL,
    device_hash TEXT NOT NULL,
    weight REAL NOT NULL,
    kind TEXT NOT NULL DEFAULT 'report',       -- report | unbind
    created_at INTEGER NOT NULL,
    PRIMARY KEY (provider, external_id, device_hash)
);
CREATE INDEX IF NOT EXISTS binding_reports_work
    ON binding_reports(miko_work_id, provider);

CREATE TABLE IF NOT EXISTS trusted_minters (
    device_hash TEXT PRIMARY KEY,
    added_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS provider_stats (
    provider TEXT PRIMARY KEY,
    minted_count INTEGER NOT NULL DEFAULT 0,      -- device-attributed mints (ledger-derived on recompute)
    solidified_count INTEGER NOT NULL DEFAULT 0,  -- device-attributed rows now confirmed
    quarantined_count INTEGER NOT NULL DEFAULT 0,
    confirm_count INTEGER NOT NULL DEFAULT 0,
    served_count INTEGER NOT NULL DEFAULT 0,      -- client telemetry, observational only
    failed_count INTEGER NOT NULL DEFAULT 0,      -- client telemetry, observational only
    report_weight REAL NOT NULL DEFAULT 0,        -- cross-entry derank input (20-02)
    dynamic_tier TEXT,                            -- promotion output (20-04); NULL = manifest tier stands
    tier_cap TEXT,                                -- demotion ceiling (20-02); e.g. 'C' = never golden
    updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS binding_rate_log (
    device_hash TEXT NOT NULL,
    action TEXT NOT NULL,              -- mint | report | unbind
    created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS binding_rate_log_device
    ON binding_rate_log(device_hash, action, created_at);

ALTER TABLE canonical_feeds ADD COLUMN pending INTEGER NOT NULL DEFAULT 0;
ALTER TABLE canonical_feeds ADD COLUMN submitter_device_hash TEXT;
