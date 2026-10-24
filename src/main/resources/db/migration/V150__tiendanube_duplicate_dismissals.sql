CREATE TABLE IF NOT EXISTS tiendanube_duplicate_dismissals (
    id BIGSERIAL PRIMARY KEY,
    tiendanube_store_id BIGINT NOT NULL REFERENCES tiendanube_stores(store_id) ON DELETE CASCADE,
    product_id_low BIGINT NOT NULL,
    product_id_high BIGINT NOT NULL,
    dismissed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_tiendanube_duplicate_order CHECK (product_id_low < product_id_high),
    CONSTRAINT uq_tiendanube_duplicate_dismissal UNIQUE (tiendanube_store_id, product_id_low, product_id_high)
);
