ALTER TABLE inventory
    ADD COLUMN editorial_price_sync_enabled BOOLEAN NOT NULL DEFAULT FALSE;