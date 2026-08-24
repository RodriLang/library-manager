ALTER TABLE inventory_movements
    DROP CONSTRAINT IF EXISTS chk_inventory_movements_quantity_positive;

ALTER TABLE inventory_movements
    ADD CONSTRAINT chk_inventory_movements_quantity_non_zero
        CHECK (quantity <> 0);