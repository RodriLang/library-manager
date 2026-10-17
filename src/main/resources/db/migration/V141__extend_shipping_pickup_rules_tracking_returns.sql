ALTER TABLE store_shipping_quotes
    ADD COLUMN IF NOT EXISTS pickup_point_external_id VARCHAR(160),
    ADD COLUMN IF NOT EXISTS pickup_point_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS pickup_point_address VARCHAR(300),
    ADD COLUMN IF NOT EXISTS pickup_point_postal_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS pickup_point_city VARCHAR(120),
    ADD COLUMN IF NOT EXISTS pickup_point_state VARCHAR(120),
    ADD COLUMN IF NOT EXISTS pickup_point_latitude DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS pickup_point_longitude DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS recommended BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS sort_priority INTEGER NOT NULL DEFAULT 100;

ALTER TABLE store_orders
    ADD COLUMN IF NOT EXISTS shipping_delivery_type VARCHAR(30),
    ADD COLUMN IF NOT EXISTS shipping_pickup_point_external_id VARCHAR(160),
    ADD COLUMN IF NOT EXISTS shipping_pickup_point_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS shipping_pickup_point_address VARCHAR(300),
    ADD COLUMN IF NOT EXISTS shipping_pickup_point_postal_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS shipping_pickup_point_city VARCHAR(120),
    ADD COLUMN IF NOT EXISTS shipping_pickup_point_state VARCHAR(120),
    ADD COLUMN IF NOT EXISTS shipping_pickup_point_latitude DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS shipping_pickup_point_longitude DOUBLE PRECISION;

ALTER TABLE store_return_items
    ADD COLUMN IF NOT EXISTS restocked_quantity INTEGER NOT NULL DEFAULT 0;

ALTER TABLE store_return_items DROP CONSTRAINT IF EXISTS ck_store_return_item_restocked;
ALTER TABLE store_return_items ADD CONSTRAINT ck_store_return_item_restocked
    CHECK (restocked_quantity >= 0 AND restocked_quantity <= quantity);

ALTER TABLE store_shipments
    ADD COLUMN IF NOT EXISTS tracking_sync_failures INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS tracking_last_synced_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS tracking_next_sync_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_store_shipments_tracking_sync
    ON store_shipments(status, tracking_next_sync_at, updated_at)
    WHERE tracking_number IS NOT NULL;
