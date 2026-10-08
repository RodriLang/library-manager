CREATE TABLE store_orders (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    store_id BIGINT NOT NULL REFERENCES bookstore_stores(id),
    bookstore_id BIGINT NOT NULL REFERENCES bookstores(id),
    client_request_id UUID NOT NULL,
    order_number VARCHAR(40) NOT NULL UNIQUE,
    tracking_token UUID NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL,
    payment_status VARCHAR(30) NOT NULL,
    fulfillment_status VARCHAR(40) NOT NULL,
    payment_method VARCHAR(40) NOT NULL,
    delivery_method VARCHAR(40) NOT NULL,
    customer_name VARCHAR(200) NOT NULL,
    customer_email VARCHAR(320) NOT NULL,
    customer_phone VARCHAR(80),
    notes TEXT,
    subtotal NUMERIC(14,2) NOT NULL,
    discount_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    shipping_cost NUMERIC(14,2) NOT NULL DEFAULT 0,
    total NUMERIC(14,2) NOT NULL,
    reservation_expires_at TIMESTAMPTZ,
    confirmed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    cancellation_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_store_order_client_request UNIQUE (store_id, client_request_id)
);

CREATE INDEX idx_store_orders_bookstore_created ON store_orders(bookstore_id, created_at DESC);
CREATE INDEX idx_store_orders_store_created ON store_orders(store_id, created_at DESC);
CREATE INDEX idx_store_orders_status ON store_orders(bookstore_id, status, created_at DESC);
CREATE INDEX idx_store_orders_customer_email ON store_orders(bookstore_id, LOWER(customer_email));

CREATE TABLE store_order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES store_orders(id) ON DELETE CASCADE,
    inventory_id BIGINT NOT NULL REFERENCES inventory(id),
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(14,2) NOT NULL,
    subtotal NUMERIC(14,2) NOT NULL,
    title VARCHAR(500) NOT NULL,
    isbn VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_store_order_item_quantity_positive CHECK (quantity > 0),
    CONSTRAINT uq_store_order_inventory UNIQUE (order_id, inventory_id)
);

CREATE INDEX idx_store_order_items_order ON store_order_items(order_id);
CREATE INDEX idx_store_order_items_inventory ON store_order_items(inventory_id);

CREATE TABLE store_stock_reservations (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES store_orders(id) ON DELETE CASCADE,
    order_item_id BIGINT NOT NULL REFERENCES store_order_items(id) ON DELETE CASCADE,
    inventory_id BIGINT NOT NULL REFERENCES inventory(id),
    quantity INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    expires_at TIMESTAMPTZ,
    released_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_store_reservation_quantity_positive CHECK (quantity > 0),
    CONSTRAINT uq_store_reservation_order_item UNIQUE (order_item_id)
);

CREATE INDEX idx_store_reservations_inventory_active
    ON store_stock_reservations(inventory_id, status, expires_at);
CREATE INDEX idx_store_reservations_order ON store_stock_reservations(order_id);
