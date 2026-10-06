ALTER TABLE inventory
    ADD COLUMN IF NOT EXISTS consignment_stock INTEGER NOT NULL DEFAULT 0;

ALTER TABLE inventory
    ADD COLUMN IF NOT EXISTS consignment_provider_id BIGINT REFERENCES providers(id);

ALTER TABLE inventory
    DROP CONSTRAINT IF EXISTS chk_inventory_consignment_stock;
ALTER TABLE inventory
    ADD CONSTRAINT chk_inventory_consignment_stock
        CHECK (consignment_stock >= 0 AND consignment_stock <= stock);

ALTER TABLE inventory
    DROP CONSTRAINT IF EXISTS chk_inventory_consignment_provider;
ALTER TABLE inventory
    ADD CONSTRAINT chk_inventory_consignment_provider
        CHECK (consignment_stock = 0 OR consignment_provider_id IS NOT NULL);

CREATE INDEX IF NOT EXISTS idx_inventory_bookstore_consignment
    ON inventory(bookstore_id, consignment_stock)
    WHERE consignment_stock > 0;
CREATE INDEX IF NOT EXISTS idx_inventory_consignment_provider
    ON inventory(consignment_provider_id)
    WHERE consignment_provider_id IS NOT NULL;

ALTER TABLE inventory_movements
    ADD COLUMN IF NOT EXISTS consignment_delta INTEGER NOT NULL DEFAULT 0;
ALTER TABLE inventory_movements
    ADD COLUMN IF NOT EXISTS consignment_before INTEGER NOT NULL DEFAULT 0;
ALTER TABLE inventory_movements
    ADD COLUMN IF NOT EXISTS consignment_after INTEGER NOT NULL DEFAULT 0;
ALTER TABLE inventory_movements
    ADD COLUMN IF NOT EXISTS consignment_provider_id BIGINT REFERENCES providers(id);

ALTER TABLE goods_receipt_items
    ADD COLUMN IF NOT EXISTS consignment_quantity INTEGER NOT NULL DEFAULT 0;
ALTER TABLE goods_receipt_items
    DROP CONSTRAINT IF EXISTS chk_goods_receipt_items_consignment;
ALTER TABLE goods_receipt_items
    ADD CONSTRAINT chk_goods_receipt_items_consignment
        CHECK (consignment_quantity >= 0 AND consignment_quantity <= received_quantity);

ALTER TABLE sale_items
    ADD COLUMN IF NOT EXISTS consignment_quantity INTEGER NOT NULL DEFAULT 0;
ALTER TABLE sale_items
    ADD COLUMN IF NOT EXISTS consignment_provider_id BIGINT REFERENCES providers(id);
ALTER TABLE sale_items
    DROP CONSTRAINT IF EXISTS chk_sale_items_consignment;
ALTER TABLE sale_items
    ADD CONSTRAINT chk_sale_items_consignment
        CHECK (consignment_quantity >= 0 AND consignment_quantity <= quantity);

CREATE TABLE IF NOT EXISTS consignment_settlements (
    id BIGSERIAL PRIMARY KEY,
    bookstore_id BIGINT NOT NULL REFERENCES bookstores(id),
    provider_id BIGINT NOT NULL REFERENCES providers(id),
    settlement_number VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SETTLED',
    settled_at TIMESTAMPTZ NOT NULL,
    notes VARCHAR(1000),
    created_by_user_id BIGINT REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_consignment_settlement_bookstore_number UNIQUE (bookstore_id, settlement_number),
    CONSTRAINT chk_consignment_settlement_status CHECK (status IN ('SETTLED', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_sale_items_consignment_provider
    ON sale_items(consignment_provider_id)
    WHERE consignment_quantity > 0;
CREATE INDEX IF NOT EXISTS idx_consignment_settlements_bookstore_provider
    ON consignment_settlements(bookstore_id, provider_id, settled_at DESC);

ALTER TABLE inventory_count_sessions
    ADD COLUMN IF NOT EXISTS default_consignment BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE inventory_count_sessions
    ADD COLUMN IF NOT EXISTS default_consignment_provider_id BIGINT REFERENCES providers(id);
ALTER TABLE inventory_count_items
    ADD COLUMN IF NOT EXISTS consignment_quantity_override INTEGER;
ALTER TABLE inventory_count_items
    ADD COLUMN IF NOT EXISTS consignment_provider_id BIGINT REFERENCES providers(id);
ALTER TABLE inventory_count_items
    DROP CONSTRAINT IF EXISTS chk_inventory_count_items_consignment;
ALTER TABLE inventory_count_items
    ADD CONSTRAINT chk_inventory_count_items_consignment
        CHECK (consignment_quantity_override IS NULL OR consignment_quantity_override >= 0);

ALTER TABLE inventory_movements
    DROP CONSTRAINT IF EXISTS chk_inventory_movements_type;
ALTER TABLE inventory_movements
    ADD CONSTRAINT chk_inventory_movements_type
        CHECK (movement_type IN (
            'INITIAL_STOCK','ENTRY','PURCHASE','SALE','RETURN','ADJUSTMENT','DAMAGE','LOSS','OWNERSHIP_ADJUSTMENT'
        ));

ALTER TABLE inventory_movements
    DROP CONSTRAINT IF EXISTS chk_inventory_movements_quantity_non_zero;
ALTER TABLE inventory_movements
    ADD CONSTRAINT chk_inventory_movements_quantity_non_zero
        CHECK (quantity <> 0 OR movement_type = 'OWNERSHIP_ADJUSTMENT');

CREATE TABLE IF NOT EXISTS consignment_settlement_items (
    id BIGSERIAL PRIMARY KEY,
    settlement_id BIGINT NOT NULL REFERENCES consignment_settlements(id) ON DELETE CASCADE,
    inventory_movement_id BIGINT NOT NULL REFERENCES inventory_movements(id),
    quantity INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_consignment_settlement_items_movement UNIQUE (inventory_movement_id),
    CONSTRAINT chk_consignment_settlement_items_quantity CHECK (quantity > 0)
);
CREATE INDEX IF NOT EXISTS idx_consignment_settlement_items_settlement
    ON consignment_settlement_items(settlement_id);
