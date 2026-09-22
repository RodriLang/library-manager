CREATE INDEX IF NOT EXISTS idx_authors_name_normalized_fts
    ON authors
    USING gin (to_tsvector('simple', name_normalized));

CREATE INDEX IF NOT EXISTS idx_publishers_name_normalized_fts
    ON publishers
    USING gin (to_tsvector('simple', name_normalized));

DROP INDEX IF EXISTS idx_authors_search_name_trgm;
DROP INDEX IF EXISTS idx_publishers_search_name_trgm;

ANALYZE authors;
ANALYZE publishers;
