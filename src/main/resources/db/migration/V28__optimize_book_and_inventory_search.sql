CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE OR REPLACE FUNCTION immutable_unaccent(TEXT)
    RETURNS TEXT
    LANGUAGE SQL
    IMMUTABLE
    PARALLEL SAFE
    STRICT
AS
$$
SELECT public.unaccent('public.unaccent', $1)
$$;

CREATE INDEX IF NOT EXISTS idx_books_isbn13_prefix
    ON books (isbn_13 text_pattern_ops)
    WHERE isbn_13 IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_books_isbn10_prefix
    ON books (isbn_10 text_pattern_ops)
    WHERE isbn_10 IS NOT NULL;

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

CREATE INDEX IF NOT EXISTS idx_book_authors_book_id
    ON book_authors (book_id);

CREATE INDEX IF NOT EXISTS idx_book_authors_author_id
    ON book_authors (author_id);

CREATE INDEX IF NOT EXISTS idx_inventory_bookstore_active_book
    ON inventory (bookstore_id, active, book_id);

ANALYZE books;
ANALYZE authors;
ANALYZE publishers;
ANALYZE book_authors;
ANALYZE inventory;