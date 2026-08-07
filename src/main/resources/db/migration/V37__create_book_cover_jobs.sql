CREATE TABLE book_cover_jobs
(
    id                       BIGSERIAL PRIMARY KEY,

    book_id                  BIGINT                      NOT NULL,
    price_list_import_job_id BIGINT,

    source_url               TEXT                        NOT NULL,
    normalized_source_url    TEXT                        NOT NULL,

    source                   VARCHAR(40)                 NOT NULL,
    source_row_number        INTEGER,

    job_key                  VARCHAR(64)                 NOT NULL,

    status                   VARCHAR(30)                 NOT NULL DEFAULT 'PENDING',

    attempts                 INTEGER                     NOT NULL DEFAULT 0,
    max_attempts             INTEGER                     NOT NULL DEFAULT 4,

    next_attempt_at          TIMESTAMP WITHOUT TIME ZONE,

    error_code               VARCHAR(50),
    error_message            TEXT,

    cloudinary_public_id     VARCHAR(255),

    created_at               TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at               TIMESTAMP WITHOUT TIME ZONE,
    completed_at             TIMESTAMP WITHOUT TIME ZONE,

    CONSTRAINT fk_book_cover_jobs_book
        FOREIGN KEY (book_id)
            REFERENCES books (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_book_cover_jobs_price_list_import_job
        FOREIGN KEY (price_list_import_job_id)
            REFERENCES price_list_import_jobs (id)
            ON DELETE SET NULL,

    CONSTRAINT uk_book_cover_jobs_job_key
        UNIQUE (job_key),

    CONSTRAINT ck_book_cover_jobs_attempts
        CHECK (attempts >= 0),

    CONSTRAINT ck_book_cover_jobs_max_attempts
        CHECK (max_attempts > 0),

    CONSTRAINT ck_book_cover_jobs_source_row
        CHECK (
            source_row_number IS NULL
                OR source_row_number > 0
            )
);

CREATE INDEX idx_book_cover_jobs_book_id
    ON book_cover_jobs (book_id);

CREATE INDEX idx_book_cover_jobs_status
    ON book_cover_jobs (status);

CREATE INDEX idx_book_cover_jobs_pending
    ON book_cover_jobs (
                        status,
                        next_attempt_at,
                        created_at
        );

CREATE INDEX idx_book_cover_jobs_price_list_import_job
    ON book_cover_jobs (price_list_import_job_id)
    WHERE price_list_import_job_id IS NOT NULL;