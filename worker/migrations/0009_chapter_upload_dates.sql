-- Phase 13-05: real chapter upload dates on the canonical sequence.
--
-- Golden-source feeds carry each source chapter's published timestamp;
-- the canonical row stores the earliest contributing date. NULL stays
-- NULL (unknown) — clients render "N/A" instead of stamping sync time.

ALTER TABLE canonical_chapters ADD COLUMN date_upload INTEGER;
