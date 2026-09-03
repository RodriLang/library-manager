CREATE TABLE tiendanube_api_rate_limits
(
    tiendanube_store_id BIGINT PRIMARY KEY,
    remote_store_id     BIGINT      NOT NULL,
    limit_capacity      INTEGER,
    remaining           INTEGER,
    reset_after_ms      BIGINT,
    blocked_until       TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_tiendanube_api_rate_limits_store
        FOREIGN KEY (tiendanube_store_id) REFERENCES tiendanube_stores (id) ON DELETE CASCADE,

    CONSTRAINT ck_tiendanube_api_rate_limits_capacity
        CHECK (limit_capacity IS NULL OR limit_capacity > 0),
    CONSTRAINT ck_tiendanube_api_rate_limits_remaining
        CHECK (remaining IS NULL OR remaining >= 0),
    CONSTRAINT ck_tiendanube_api_rate_limits_reset
        CHECK (reset_after_ms IS NULL OR reset_after_ms >= 0)
);

CREATE INDEX idx_tiendanube_api_rate_limits_blocked_until
    ON tiendanube_api_rate_limits (blocked_until)
    WHERE blocked_until IS NOT NULL;

ALTER TABLE tiendanube_product_links
    ADD COLUMN pending_cover_url                TEXT,
    ADD COLUMN pending_cover_existing_image_ids TEXT,
    ADD COLUMN pending_cover_started_at         TIMESTAMPTZ;
