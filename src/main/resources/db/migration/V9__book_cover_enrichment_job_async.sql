CREATE TABLE cover_enrichment_jobs
(
    id                  BIGSERIAL PRIMARY KEY,
    status              VARCHAR(30) NOT NULL,
    total_books         INTEGER     NOT NULL DEFAULT 0,
    processed_books     INTEGER     NOT NULL DEFAULT 0,
    found_covers        INTEGER     NOT NULL DEFAULT 0,
    not_found_covers    INTEGER     NOT NULL DEFAULT 0,
    error_count         INTEGER     NOT NULL DEFAULT 0,
    progress_percentage INTEGER     NOT NULL DEFAULT 0,
    error_message       TEXT,
    started_at          TIMESTAMP,
    completed_at        TIMESTAMP,
    created_at          TIMESTAMP   NOT NULL,
    updated_at          TIMESTAMP
);