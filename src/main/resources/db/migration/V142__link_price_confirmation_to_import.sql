ALTER TABLE inventory_prices
    ADD COLUMN IF NOT EXISTS last_confirmed_price_import_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_inventory_price_last_confirmed_import'
    ) THEN
        ALTER TABLE inventory_prices
            ADD CONSTRAINT fk_inventory_price_last_confirmed_import
            FOREIGN KEY (last_confirmed_price_import_id)
            REFERENCES inventory_price_imports(id)
            ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_inventory_prices_last_confirmed_import
    ON inventory_prices(last_confirmed_price_import_id);
