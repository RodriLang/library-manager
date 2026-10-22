-- Keep historical imports compatible; new clients explicitly choose the catalog destination.
ALTER TABLE inventory_price_imports
    ADD COLUMN sync_provider_catalog BOOLEAN NOT NULL DEFAULT TRUE;
