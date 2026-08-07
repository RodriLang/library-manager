ALTER TABLE price_list_import_jobs
    ADD COLUMN processed_books INTEGER NOT NULL DEFAULT 0;

CREATE TABLE price_list_import_price_staging
(
    id                      BIGSERIAL PRIMARY KEY,

    job_id                  BIGINT         NOT NULL,
    book_id                 BIGINT         NOT NULL,

    selected_row_number     INTEGER        NOT NULL,
    selected_isbn           VARCHAR(32),
    selected_price          NUMERIC(14, 2) NOT NULL,

    first_row_number        INTEGER        NOT NULL,
    first_isbn              VARCHAR(32),
    first_price             NUMERIC(14, 2) NOT NULL,

    min_price               NUMERIC(14, 2) NOT NULL,
    max_price               NUMERIC(14, 2) NOT NULL,

    occurrence_count        INTEGER        NOT NULL DEFAULT 1,
    conflicting_price_count INTEGER        NOT NULL DEFAULT 0,

    created_at              TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_price_list_import_price_staging_job
        FOREIGN KEY (job_id)
            REFERENCES price_list_import_jobs (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_price_list_import_price_staging_book
        FOREIGN KEY (book_id)
            REFERENCES books (id),

    CONSTRAINT uk_price_list_import_price_staging_job_book
        UNIQUE (job_id, book_id),

    CONSTRAINT ck_price_list_import_price_staging_occurrence_count
        CHECK (occurrence_count >= 1),

    CONSTRAINT ck_price_list_import_price_staging_conflict_count
        CHECK (conflicting_price_count >= 0),

    CONSTRAINT ck_price_list_import_price_staging_prices
        CHECK (
            min_price <= max_price
                AND selected_price BETWEEN min_price AND max_price
            )
);

CREATE INDEX idx_price_list_import_price_staging_book_id
    ON price_list_import_price_staging (book_id);