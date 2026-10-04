-- 0010: durable per-source chapter feeds. canonical_chapters becomes the
-- materialized merge over this set — a canonize POST upserts its feeds and
-- remerges the whole corpus, so a submission can never erase a source it
-- didn't carry (and server-side seeds like MangaDex persist).

CREATE TABLE IF NOT EXISTS canonical_feeds (
    work_id TEXT NOT NULL,
    source_key TEXT NOT NULL,
    feed_json TEXT NOT NULL,
    verified INTEGER NOT NULL DEFAULT 0,
    score REAL,
    received_at INTEGER NOT NULL,
    PRIMARY KEY (work_id, source_key)
);
