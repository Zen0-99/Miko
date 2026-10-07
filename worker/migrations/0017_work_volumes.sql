-- 0017: volume segmentation (Phase 21-01 / NCM-016).
--
-- Canonical works gain real volume structure keyed on mk:mb:* ids:
--   work_volumes          — per-(work, volume, language-edition) cover +
--                           edition metadata (cover urls, blurhash, isbn,
--                           release date, page count). EN and JA editions
--                           of the same volume coexist as separate rows.
--   work_volume_chapters  — merged consensus boundary map:
--                           (work, chapter) → winning volume + vote
--                           confidence + contributing sources. Written
--                           ONLY by the seed endpoint (the local
--                           harvester merges votes; the worker stores
--                           the verdict).
--   volume_seed_queue     — lazy tail feed: a Bearer client's GET on a
--                           volume-less work enqueues it for the next
--                           harvest run.
--
-- works_cache gains final_volume + covers_count (the MangaBaka seed
-- already captured total_chapters but never these — pure seed gap).

CREATE TABLE work_volumes (
    work_id TEXT NOT NULL,
    volume_no REAL NOT NULL,          -- omnibus-safe (1.5, "3-in-1" halves)
    language TEXT NOT NULL,
    cover_url TEXT,
    cover_thumb_url TEXT,
    cover_blurhash TEXT,
    isbn TEXT,
    release_date TEXT,
    pages INTEGER,
    source TEXT NOT NULL,             -- mangabaka|mangadex|wiki|cubari|feed_markers|comick
    updated_at INTEGER NOT NULL,
    PRIMARY KEY (work_id, volume_no, language)
);
CREATE INDEX work_volumes_work ON work_volumes (work_id);

CREATE TABLE work_volume_chapters (
    work_id TEXT NOT NULL,
    chapter REAL NOT NULL,
    volume_no REAL NOT NULL,
    confidence REAL NOT NULL,         -- agreeing votes / total votes
    scheme TEXT NOT NULL DEFAULT 'tankobon',
    sources TEXT NOT NULL,            -- json array of contributing lanes
    PRIMARY KEY (work_id, chapter)
);
CREATE INDEX work_volume_chapters_work ON work_volume_chapters (work_id);

CREATE TABLE volume_seed_queue (
    work_id TEXT PRIMARY KEY,
    requested_at INTEGER NOT NULL,
    device_hash TEXT
);

ALTER TABLE works_cache ADD COLUMN final_volume INTEGER;
ALTER TABLE works_cache ADD COLUMN covers_count INTEGER;
