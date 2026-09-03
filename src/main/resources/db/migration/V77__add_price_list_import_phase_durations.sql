-- V70__add_price_list_import_phase_durations.sql

ALTER TABLE price_list_import_jobs
    ADD COLUMN staging_duration_ms BIGINT,
    ADD COLUMN books_duration_ms   BIGINT,
    ADD COLUMN prices_duration_ms  BIGINT,
    ADD COLUMN total_duration_ms   BIGINT;

ALTER TABLE price_list_import_jobs
    ADD CONSTRAINT chk_price_list_import_jobs_staging_duration
        CHECK (staging_duration_ms IS NULL OR staging_duration_ms >= 0),
    ADD CONSTRAINT chk_price_list_import_jobs_books_duration
        CHECK (books_duration_ms IS NULL OR books_duration_ms >= 0),
    ADD CONSTRAINT chk_price_list_import_jobs_prices_duration
        CHECK (prices_duration_ms IS NULL OR prices_duration_ms >= 0),
    ADD CONSTRAINT chk_price_list_import_jobs_total_duration
        CHECK (total_duration_ms IS NULL OR total_duration_ms >= 0);