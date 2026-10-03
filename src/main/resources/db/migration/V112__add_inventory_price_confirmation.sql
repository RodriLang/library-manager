ALTER TABLE inventory_prices
    ADD COLUMN IF NOT EXISTS last_confirmed_at DATE;

ALTER TABLE inventory_prices
    ADD COLUMN IF NOT EXISTS last_confirmed_source VARCHAR(150);

-- Los precios existentes al menos se consideran conocidos desde su fecha de vigencia.
UPDATE inventory_prices
SET last_confirmed_at = effective_from
WHERE last_confirmed_at IS NULL;