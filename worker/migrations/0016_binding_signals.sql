-- 0016: stacked carrier signals (Phase 20-05 / NCM-015).
--
-- binding_reports' PK (provider, external_id, device_hash) allowed one
-- row per device per binding — the vote/report/remove model needs
-- stacked signals: a device may hold a 'downvote' AND a 'report' AND a
-- 'remove' on the same binding simultaneously (owner-locked 1/1.5 —
-- down 0.25 + report ~1.0 + remove 0.25 = -1.5 max vs +1 upvote).
-- SQLite can't ALTER a PK — rebuild with kind in the key so dedupe
-- remains per-(device, kind).
--
-- Report kinds fold into a `reason` column: putting the report
-- taxonomy in `kind` would let one device stack 5 report-kind rows
-- (~5.0 weight) and solo-quarantine a binding — the per-device report
-- dedupe MUST stay one 'report' row. kind ∈ {'report', 'downvote',
-- 'remove', 'unbind'}; reason carries the user-facing taxonomy.
--
-- Scoring consequence (trust.ts): `signalWeight` sums every kind except
-- the zero-weight 'unbind' audit rows; `reportWeight` sums only
-- kind='report' and remains the sole quarantine input — downvotes erode
-- margin/incumbency but can never quarantine alone.

CREATE TABLE binding_reports_v2 (
    provider TEXT NOT NULL,
    external_id TEXT NOT NULL,
    miko_work_id TEXT NOT NULL,
    device_hash TEXT NOT NULL,
    weight REAL NOT NULL,
    kind TEXT NOT NULL DEFAULT 'report',
    reason TEXT,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (provider, external_id, device_hash, kind)
);
INSERT INTO binding_reports_v2
    SELECT provider, external_id, miko_work_id, device_hash, weight,
        CASE WHEN kind IN ('report','wrong_work','dead_feed','low_quality','other')
             THEN 'report' ELSE kind END,
        CASE WHEN kind IN ('wrong_work','dead_feed','low_quality','other')
             THEN kind ELSE NULL END,
        created_at
    FROM binding_reports;
DROP TABLE binding_reports;
ALTER TABLE binding_reports_v2 RENAME TO binding_reports;
CREATE INDEX IF NOT EXISTS binding_reports_work
    ON binding_reports(miko_work_id, provider);
