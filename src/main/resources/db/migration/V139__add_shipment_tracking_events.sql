ALTER TABLE store_shipments DROP CONSTRAINT IF EXISTS ck_store_shipments_status;
ALTER TABLE store_shipments
    ADD CONSTRAINT ck_store_shipments_status
    CHECK (status IN ('DRAFT','READY_TO_SHIP','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','EXCEPTION','CANCELLED'));

CREATE TABLE IF NOT EXISTS store_shipment_tracking_events (
    id BIGSERIAL PRIMARY KEY,
    shipment_id BIGINT NOT NULL REFERENCES store_shipments(id) ON DELETE CASCADE,
    provider_event_id VARCHAR(180),
    provider_status VARCHAR(120),
    normalized_status VARCHAR(40) NOT NULL,
    description VARCHAR(500),
    location VARCHAR(250),
    event_at TIMESTAMPTZ NOT NULL,
    raw_payload TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_store_shipment_tracking_event_status
        CHECK (normalized_status IN ('DRAFT','READY_TO_SHIP','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','EXCEPTION','CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_store_shipment_tracking_events_shipment_event
    ON store_shipment_tracking_events(shipment_id, event_at DESC, id DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_store_shipment_tracking_provider_event
    ON store_shipment_tracking_events(shipment_id, provider_event_id)
    WHERE provider_event_id IS NOT NULL;
