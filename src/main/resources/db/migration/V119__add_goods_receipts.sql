ALTER TABLE purchase_orders
    DROP CONSTRAINT IF EXISTS chk_purchase_orders_status;

ALTER TABLE purchase_orders
    ADD CONSTRAINT chk_purchase_orders_status
        CHECK (status IN (
                          'DRAFT',
                          'SENT',
                          'PARTIALLY_RECEIVED',
                          'RECEIVED',
                          'CLOSED_INCOMPLETE',
                          'CANCELLED'
            ));

ALTER TABLE purchase_order_items
    ADD COLUMN IF NOT EXISTS received_quantity INTEGER NOT NULL DEFAULT 0;

ALTER TABLE purchase_order_items
    DROP CONSTRAINT IF EXISTS chk_purchase_order_items_received_quantity;

ALTER TABLE purchase_order_items
    ADD CONSTRAINT chk_purchase_order_items_received_quantity
        CHECK (received_quantity >= 0);

ALTER TABLE purchase_order_items
    DROP CONSTRAINT IF EXISTS chk_purchase_order_items_received_not_over_quantity;

ALTER TABLE purchase_order_items
    ADD CONSTRAINT chk_purchase_order_items_received_not_over_quantity
        CHECK (received_quantity <= quantity);

CREATE TABLE IF NOT EXISTS goods_receipts
(
    id                BIGSERIAL PRIMARY KEY,
    bookstore_id      BIGINT      NOT NULL REFERENCES bookstores (id),
    provider_id       BIGINT REFERENCES providers (id),
    purchase_order_id BIGINT REFERENCES purchase_orders (id),
    receipt_number    VARCHAR(50) NOT NULL,
    source            VARCHAR(20) NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    document_type     VARCHAR(30),
    document_number   VARCHAR(100),
    notes             VARCHAR(1000),
    confirmed_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_goods_receipts_bookstore_number UNIQUE (bookstore_id, receipt_number),
    CONSTRAINT chk_goods_receipts_source CHECK (source IN ('ORDER', 'DOCUMENT', 'FREE')),
    CONSTRAINT chk_goods_receipts_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'CANCELLED')),
    CONSTRAINT chk_goods_receipts_order_source CHECK (source <> 'ORDER' OR purchase_order_id IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_goods_receipts_bookstore_status
    ON goods_receipts (bookstore_id, status);
CREATE INDEX IF NOT EXISTS idx_goods_receipts_order
    ON goods_receipts (purchase_order_id);
CREATE INDEX IF NOT EXISTS idx_goods_receipts_provider
    ON goods_receipts (provider_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_goods_receipts_open_order
    ON goods_receipts (purchase_order_id)
    WHERE purchase_order_id IS NOT NULL AND status = 'DRAFT';

CREATE TABLE IF NOT EXISTS goods_receipt_items
(
    id                     BIGSERIAL PRIMARY KEY,
    goods_receipt_id       BIGINT      NOT NULL REFERENCES goods_receipts (id) ON DELETE CASCADE,
    book_id                BIGINT      NOT NULL REFERENCES books (id),
    purchase_order_item_id BIGINT REFERENCES purchase_order_items (id),
    condition              VARCHAR(20) NOT NULL DEFAULT 'NEW',
    expected_quantity      INTEGER,
    document_quantity      INTEGER,
    scanned_quantity       INTEGER     NOT NULL DEFAULT 0,
    received_quantity      INTEGER     NOT NULL DEFAULT 0,
    notes                  VARCHAR(500),
    created_at             TIMESTAMPTZ NOT NULL,
    updated_at             TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_goods_receipt_items_book_condition UNIQUE (goods_receipt_id, book_id, condition),
    CONSTRAINT chk_goods_receipt_items_expected CHECK (expected_quantity IS NULL OR expected_quantity >= 0),
    CONSTRAINT chk_goods_receipt_items_document CHECK (document_quantity IS NULL OR document_quantity >= 0),
    CONSTRAINT chk_goods_receipt_items_scanned CHECK (scanned_quantity >= 0),
    CONSTRAINT chk_goods_receipt_items_received CHECK (received_quantity >= 0)
);

CREATE INDEX IF NOT EXISTS idx_goods_receipt_items_receipt ON goods_receipt_items (goods_receipt_id);
CREATE INDEX IF NOT EXISTS idx_goods_receipt_items_order_item ON goods_receipt_items (purchase_order_item_id);
CREATE INDEX IF NOT EXISTS idx_goods_receipt_items_book ON goods_receipt_items (book_id);
