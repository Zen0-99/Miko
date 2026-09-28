-- Phase 15-02: honest-null kind — relax works_cache.type NOT NULL.
-- `type` IS the kind column; a show minted while Cinemeta is down keeps
-- its identity (mk:imdb:tt…) with kind NULL until upstream confirms it.
-- SQLite can't drop NOT NULL in place — rebuild the table.

CREATE TABLE works_cache_new (
    miko_work_id TEXT PRIMARY KEY,
    type TEXT,
    title TEXT NOT NULL,
    romanized_title TEXT,
    native_title TEXT,
    normalized_title TEXT NOT NULL,
    year INTEGER,
    total_chapters TEXT,
    author TEXT,
    provider_links TEXT NOT NULL DEFAULT '[]',
    fetched_at INTEGER NOT NULL,
    medium TEXT,
    nsfw INTEGER NOT NULL DEFAULT 0,
    content_rating TEXT
);

INSERT INTO works_cache_new
SELECT miko_work_id, type, title, romanized_title, native_title,
       normalized_title, year, total_chapters, author, provider_links,
       fetched_at, medium, nsfw, content_rating
FROM works_cache;

DROP TABLE works_cache;
ALTER TABLE works_cache_new RENAME TO works_cache;
