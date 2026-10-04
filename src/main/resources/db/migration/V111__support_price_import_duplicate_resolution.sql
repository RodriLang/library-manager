ALTER TABLE inventory_price_import_items
    ADD COLUMN IF NOT EXISTS duplicate_group BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE inventory_price_import_items
    ADD COLUMN IF NOT EXISTS discarded BOOLEAN NOT NULL DEFAULT FALSE;

-- Importaciones creadas antes de esta migración:
-- los DUPLICATE_CONFLICT ya existentes forman parte de grupos duplicados.
UPDATE inventory_price_import_items
SET duplicate_group = TRUE
WHERE classification = 'DUPLICATE_CONFLICT';