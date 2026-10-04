-- Batch hydrate endpoint (`GET /v1/works/hydrate`) looks up
-- same-normalized-title siblings to borrow covers for sparse rows.
-- Without this index each lookup is a full scan over works_cache.
CREATE INDEX IF NOT EXISTS works_cache_normalized_title
    ON works_cache(normalized_title);
