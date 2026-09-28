-- Phase 12-04: canonical chapter map.
--
-- One canonical sequence per work, rebuilt in full by
-- POST /v1/chapters/canonize from golden-source feeds (tier S/A per
-- miko.json). Shared across devices — chapter threads (:c<num>) and
-- the unified chapter list bind to these rows.
--
-- number is NULL for unnumbered specials (prologue/extra/…) — they
-- merge across feeds only on identical normalized label. seq is the
-- display/scan order within the work. confidence: 'verified' when ≥2
-- distinct source feeds produced the same merge key, else 'single'.

CREATE TABLE IF NOT EXISTS canonical_chapters (
    work_id TEXT NOT NULL,
    seq INTEGER NOT NULL,
    number REAL,
    part INTEGER,
    volume REAL,
    kind TEXT NOT NULL,              -- chapter|prologue|epilogue|afterword|extra
    label TEXT NOT NULL,             -- representative source label
    confidence TEXT NOT NULL,        -- verified | single
    sources_json TEXT NOT NULL,      -- [source_key] that produced this entry
    refs_json TEXT NOT NULL DEFAULT '{}', -- {source_key: source url}
    updated_at INTEGER NOT NULL,
    PRIMARY KEY (work_id, seq)
);
CREATE INDEX IF NOT EXISTS canonical_chapters_lookup
    ON canonical_chapters(work_id, kind, number, part);
