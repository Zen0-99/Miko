-- Miko comments D1 — initial schema (Phase 8, 08-CONTEXT.md).

CREATE TABLE IF NOT EXISTS identities (
    device_hash TEXT PRIMARY KEY,
    token_hash TEXT NOT NULL,
    display_name TEXT UNIQUE,
    created_at INTEGER NOT NULL,
    name_set_at INTEGER,
    last_seen_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS external_ids (
    provider TEXT NOT NULL,
    external_id TEXT NOT NULL,
    miko_work_id TEXT NOT NULL,
    PRIMARY KEY (provider, external_id)
);
CREATE INDEX IF NOT EXISTS external_ids_work ON external_ids(miko_work_id);

CREATE TABLE IF NOT EXISTS works_cache (
    miko_work_id TEXT PRIMARY KEY,
    type TEXT NOT NULL,
    title TEXT NOT NULL,
    romanized_title TEXT,
    native_title TEXT,
    normalized_title TEXT NOT NULL,
    year INTEGER,
    total_chapters TEXT,
    author TEXT,
    provider_links TEXT NOT NULL DEFAULT '[]',
    fetched_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS works_titles (
    miko_work_id TEXT NOT NULL,
    title TEXT NOT NULL,
    lang TEXT,
    normalized TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS works_titles_normalized ON works_titles(normalized);

CREATE TABLE IF NOT EXISTS comments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    thread_id TEXT NOT NULL,
    device_hash TEXT NOT NULL,
    parent_id INTEGER,
    body TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    edited_at INTEGER,
    status TEXT NOT NULL DEFAULT 'visible'
);
CREATE INDEX IF NOT EXISTS comments_thread ON comments(thread_id, created_at);
CREATE INDEX IF NOT EXISTS comments_device_time ON comments(device_hash, created_at);

CREATE TABLE IF NOT EXISTS votes (
    comment_id INTEGER NOT NULL,
    device_hash TEXT NOT NULL,
    value INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    PRIMARY KEY (comment_id, device_hash)
);
CREATE INDEX IF NOT EXISTS votes_comment ON votes(comment_id);

CREATE TABLE IF NOT EXISTS reports (
    comment_id INTEGER NOT NULL,
    device_hash TEXT NOT NULL,
    weight REAL NOT NULL,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (comment_id, device_hash)
);

CREATE TABLE IF NOT EXISTS strikes (
    device_hash TEXT PRIMARY KEY,
    strike_level INTEGER NOT NULL DEFAULT 0,
    suspended_until INTEGER NOT NULL DEFAULT 0,
    last_strike_at INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS moderation_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    comment_id INTEGER,
    device_hash TEXT,
    layer TEXT NOT NULL,
    rule_id TEXT NOT NULL,
    ruleset_version INTEGER NOT NULL,
    action TEXT NOT NULL,
    created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS moderation_log_comment ON moderation_log(comment_id);

CREATE TABLE IF NOT EXISTS identity_review (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    kind TEXT NOT NULL,
    payload TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'open',
    created_at INTEGER NOT NULL
);
