CREATE TABLE IF NOT EXISTS mercado_pago_platform_config (
    id BIGSERIAL PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    client_id VARCHAR(255),
    client_secret_encrypted TEXT,
    webhook_secret_encrypted TEXT,
    oauth_redirect_uri TEXT,
    public_api_base_url TEXT,
    frontend_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_mercado_pago_platform_config_singleton UNIQUE (id),
    CONSTRAINT chk_mercado_pago_platform_config_singleton CHECK (id = 1)
);
