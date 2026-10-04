-- 0011: provider bindings + work merges.
--
-- external_ids gains binding metadata — it already IS the work↔provider
-- map; confidence/provenance/last_seen_at make it a durable carrier
-- record for the crawler (where to fetch) and the app (who can serve
-- this work).
--
-- work_merges records catalog dedupe: dead ids remain as aliases that
-- read paths substitute for the live work.

ALTER TABLE external_ids ADD COLUMN confidence REAL;
ALTER TABLE external_ids ADD COLUMN provenance TEXT DEFAULT 'seed';
ALTER TABLE external_ids ADD COLUMN last_seen_at INTEGER;
CREATE INDEX IF NOT EXISTS external_ids_provider ON external_ids(provider);

CREATE TABLE IF NOT EXISTS work_merges (
    dead_id TEXT PRIMARY KEY,
    live_id TEXT NOT NULL,
    merged_at INTEGER NOT NULL
);
