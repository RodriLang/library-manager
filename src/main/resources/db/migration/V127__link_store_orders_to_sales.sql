ALTER TABLE store_orders
    ADD COLUMN sale_id BIGINT REFERENCES sales(id),
    ADD COLUMN completed_at TIMESTAMPTZ;

CREATE UNIQUE INDEX uq_store_orders_sale_id
    ON store_orders(sale_id)
    WHERE sale_id IS NOT NULL;
