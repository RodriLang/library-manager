CREATE TABLE price_list_import_staging_rows
(
    id                 BIGSERIAL PRIMARY KEY,

    job_id             BIGINT      NOT NULL,
    row_number         INTEGER     NOT NULL,

    identifier_key     VARCHAR(300),
    row_payload        JSONB       NOT NULL,

    valid              BOOLEAN     NOT NULL DEFAULT TRUE,
    validation_message TEXT,

    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_price_list_staging_job
        FOREIGN KEY (job_id)
            REFERENCES price_list_import_jobs (id)
            ON DELETE CASCADE
);

CREATE UNIQUE INDEX uk_price_list_staging_job_identifier
    ON price_list_import_staging_rows (job_id, identifier_key)
    WHERE identifier_key IS NOT NULL
        AND valid = TRUE;

CREATE INDEX idx_price_list_staging_job_valid_id
    ON price_list_import_staging_rows (job_id, valid, id);