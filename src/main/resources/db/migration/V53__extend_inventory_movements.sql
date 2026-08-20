-- =========================================================
-- INVENTORY MOVEMENTS
-- =========================================================

ALTER TABLE inventory_movements
    RENAME COLUMN reason TO note;


ALTER TABLE inventory_movements
    ADD COLUMN source VARCHAR(30);

ALTER TABLE inventory_movements
    ADD COLUMN reference_type VARCHAR(40);

ALTER TABLE inventory_movements
    ADD COLUMN reference_id VARCHAR(100);


-- Los movimientos antiguos, si existieran, fueron generados
-- por el sistema previo a la incorporación explícita del origen.
UPDATE inventory_movements
SET source = 'SYSTEM'
WHERE source IS NULL;


ALTER TABLE inventory_movements
    ALTER COLUMN source SET NOT NULL;


-- =========================================================
-- STOCK SNAPSHOT INTEGRITY
-- =========================================================

DO
$$
    BEGIN
        IF EXISTS (SELECT 1
                   FROM inventory_movements
                   WHERE stock_before IS NULL
                      OR stock_after IS NULL) THEN
            RAISE EXCEPTION
                'Existen inventory_movements sin stock_before/stock_after. Deben corregirse antes de aplicar NOT NULL.';
        END IF;
    END
$$;


ALTER TABLE inventory_movements
    ALTER COLUMN stock_before SET NOT NULL;

ALTER TABLE inventory_movements
    ALTER COLUMN stock_after SET NOT NULL;


-- =========================================================
-- INDEXES
-- =========================================================

CREATE INDEX IF NOT EXISTS idx_inventory_movements_inventory_created
    ON inventory_movements (inventory_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_inventory_movements_type
    ON inventory_movements (movement_type);

CREATE INDEX IF NOT EXISTS idx_inventory_movements_source
    ON inventory_movements (source);

CREATE INDEX IF NOT EXISTS idx_inventory_movements_reference
    ON inventory_movements (reference_type, reference_id);