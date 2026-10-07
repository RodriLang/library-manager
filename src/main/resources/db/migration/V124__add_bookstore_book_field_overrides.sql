CREATE TABLE IF NOT EXISTS bookstore_book_field_overrides (
    id BIGSERIAL PRIMARY KEY,
    bookstore_id BIGINT NOT NULL REFERENCES bookstores(id) ON DELETE CASCADE,
    book_id BIGINT NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    field_name VARCHAR(40) NOT NULL,
    value TEXT NOT NULL,
    updated_by_user_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_bookstore_book_field_override UNIQUE (bookstore_id, book_id, field_name),
    CONSTRAINT ck_bookstore_book_field_override_field
        CHECK (field_name IN (
            'TITLE', 'SUBTITLE', 'DESCRIPTION', 'LANGUAGE', 'PAGE_COUNT',
            'PUBLICATION_YEAR', 'PUBLICATION_MONTH', 'COVER_URL',
            'CATEGORY_NAME', 'GENRE_NAME', 'PUBLISHER', 'AUTHORS',
            'WEIGHT_GRAMS', 'WIDTH_CM', 'HEIGHT_CM', 'DEPTH_CM'
        ))
);

CREATE INDEX IF NOT EXISTS idx_bookstore_book_field_overrides_bookstore_book
    ON bookstore_book_field_overrides (bookstore_id, book_id);

CREATE INDEX IF NOT EXISTS idx_bookstore_book_field_overrides_book_field
    ON bookstore_book_field_overrides (book_id, field_name);
