-- Categorías comerciales exclusivas de cada librería; sin alterar la clasificación bibliográfica.
CREATE TABLE bookstore_commercial_categories (
    id BIGSERIAL PRIMARY KEY,
    bookstore_id BIGINT NOT NULL REFERENCES bookstores(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    parent_id BIGINT,
    tiendanube_category_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (bookstore_id, tiendanube_category_id),
    UNIQUE (id, bookstore_id),
    FOREIGN KEY (parent_id, bookstore_id) REFERENCES bookstore_commercial_categories(id, bookstore_id) ON DELETE RESTRICT,
    CONSTRAINT commercial_category_remote_positive CHECK (tiendanube_category_id IS NULL OR tiendanube_category_id > 0)
);
CREATE INDEX idx_commercial_category_parent ON bookstore_commercial_categories(bookstore_id, parent_id);
CREATE TABLE inventory_commercial_categories (
    bookstore_id BIGINT NOT NULL REFERENCES bookstores(id) ON DELETE CASCADE,
    inventory_id BIGINT NOT NULL REFERENCES inventory(id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL,
    PRIMARY KEY (inventory_id, category_id),
    FOREIGN KEY (category_id, bookstore_id) REFERENCES bookstore_commercial_categories(id, bookstore_id) ON DELETE CASCADE
);
CREATE INDEX idx_inventory_commercial_categories_bookstore ON inventory_commercial_categories(bookstore_id, inventory_id);
