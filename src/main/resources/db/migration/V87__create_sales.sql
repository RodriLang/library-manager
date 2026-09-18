-- =========================================================
-- SALES
-- =========================================================

CREATE TABLE sales
(
    id                     BIGSERIAL PRIMARY KEY,
    bookstore_id           BIGINT         NOT NULL,
    created_by_user_id     BIGINT         NOT NULL,
    status                 VARCHAR(30)    NOT NULL,
    origin                 VARCHAR(30)    NOT NULL,
    sold_at                TIMESTAMPTZ    NOT NULL,
    subtotal               NUMERIC(14, 2) NOT NULL,
    discount_amount        NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total                  NUMERIC(14, 2) NOT NULL,
    external_reference     VARCHAR(100),
    notes                  VARCHAR(1000),
    cancelled_at           TIMESTAMPTZ,
    cancelled_by_user_id   BIGINT,
    cancellation_reason    VARCHAR(500),
    created_at             TIMESTAMPTZ    NOT NULL,
    updated_at             TIMESTAMPTZ    NOT NULL,

    CONSTRAINT fk_sales_bookstore
        FOREIGN KEY (bookstore_id)
            REFERENCES bookstores (id),

    CONSTRAINT fk_sales_created_by_user
        FOREIGN KEY (created_by_user_id)
            REFERENCES users (id),

    CONSTRAINT fk_sales_cancelled_by_user
        FOREIGN KEY (cancelled_by_user_id)
            REFERENCES users (id),

    CONSTRAINT chk_sales_status
        CHECK (status IN ('COMPLETED', 'CANCELLED')),

    CONSTRAINT chk_sales_origin
        CHECK (origin IN ('MANUAL', 'TIENDANUBE')),

    CONSTRAINT chk_sales_amounts
        CHECK (
            subtotal >= 0
            AND discount_amount >= 0
            AND discount_amount <= subtotal
            AND total = subtotal - discount_amount
        ),

    CONSTRAINT chk_sales_cancellation
        CHECK (
            (status = 'COMPLETED' AND cancelled_at IS NULL)
            OR
            (status = 'CANCELLED' AND cancelled_at IS NOT NULL)
        )
);

CREATE INDEX idx_sales_bookstore_sold_at
    ON sales (bookstore_id, sold_at DESC);

CREATE INDEX idx_sales_bookstore_status
    ON sales (bookstore_id, status);

CREATE UNIQUE INDEX uk_sales_external_reference
    ON sales (bookstore_id, origin, external_reference)
    WHERE external_reference IS NOT NULL;


-- =========================================================
-- SALE ITEMS
-- =========================================================

CREATE TABLE sale_items
(
    id             BIGSERIAL PRIMARY KEY,
    sale_id        BIGINT         NOT NULL,
    inventory_id   BIGINT         NOT NULL,
    quantity       INTEGER        NOT NULL,
    unit_price     NUMERIC(14, 2) NOT NULL,
    subtotal       NUMERIC(14, 2) NOT NULL,
    description    VARCHAR(500)   NOT NULL,
    isbn           VARCHAR(20),
    created_at     TIMESTAMPTZ    NOT NULL,
    updated_at     TIMESTAMPTZ    NOT NULL,

    CONSTRAINT fk_sale_items_sale
        FOREIGN KEY (sale_id)
            REFERENCES sales (id),

    CONSTRAINT fk_sale_items_inventory
        FOREIGN KEY (inventory_id)
            REFERENCES inventory (id),

    CONSTRAINT uk_sale_items_sale_inventory
        UNIQUE (sale_id, inventory_id),

    CONSTRAINT chk_sale_items_quantity
        CHECK (quantity > 0),

    CONSTRAINT chk_sale_items_amounts
        CHECK (
            unit_price >= 0
            AND subtotal = unit_price * quantity
        )
);

CREATE INDEX idx_sale_items_sale
    ON sale_items (sale_id);

CREATE INDEX idx_sale_items_inventory
    ON sale_items (inventory_id);


-- =========================================================
-- SALE PAYMENTS
-- =========================================================

CREATE TABLE sale_payments
(
    id             BIGSERIAL PRIMARY KEY,
    sale_id        BIGINT         NOT NULL,
    method         VARCHAR(30)    NOT NULL,
    amount         NUMERIC(14, 2) NOT NULL,
    reference      VARCHAR(100),
    created_at     TIMESTAMPTZ    NOT NULL,
    updated_at     TIMESTAMPTZ    NOT NULL,

    CONSTRAINT fk_sale_payments_sale
        FOREIGN KEY (sale_id)
            REFERENCES sales (id),

    CONSTRAINT chk_sale_payments_method
        CHECK (
            method IN (
                'CASH',
                'DEBIT_CARD',
                'CREDIT_CARD',
                'TRANSFER',
                'DIGITAL_WALLET',
                'OTHER'
            )
        ),

    CONSTRAINT chk_sale_payments_amount
        CHECK (amount > 0)
);

CREATE INDEX idx_sale_payments_sale
    ON sale_payments (sale_id);


-- =========================================================
-- PURCHASE REQUIREMENT SOURCE FOR SALE ITEMS
-- =========================================================

ALTER TABLE purchase_requirement_sources
    DROP CONSTRAINT chk_purchase_requirement_sources_type;

ALTER TABLE purchase_requirement_sources
    ADD CONSTRAINT chk_purchase_requirement_sources_type
        CHECK (
            source_type IN (
                'SALE',
                'SALE_ITEM',
                'INVENTORY',
                'CATALOG',
                'LOW_STOCK',
                'MANUAL',
                'ADJUSTMENT',
                'REVERSAL'
            )
        );

CREATE UNIQUE INDEX uk_purchase_requirement_sources_sale_item_reference
    ON purchase_requirement_sources (reference_id)
    WHERE source_type = 'SALE_ITEM'
      AND reference_id IS NOT NULL;
