-- Local, reversible decision. Does not delete or modify Tiendanube products.
CREATE TABLE tiendanube_ignored_publications (
    bookstore_id BIGINT NOT NULL REFERENCES bookstores(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL CHECK (product_id > 0),
    ignored_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (bookstore_id, product_id)
);
