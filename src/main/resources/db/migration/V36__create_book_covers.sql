CREATE TABLE book_covers
(
    id                  BIGSERIAL PRIMARY KEY,

    book_id             BIGINT                      NOT NULL,

    public_id           VARCHAR(255)                NOT NULL,
    secure_url          TEXT                        NOT NULL,

    original_source_url TEXT,

    source              VARCHAR(40)                 NOT NULL,
    status              VARCHAR(30)                 NOT NULL,

    content_hash        VARCHAR(64),

    format              VARCHAR(20),
    width               INTEGER,
    height              INTEGER,
    file_size           BIGINT,

    primary_cover       BOOLEAN                     NOT NULL DEFAULT FALSE,

    created_at          TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_book_covers_book
        FOREIGN KEY (book_id)
            REFERENCES books (id)
            ON DELETE CASCADE,

    CONSTRAINT uk_book_covers_public_id
        UNIQUE (public_id),

    CONSTRAINT ck_book_covers_width
        CHECK (width IS NULL OR width > 0),

    CONSTRAINT ck_book_covers_height
        CHECK (height IS NULL OR height > 0),

    CONSTRAINT ck_book_covers_file_size
        CHECK (file_size IS NULL OR file_size >= 0)
);

CREATE INDEX idx_book_covers_book_id
    ON book_covers (book_id);

CREATE INDEX idx_book_covers_status
    ON book_covers (status);

CREATE INDEX idx_book_covers_content_hash
    ON book_covers (content_hash)
    WHERE content_hash IS NOT NULL;

CREATE UNIQUE INDEX uk_book_covers_book_content_hash
    ON book_covers (book_id, content_hash)
    WHERE content_hash IS NOT NULL;

CREATE UNIQUE INDEX uk_book_covers_primary_per_book
    ON book_covers (book_id)
    WHERE primary_cover = TRUE;