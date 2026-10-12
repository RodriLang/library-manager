CREATE TABLE IF NOT EXISTS store_shipments (
    id BIGSERIAL PRIMARY KEY,
    bookstore_id BIGINT NOT NULL REFERENCES bookstores(id) ON DELETE CASCADE,
    order_id BIGINT NOT NULL REFERENCES store_orders(id) ON DELETE CASCADE,
    provider VARCHAR(40) NOT NULL,
    service_code VARCHAR(100),
    service_name VARCHAR(150),
    status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    provider_shipment_id VARCHAR(160),
    tracking_number VARCHAR(160),
    tracking_url TEXT,
    label_url TEXT,
    provider_status VARCHAR(120),
    provider_status_detail TEXT,
    weight_grams INTEGER,
    width_cm NUMERIC(10,2),
    height_cm NUMERIC(10,2),
    depth_cm NUMERIC(10,2),
    declared_value NUMERIC(14,2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at_provider TIMESTAMPTZ,
    dispatched_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    CONSTRAINT ck_store_shipments_provider CHECK (provider IN ('ANDREANI','CORREO_ARGENTINO','OCA','MANUAL','PICKUP_IN_STORE')),
    CONSTRAINT ck_store_shipments_status CHECK (status IN ('DRAFT','READY_TO_SHIP','IN_TRANSIT','DELIVERED','EXCEPTION','CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_store_shipments_order ON store_shipments(order_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_store_shipments_bookstore_status ON store_shipments(bookstore_id, status);
CREATE UNIQUE INDEX IF NOT EXISTS uq_store_shipments_provider_id
    ON store_shipments(provider, provider_shipment_id)
    WHERE provider_shipment_id IS NOT NULL;
