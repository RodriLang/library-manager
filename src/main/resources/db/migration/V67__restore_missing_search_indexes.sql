CREATE INDEX IF NOT EXISTS idx_books_search_title_trgm
    ON books
        USING gin (
                   immutable_unaccent(lower(title)) gin_trgm_ops
            );

CREATE INDEX IF NOT EXISTS idx_books_search_subtitle_trgm
    ON books
        USING gin (
                   immutable_unaccent(lower(coalesce(subtitle, ''))) gin_trgm_ops
            );

CREATE INDEX IF NOT EXISTS idx_publishers_search_name_trgm
    ON publishers
        USING gin (
                   immutable_unaccent(lower(name)) gin_trgm_ops
            );

CREATE INDEX IF NOT EXISTS idx_authors_search_name_trgm
    ON authors
        USING gin (
                   immutable_unaccent(lower(name)) gin_trgm_ops
            );

ANALYZE books;
ANALYZE authors;
ANALYZE publishers;