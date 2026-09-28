-- Phase 14-01: coverage-gap telemetry. Stores ONLY work id + medium —
-- no user, device, or metadata by design.
CREATE TABLE IF NOT EXISTS seen_ids (
    work_id    TEXT PRIMARY KEY,
    medium     TEXT NOT NULL,
    first_seen INTEGER NOT NULL,
    seen_count INTEGER NOT NULL DEFAULT 1
);
