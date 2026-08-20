CREATE INDEX IF NOT EXISTS idx_books_title_sort_trgm_gist
    ON books
        USING gist (title_sort gist_trgm_ops)
    WHERE active = true;