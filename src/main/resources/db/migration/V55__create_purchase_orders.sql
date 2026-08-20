CREATE TABLE purchase_orders
(
    id           BIGSERIAL PRIMARY KEY,
    bookstore_id BIGINT      NOT NULL,
    provider_id  BIGINT      NOT NULL,
    order_number VARCHAR(50) NOT NULL,
    status       VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    sent_at      TIMESTAMPTZ,
    notes        VARCHAR(1000),
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_purchase_orders_bookstore
        FOREIGN KEY (bookstore_id)
            REFERENCES bookstores (id),

    CONSTRAINT fk_purchase_orders_provider
        FOREIGN KEY (provider_id)
            REFERENCES price_list_providers (id),

    CONSTRAINT chk_purchase_orders_status
        CHECK (
            status IN (
                       'DRAFT',
                       'SENT',
                       'CANCELLED'
                )
            )
);


CREATE UNIQUE INDEX uk_purchase_orders_bookstore_number
    ON purchase_orders (
                        bookstore_id,
                        order_number
        );


CREATE INDEX idx_purchase_orders_bookstore_status
    ON purchase_orders (
                        bookstore_id,
                        status
        );


CREATE INDEX idx_purchase_orders_provider
    ON purchase_orders (
                        provider_id
        );


CREATE TABLE purchase_order_items
(
    id                      BIGSERIAL PRIMARY KEY,
    purchase_order_id       BIGINT      NOT NULL,
    book_id                 BIGINT      NOT NULL,
    purchase_requirement_id BIGINT,
    quantity                INTEGER     NOT NULL,
    requirement_quantity    INTEGER     NOT NULL DEFAULT 0,
    unit_price              NUMERIC(12, 2),
    notes                   VARCHAR(500),
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_purchase_order_items_order
        FOREIGN KEY (purchase_order_id)
            REFERENCES purchase_orders (id),

    CONSTRAINT fk_purchase_order_items_book
        FOREIGN KEY (book_id)
            REFERENCES books (id),

    CONSTRAINT fk_purchase_order_items_requirement
        FOREIGN KEY (purchase_requirement_id)
            REFERENCES purchase_requirements (id),

    CONSTRAINT uk_purchase_order_items_order_book
        UNIQUE (
                purchase_order_id,
                book_id
            ),

    CONSTRAINT chk_purchase_order_items_quantity
        CHECK (quantity > 0),

    CONSTRAINT chk_purchase_order_items_requirement_quantity
        CHECK (
            requirement_quantity >= 0
                AND requirement_quantity <= quantity
            ),

    CONSTRAINT chk_purchase_order_items_unit_price
        CHECK (
            unit_price IS NULL
                OR unit_price >= 0
            )
);


CREATE INDEX idx_purchase_order_items_order
    ON purchase_order_items (
                             purchase_order_id
        );


CREATE INDEX idx_purchase_order_items_requirement
    ON purchase_order_items (
                             purchase_requirement_id
        );