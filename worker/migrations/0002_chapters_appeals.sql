-- Phase 10: chapter-thread alignment + Jev appeals (10-CONTEXT.md).

-- Every fingerprint a source submits; feeds inter-source consensus.
CREATE TABLE IF NOT EXISTS chapter_fingerprints (
    work_id TEXT NOT NULL,
    source_key TEXT NOT NULL,
    fingerprint_hash TEXT NOT NULL,
    count INTEGER NOT NULL,
    numbers_json TEXT NOT NULL,
    names_hash TEXT NOT NULL,
    first_seen INTEGER NOT NULL,
    last_seen INTEGER NOT NULL,
    PRIMARY KEY (work_id, source_key, fingerprint_hash)
);
CREATE INDEX IF NOT EXISTS chapter_fingerprints_work
    ON chapter_fingerprints(work_id, source_key);

-- Cached verdict per (work, source, fingerprint).
CREATE TABLE IF NOT EXISTS chapter_alignments (
    work_id TEXT NOT NULL,
    source_key TEXT NOT NULL,
    fingerprint_hash TEXT NOT NULL,
    status TEXT NOT NULL,             -- aligned | misaligned | unknown
    score REAL,
    reference TEXT,                   -- mangadex | consensus | none
    checked_at INTEGER NOT NULL,
    PRIMARY KEY (work_id, source_key, fingerprint_hash)
);

-- Numbering exceptions: reported -> canonical, applied before scoring.
CREATE TABLE IF NOT EXISTS chapter_aliases (
    work_id TEXT NOT NULL,
    source_key TEXT NOT NULL,
    reported REAL NOT NULL,
    canonical REAL NOT NULL,
    PRIMARY KEY (work_id, source_key, reported)
);

CREATE TABLE IF NOT EXISTS appeals (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    comment_id INTEGER NOT NULL,
    device_hash TEXT NOT NULL,
    reason TEXT,
    status TEXT NOT NULL,             -- pending | restored | upheld
    jev_answer TEXT,
    jev_confidence REAL,
    created_at INTEGER NOT NULL,
    resolved_at INTEGER
);
CREATE INDEX IF NOT EXISTS appeals_comment ON appeals(comment_id, status);
CREATE INDEX IF NOT EXISTS appeals_device ON appeals(device_hash, created_at);
