ALTER TABLE inventory_price_imports
    ADD COLUMN preview_phase VARCHAR(40),
    ADD COLUMN preview_processed_rows INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN preview_progress_at TIMESTAMPTZ;
