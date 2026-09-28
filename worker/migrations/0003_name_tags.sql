-- Phase 10 follow-up: Discord-style name discriminators.
-- display_name alone is no longer unique; (lower(display_name), name_tag) is.
-- Table rebuild required — SQLite can't drop a column UNIQUE constraint.

CREATE TABLE identities_new (
    device_hash TEXT PRIMARY KEY,
    token_hash TEXT NOT NULL,
    display_name TEXT,
    name_tag TEXT,
    created_at INTEGER NOT NULL,
    name_set_at INTEGER,
    last_seen_at INTEGER NOT NULL
);

INSERT INTO identities_new (device_hash, token_hash, display_name, name_tag, created_at, name_set_at, last_seen_at)
SELECT device_hash,
       token_hash,
       display_name,
       CASE WHEN display_name IS NOT NULL
            THEN printf('%04d', rowid % 10000)
            ELSE NULL END,
       created_at, name_set_at, last_seen_at
FROM identities;

DROP TABLE identities;
ALTER TABLE identities_new RENAME TO identities;

CREATE UNIQUE INDEX IF NOT EXISTS identities_name_tag
    ON identities(LOWER(display_name), name_tag);
