ALTER TABLE price_list_import_jobs
    ADD COLUMN duplicate_book_rows INTEGER NOT NULL DEFAULT 0;