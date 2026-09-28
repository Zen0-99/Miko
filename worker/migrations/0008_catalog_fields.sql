-- Phase 15 browse/catalog: ranking + card fields on works_cache.
-- rating (MangaBaka 0-100 score), popularity (rank position, lower =
-- more popular), cover_url (card art), genres (JSON array string).
-- NULLs mean "not seeded" — browse sorts must order NULLs last.

ALTER TABLE works_cache ADD COLUMN rating REAL;
ALTER TABLE works_cache ADD COLUMN popularity INTEGER;
ALTER TABLE works_cache ADD COLUMN cover_url TEXT;
ALTER TABLE works_cache ADD COLUMN genres TEXT;

-- Catalog browse: per-carrier ranking (rating DESC NULLS-last is done
-- with `rating IS NULL` in the ORDER BY — the index covers the
-- medium filter + rating scan either way).
CREATE INDEX IF NOT EXISTS works_catalog_rating ON works_cache(medium, rating DESC);
-- Kind-filtered browse within a carrier.
CREATE INDEX IF NOT EXISTS works_catalog_kind ON works_cache(medium, type);
