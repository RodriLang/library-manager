ALTER TABLE price_list_import_jobs
    ADD COLUMN skipped_rows INTEGER NOT NULL DEFAULT 0;