ALTER TABLE store_shipments
    ADD COLUMN IF NOT EXISTS package_sequence integer NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS notes text;

CREATE TABLE IF NOT EXISTS store_shipment_items (
    id bigserial PRIMARY KEY,
    shipment_id bigint NOT NULL REFERENCES store_shipments(id) ON DELETE CASCADE,
    order_item_id bigint NOT NULL REFERENCES store_order_items(id) ON DELETE RESTRICT,
    quantity integer NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_store_shipment_items_quantity CHECK (quantity > 0),
    CONSTRAINT uq_store_shipment_item UNIQUE (shipment_id, order_item_id)
);
CREATE INDEX IF NOT EXISTS idx_store_shipment_items_order_item ON store_shipment_items(order_item_id);

CREATE TABLE IF NOT EXISTS store_returns (
    id bigserial PRIMARY KEY,
    bookstore_id bigint NOT NULL REFERENCES bookstores(id) ON DELETE CASCADE,
    order_id bigint NOT NULL REFERENCES store_orders(id) ON DELETE CASCADE,
    shipment_id bigint REFERENCES store_shipments(id) ON DELETE SET NULL,
    status varchar(40) NOT NULL,
    reason varchar(120),
    notes text,
    provider varchar(40),
    provider_return_id varchar(160),
    tracking_number varchar(160),
    tracking_url text,
    requested_at timestamptz NOT NULL DEFAULT now(),
    approved_at timestamptz,
    received_at timestamptz,
    completed_at timestamptz,
    cancelled_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_store_returns_status CHECK (status IN ('REQUESTED','APPROVED','IN_TRANSIT','RECEIVED','COMPLETED','REJECTED','CANCELLED'))
);
CREATE INDEX IF NOT EXISTS idx_store_returns_order ON store_returns(order_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_store_returns_bookstore_status ON store_returns(bookstore_id, status);

CREATE TABLE IF NOT EXISTS store_return_items (
    id bigserial PRIMARY KEY,
    return_id bigint NOT NULL REFERENCES store_returns(id) ON DELETE CASCADE,
    order_item_id bigint NOT NULL REFERENCES store_order_items(id) ON DELETE RESTRICT,
    quantity integer NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_store_return_items_quantity CHECK (quantity > 0),
    CONSTRAINT uq_store_return_item UNIQUE (return_id, order_item_id)
);
CREATE INDEX IF NOT EXISTS idx_store_return_items_order_item ON store_return_items(order_item_id);
