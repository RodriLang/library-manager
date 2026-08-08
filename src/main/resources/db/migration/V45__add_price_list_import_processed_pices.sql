ALTER TABLE price_list_import_jobs
    ADD COLUMN processed_prices INTEGER NOT NULL DEFAULT 0;