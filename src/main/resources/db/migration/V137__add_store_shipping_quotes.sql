CREATE TABLE IF NOT EXISTS store_shipping_quotes (
    id UUID PRIMARY KEY,
    store_id BIGINT NOT NULL REFERENCES bookstore_stores(id) ON DELETE CASCADE,
    bookstore_id BIGINT NOT NULL REFERENCES bookstores(id) ON DELETE CASCADE,
    provider VARCHAR(40) NOT NULL,
    service_code VARCHAR(100) NOT NULL,
    service_name VARCHAR(150) NOT NULL,
    delivery_type VARCHAR(30) NOT NULL,
    destination_postal_code VARCHAR(20) NOT NULL,
    items_hash VARCHAR(64) NOT NULL,
    provider_amount NUMERIC(14,2) NOT NULL,
    packaging_surcharge NUMERIC(14,2) NOT NULL DEFAULT 0,
    charged_amount NUMERIC(14,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'ARS',
    estimated_days_min INTEGER,
    estimated_days_max INTEGER,
    estimated_text VARCHAR(120),
    external_quote_id VARCHAR(160),
    free_shipping_applied BOOLEAN NOT NULL DEFAULT FALSE,
    weight_grams INTEGER NOT NULL,
    width_cm NUMERIC(10,2) NOT NULL,
    height_cm NUMERIC(10,2) NOT NULL,
    depth_cm NUMERIC(10,2) NOT NULL,
    volume_cm3 NUMERIC(14,2) NOT NULL,
    metadata_json TEXT,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_store_shipping_quote_provider CHECK (provider IN ('ANDREANI','CORREO_ARGENTINO','OCA','MANUAL','PICKUP_IN_STORE')),
    CONSTRAINT ck_store_shipping_quote_delivery_type CHECK (delivery_type IN ('HOME','BRANCH','PICKUP_POINT','STORE_PICKUP','CUSTOM')),
    CONSTRAINT ck_store_shipping_quote_amounts CHECK (provider_amount >= 0 AND packaging_surcharge >= 0 AND charged_amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_store_shipping_quotes_store_expires
    ON store_shipping_quotes(store_id, expires_at);

ALTER TABLE store_orders
    ADD COLUMN IF NOT EXISTS shipping_quote_id UUID,
    ADD COLUMN IF NOT EXISTS shipping_provider VARCHAR(40),
    ADD COLUMN IF NOT EXISTS shipping_service_code VARCHAR(100),
    ADD COLUMN IF NOT EXISTS shipping_service_name VARCHAR(150),
    ADD COLUMN IF NOT EXISTS shipping_destination_postal_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS shipping_destination_city VARCHAR(120),
    ADD COLUMN IF NOT EXISTS shipping_destination_state VARCHAR(120),
    ADD COLUMN IF NOT EXISTS shipping_destination_street VARCHAR(180),
    ADD COLUMN IF NOT EXISTS shipping_destination_street_number VARCHAR(40),
    ADD COLUMN IF NOT EXISTS shipping_destination_floor_apartment VARCHAR(80);
