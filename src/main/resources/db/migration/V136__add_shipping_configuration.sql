CREATE TABLE IF NOT EXISTS bookstore_shipping_settings (
    id BIGSERIAL PRIMARY KEY,
    bookstore_id BIGINT NOT NULL UNIQUE REFERENCES bookstores(id) ON DELETE CASCADE,
    origin_name VARCHAR(150),
    origin_contact_name VARCHAR(150),
    origin_email VARCHAR(150),
    origin_phone VARCHAR(50),
    origin_street VARCHAR(180),
    origin_street_number VARCHAR(40),
    origin_floor_apartment VARCHAR(80),
    origin_postal_code VARCHAR(20),
    origin_city VARCHAR(120),
    origin_state VARCHAR(120),
    origin_country_code VARCHAR(2) NOT NULL DEFAULT 'AR',
    preparation_days INTEGER NOT NULL DEFAULT 1,
    free_shipping_threshold NUMERIC(14,2),
    packaging_surcharge NUMERIC(14,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_shipping_preparation_days CHECK (preparation_days >= 0 AND preparation_days <= 30),
    CONSTRAINT ck_shipping_free_threshold CHECK (free_shipping_threshold IS NULL OR free_shipping_threshold >= 0),
    CONSTRAINT ck_shipping_packaging_surcharge CHECK (packaging_surcharge >= 0)
);

CREATE TABLE IF NOT EXISTS bookstore_shipping_integrations (
    id BIGSERIAL PRIMARY KEY,
    bookstore_id BIGINT NOT NULL REFERENCES bookstores(id) ON DELETE CASCADE,
    provider VARCHAR(40) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(30) NOT NULL DEFAULT 'NOT_CONFIGURED',
    credentials_encrypted TEXT,
    settings_json TEXT,
    last_error TEXT,
    configured_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_bookstore_shipping_integration UNIQUE (bookstore_id, provider),
    CONSTRAINT ck_bookstore_shipping_provider CHECK (provider IN ('ANDREANI','CORREO_ARGENTINO','OCA','MANUAL','PICKUP_IN_STORE')),
    CONSTRAINT ck_bookstore_shipping_status CHECK (status IN ('NOT_CONFIGURED','CONFIGURED','ERROR'))
);

CREATE INDEX IF NOT EXISTS idx_bookstore_shipping_integrations_enabled
    ON bookstore_shipping_integrations(bookstore_id, enabled);

CREATE TABLE IF NOT EXISTS bookstore_shipping_services (
    id BIGSERIAL PRIMARY KEY,
    bookstore_id BIGINT NOT NULL REFERENCES bookstores(id) ON DELETE CASCADE,
    provider VARCHAR(40) NOT NULL,
    service_code VARCHAR(100) NOT NULL,
    display_name VARCHAR(150) NOT NULL,
    delivery_type VARCHAR(30) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    settings_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_bookstore_shipping_service UNIQUE (bookstore_id, provider, service_code),
    CONSTRAINT ck_bookstore_shipping_service_provider CHECK (provider IN ('ANDREANI','CORREO_ARGENTINO','OCA','MANUAL','PICKUP_IN_STORE')),
    CONSTRAINT ck_bookstore_shipping_delivery_type CHECK (delivery_type IN ('HOME','BRANCH','PICKUP_POINT','STORE_PICKUP','CUSTOM')),
    CONSTRAINT ck_bookstore_shipping_service_sort CHECK (sort_order >= 0)
);

CREATE INDEX IF NOT EXISTS idx_bookstore_shipping_services_enabled
    ON bookstore_shipping_services(bookstore_id, provider, enabled, sort_order);
