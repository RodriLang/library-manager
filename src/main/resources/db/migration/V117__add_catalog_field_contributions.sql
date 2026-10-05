ALTER TABLE books
    ADD COLUMN IF NOT EXISTS field_metadata jsonb NOT NULL DEFAULT '{}'::jsonb;

CREATE TABLE IF NOT EXISTS book_field_proposals (
    id BIGSERIAL PRIMARY KEY,
    book_id BIGINT NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    field_name VARCHAR(40) NOT NULL,
    current_value TEXT,
    proposed_value TEXT NOT NULL,
    submitted_by_bookstore_id BIGINT NOT NULL REFERENCES bookstores(id),
    submitted_by_user_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMPTZ,
    reviewed_by_user_id BIGINT REFERENCES users(id),
    CONSTRAINT ck_book_field_proposals_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUPERSEDED')),
    CONSTRAINT ck_book_field_proposals_field
        CHECK (field_name IN (
            'TITLE', 'SUBTITLE', 'DESCRIPTION', 'LANGUAGE', 'PAGE_COUNT',
            'PUBLICATION_YEAR', 'PUBLICATION_MONTH', 'COVER_URL',
            'CATEGORY_NAME', 'GENRE_NAME', 'PUBLISHER', 'AUTHORS',
            'WEIGHT_GRAMS', 'WIDTH_CM', 'HEIGHT_CM', 'DEPTH_CM'
        ))
);

CREATE INDEX IF NOT EXISTS idx_book_field_proposals_pending
    ON book_field_proposals (status, created_at DESC)
    WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_book_field_proposals_book_field
    ON book_field_proposals (book_id, field_name, status);

CREATE INDEX IF NOT EXISTS idx_book_field_proposals_bookstore
    ON book_field_proposals (submitted_by_bookstore_id, created_at DESC);
