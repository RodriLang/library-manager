CREATE TABLE store_mercado_pago_configs (
    id BIGSERIAL PRIMARY KEY,
    bookstore_id BIGINT NOT NULL UNIQUE REFERENCES bookstores(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    access_token_encrypted TEXT,
    webhook_secret_encrypted TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE store_orders
    ADD COLUMN payment_external_id VARCHAR(120),
    ADD COLUMN payment_checkout_url TEXT,
    ADD COLUMN payment_status_detail VARCHAR(120),
    ADD COLUMN payment_updated_at TIMESTAMPTZ;

CREATE UNIQUE INDEX uq_store_orders_payment_external_id
    ON store_orders(payment_external_id)
    WHERE payment_external_id IS NOT NULL;
