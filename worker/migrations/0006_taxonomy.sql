-- Phase 15-01: unified taxonomy — carrier (medium), kind, nsfw.
-- medium ∈ {shows, manga, books} replaces the anime/manga/novel vocab;
-- `type` stays the kind column; nsfw is orthogonal (15-CONTEXT.md).

ALTER TABLE works_cache ADD COLUMN medium TEXT;
ALTER TABLE works_cache ADD COLUMN nsfw INTEGER NOT NULL DEFAULT 0;
-- Raw MangaBaka rating kept so the borderline 'suggestive' tag can be
-- surfaced later without re-fetching (see nsfw note below).
ALTER TABLE works_cache ADD COLUMN content_rating TEXT;

-- Backfill carrier from kind (type IS the kind column — spec:
-- "kind (work row type)"); books carrier = novel|book kinds ONLY.
-- 'oel' is a MANGA kind (original-English-language comics per the
-- MangaBaka vocab), NOT a books kind — it falls through to the manga
-- default below.
UPDATE works_cache SET medium = 'books' WHERE type IN ('novel','book')
  AND medium IS NULL;
UPDATE works_cache SET medium = 'manga' WHERE medium IS NULL;

-- Value rewrites (one-time, per locked ruling).
UPDATE seen_ids SET medium = 'shows' WHERE medium = 'anime';
UPDATE seen_ids SET medium = 'books' WHERE medium = 'novel';

-- Thread prefixes. Stored shapes today (ThreadIdResolver.kt:60-80,
-- ChapterBinder.kt:70):
--   anime:imdb:tt…[:eN]   anime:al:<id>[:eN]   anime:mal:<id>[:eN]
--   novel:mk:…[:…cN]      manga:mk:…[:…cN]
-- New emission is 'shows:<workId>' where workId is a mk:-style id
-- (shows:mk:imdb:tt…, shows:mk:m:…) — so imdb threads need the 'mk:'
-- infix inserted, not just a prefix swap. ':eN' suffixes survive the
-- substr rewrite unchanged.
UPDATE comments SET thread_id = 'shows:mk:' || substr(thread_id, 7)
  WHERE thread_id LIKE 'anime:imdb:%';
-- anime:al:<id> / anime:mal:<id> proto-ids embed tracker ids that only
-- the APP-side miko-anime-map.db can map to imdb — no server-side remap
-- to mk: ids exists (the anime map is not in D1). Carrier-prefix-only
-- rewrite; the 15-03 ThreadIdResolver keeps a provider-id fallback that
-- emits shows:al:<id>/shows:mal:<id> so these threads stay reachable.
UPDATE comments SET thread_id = 'shows:' || substr(thread_id, 7)
  WHERE thread_id LIKE 'anime:al:%' OR thread_id LIKE 'anime:mal:%';
UPDATE comments SET thread_id = 'books:' || substr(thread_id, 7)
  WHERE thread_id LIKE 'novel:%';
-- manga: rows untouched.
