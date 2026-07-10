CREATE TABLE price_list_import_jobs
(
    id                BIGSERIAL PRIMARY KEY,
    idempotency_key   VARCHAR(255) NOT NULL,
    price_list_source VARCHAR(50)  NOT NULL,
    status            VARCHAR(50)  NOT NULL,
    total_rows        INTEGER      NOT NULL DEFAULT 0,
    processed_rows    INTEGER      NOT NULL DEFAULT 0,
    created_books     INTEGER      NOT NULL DEFAULT 0,
    updated_books     INTEGER      NOT NULL DEFAULT 0,
    error_count       INTEGER      NOT NULL DEFAULT 0,
    error_message     TEXT,
    started_at        TIMESTAMP,
    finished_at       TIMESTAMP,
    created_at        TIMESTAMP    NOT NULL,
    CONSTRAINT uk_price_list_import_jobs_idempotency_key UNIQUE (idempotency_key)
);

CREATE TABLE price_list_import_job_errors
(
    id         BIGSERIAL PRIMARY KEY,
    job_id     BIGINT      NOT NULL,
    row_number INTEGER     NOT NULL,
    isbn       VARCHAR(255),
    message    TEXT        NOT NULL,
    severity   VARCHAR(50) NOT NULL,
    CONSTRAINT fk_price_list_import_job_errors_job
        FOREIGN KEY (job_id)
            REFERENCES price_list_import_jobs (id)
            ON DELETE CASCADE
);

CREATE INDEX idx_price_list_import_job_errors_job_id
    ON price_list_import_job_errors (job_id);