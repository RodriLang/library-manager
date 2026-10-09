ALTER TABLE store_mercado_pago_configs
    ADD COLUMN IF NOT EXISTS refresh_token_encrypted TEXT,
    ADD COLUMN IF NOT EXISTS mercado_pago_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS public_key VARCHAR(255),
    ADD COLUMN IF NOT EXISTS token_type VARCHAR(40),
    ADD COLUMN IF NOT EXISTS scope TEXT,
    ADD COLUMN IF NOT EXISTS token_expires_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS connected_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS disconnected_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS connection_error TEXT;

-- Las configuraciones manuales anteriores no tienen refresh token OAuth.
-- Se deshabilitan para forzar una vinculación explícita y renovable.
UPDATE store_mercado_pago_configs
SET enabled = FALSE
WHERE refresh_token_encrypted IS NULL;

ALTER TABLE store_mercado_pago_configs
    DROP COLUMN IF EXISTS webhook_secret_encrypted;

CREATE TABLE store_mercado_pago_oauth_states (
    id BIGSERIAL PRIMARY KEY,
    bookstore_id BIGINT NOT NULL REFERENCES bookstores(id) ON DELETE CASCADE,
    state_hash VARCHAR(64) NOT NULL UNIQUE,
    code_verifier_encrypted TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_store_mp_oauth_states_bookstore
    ON store_mercado_pago_oauth_states(bookstore_id);

CREATE INDEX idx_store_mp_oauth_states_expires
    ON store_mercado_pago_oauth_states(expires_at);
