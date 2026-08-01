ALTER TABLE price_list_import_jobs
    ALTER COLUMN price_list_source DROP NOT NULL;

ALTER TABLE price_list_import_jobs
    ADD COLUMN provider_id      BIGINT,
    ADD COLUMN import_config_id BIGINT;

ALTER TABLE price_list_import_jobs
    ADD CONSTRAINT fk_price_list_import_jobs_provider
        FOREIGN KEY (provider_id)
            REFERENCES price_list_providers (id);

ALTER TABLE price_list_import_jobs
    ADD CONSTRAINT fk_price_list_import_jobs_config
        FOREIGN KEY (import_config_id)
            REFERENCES price_list_import_configs (id);

ALTER TABLE price_list_import_jobs
    ADD CONSTRAINT chk_price_list_import_jobs_source
        CHECK (
            (
                price_list_source IS NOT NULL
                    AND provider_id IS NULL
                    AND import_config_id IS NULL
                )
                OR
            (
                price_list_source IS NULL
                    AND provider_id IS NOT NULL
                    AND import_config_id IS NOT NULL
                )
            );

CREATE INDEX idx_price_list_import_jobs_provider
    ON price_list_import_jobs (provider_id);

CREATE INDEX idx_price_list_import_jobs_config
    ON price_list_import_jobs (import_config_id);