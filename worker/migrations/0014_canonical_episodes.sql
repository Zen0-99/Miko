-- Phase 19-02: canonical episode map (shows/anime) — sibling of the
-- chapter canon per D-04/D-04a. NOT a canonical_chapters extension:
-- its kind vocabulary is chapter-shaped, so episodes get their own
-- materialized view fed by the same canonical_feeds courier contract.
--
-- One canonical episode sequence per work, rebuilt in full by
-- POST /v1/episodes/canonize from the artifact feed ('mikodb:map' —
-- the device's on-device anime-map rows) plus carrier episode feeds.
-- kind: 'episode' (main sequence) | 'special' (S# keys) | 'nc'
-- (creditless C# keys; AniDB T/P fold into 'special').
--
-- number is the kind-scoped doc-local number (AniDB episode index for
-- artifact rows, provider-local for carriers). abs_number is the
-- canonical absolute episode number over mains only — NULL when no
-- feed canonicalized it (carrier-local numbering must not be treated
-- as canon). seq is display/scan order: mains by abs, then specials
-- by (season, ep_in_season), then nc by number.
--
-- confidence: 'verified' requires the reserved artifact source_key
-- 'mikodb:map' among contributors (D-01 — the artifact is the
-- structural authority); carrier-only rows stay 'single'.

CREATE TABLE IF NOT EXISTS canonical_episodes (
    work_id TEXT NOT NULL,
    seq INTEGER NOT NULL,
    kind TEXT NOT NULL,              -- episode|special|nc
    number REAL,                     -- kind-scoped number (doc-local for feeds)
    abs_number INTEGER,              -- dense canonical absolute over mains
    season INTEGER,
    ep_in_season INTEGER,
    ep_key TEXT,                     -- artifact key verbatim ("1","S3","C2")
    doc_id TEXT,                     -- "anidb:1234" — namespaces multi-doc works
    label TEXT NOT NULL,
    air_date TEXT,
    date_upload INTEGER,
    image TEXT,
    overview TEXT,
    runtime INTEGER,
    anidb_eid INTEGER,
    tvdb_eid INTEGER,
    tvdb_show_id INTEGER,
    filler INTEGER NOT NULL DEFAULT 0,
    confidence TEXT NOT NULL,        -- verified | single
    sources_json TEXT NOT NULL,      -- [source_key] that produced this entry
    refs_json TEXT NOT NULL DEFAULT '{}', -- {source_key: source url}
    updated_at INTEGER NOT NULL,
    PRIMARY KEY (work_id, seq)
);
CREATE INDEX IF NOT EXISTS idx_canon_eps_lookup
    ON canonical_episodes(work_id, kind, abs_number);

-- Sibling alignment tables — same columns as the 0002 chapter_*
-- tables, kept separate so a shows fingerprint/verdict can never
-- collide with a chapter one (D-04a; work_ids are medium-namespaced
-- anyway — belt and suspenders).
CREATE TABLE IF NOT EXISTS episode_fingerprints (
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
CREATE INDEX IF NOT EXISTS episode_fingerprints_work
    ON episode_fingerprints(work_id, source_key);

CREATE TABLE IF NOT EXISTS episode_alignments (
    work_id TEXT NOT NULL,
    source_key TEXT NOT NULL,
    fingerprint_hash TEXT NOT NULL,
    status TEXT NOT NULL,             -- aligned | misaligned | unknown
    score REAL,
    reference TEXT,                   -- canonical_episodes | consensus | none
    checked_at INTEGER NOT NULL,
    PRIMARY KEY (work_id, source_key, fingerprint_hash)
);
