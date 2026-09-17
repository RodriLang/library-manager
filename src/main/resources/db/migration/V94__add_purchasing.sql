CREATE TABLE suppliers (
    id BIGSERIAL PRIMARY KEY,
    bookstore_id BIGINT NOT NULL REFERENCES bookstores(id),
    name VARCHAR(160) NOT NULL,
    tax_id VARCHAR(30),
    email VARCHAR(160),
    phone VARCHAR(50),
    notes TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_suppliers_bookstore_name UNIQUE (bookstore_id, name)
);

CREATE INDEX idx_suppliers_bookstore ON suppliers(bookstore_id);

CREATE TABLE book_supplier_terms (
    id BIGSERIAL PRIMARY KEY,
    supplier_id BIGINT NOT NULL REFERENCES suppliers(id),
    book_id BIGINT NOT NULL REFERENCES books(id),
    discount_percentage NUMERIC(5,2),
    last_purchase_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_book_supplier_terms UNIQUE (supplier_id, book_id),
    CONSTRAINT ck_book_supplier_terms_discount CHECK (
        discount_percentage IS NULL OR (discount_percentage >= 0 AND discount_percentage <= 100)
    )
);

CREATE INDEX idx_book_supplier_terms_book ON book_supplier_terms(book_id);

CREATE TABLE purchases (
    id BIGSERIAL PRIMARY KEY,
    bookstore_id BIGINT NOT NULL REFERENCES bookstores(id),
    supplier_id BIGINT NOT NULL REFERENCES suppliers(id),
    purchase_date DATE NOT NULL,
    document_number VARCHAR(80),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    total_amount NUMERIC(16,2) NOT NULL DEFAULT 0,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_purchases_total CHECK (total_amount >= 0)
);

CREATE INDEX idx_purchases_bookstore_date ON purchases(bookstore_id, purchase_date);
CREATE INDEX idx_purchases_supplier ON purchases(supplier_id);

CREATE TABLE purchase_items (
    id BIGSERIAL PRIMARY KEY,
    purchase_id BIGINT NOT NULL REFERENCES purchases(id) ON DELETE CASCADE,
    book_id BIGINT NOT NULL REFERENCES books(id),
    condition VARCHAR(20) NOT NULL DEFAULT 'NEW',
    quantity INTEGER NOT NULL,
    editorial_price_snapshot NUMERIC(14,2),
    discount_percentage NUMERIC(5,2),
    unit_cost NUMERIC(14,2),
    total_cost NUMERIC(16,2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_purchase_item_book_condition UNIQUE (purchase_id, book_id, condition),
    CONSTRAINT ck_purchase_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_purchase_items_unit_cost CHECK (unit_cost IS NULL OR unit_cost > 0),
    CONSTRAINT ck_purchase_items_total_cost CHECK (total_cost IS NULL OR total_cost > 0),
    CONSTRAINT ck_purchase_items_discount CHECK (
        discount_percentage IS NULL OR (discount_percentage >= 0 AND discount_percentage <= 100)
    )
);

CREATE INDEX idx_purchase_items_book ON purchase_items(book_id);

ALTER TABLE inventory_cost_layers
    ADD COLUMN purchase_item_id BIGINT REFERENCES purchase_items(id);

CREATE INDEX idx_inventory_cost_layers_purchase_item
    ON inventory_cost_layers(purchase_item_id)
    WHERE purchase_item_id IS NOT NULL;
