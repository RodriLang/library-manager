CREATE TABLE provider_books
(
    id            BIGSERIAL PRIMARY KEY,
    provider_id   BIGINT      NOT NULL,
    book_id       BIGINT      NOT NULL,
    external_code VARCHAR(100),
    active        BOOLEAN     NOT NULL DEFAULT TRUE,
    last_seen_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_provider_books_provider
        FOREIGN KEY (provider_id)
            REFERENCES price_list_providers (id),

    CONSTRAINT fk_provider_books_book
        FOREIGN KEY (book_id)
            REFERENCES books (id),

    CONSTRAINT uk_provider_books_provider_book
        UNIQUE (provider_id, book_id),

    CONSTRAINT uk_provider_books_external_code
        UNIQUE (provider_id, external_code)
);

CREATE INDEX idx_provider_books_provider
    ON provider_books (provider_id);

CREATE INDEX idx_provider_books_book
    ON provider_books (book_id);