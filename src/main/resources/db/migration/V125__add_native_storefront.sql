CREATE TABLE bookstore_sales_channels (
    id BIGSERIAL PRIMARY KEY,
    bookstore_id BIGINT NOT NULL REFERENCES bookstores(id) ON DELETE CASCADE,
    channel VARCHAR(40) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_bookstore_sales_channel UNIQUE (bookstore_id, channel),
    CONSTRAINT ck_bookstore_sales_channel CHECK (channel IN ('ANAQUEL_STORE', 'TIENDANUBE'))
);

INSERT INTO bookstore_sales_channels (bookstore_id, channel, enabled)
SELECT b.id, 'ANAQUEL_STORE', FALSE
FROM bookstores b
ON CONFLICT (bookstore_id, channel) DO NOTHING;

INSERT INTO bookstore_sales_channels (bookstore_id, channel, enabled)
SELECT b.id, 'TIENDANUBE', EXISTS (
    SELECT 1 FROM tiendanube_stores ts WHERE ts.bookstore_id = b.id AND ts.active = TRUE
)
FROM bookstores b
ON CONFLICT (bookstore_id, channel) DO NOTHING;

CREATE TABLE bookstore_stores (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    bookstore_id BIGINT NOT NULL UNIQUE REFERENCES bookstores(id) ON DELETE CASCADE,
    slug VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(150) NOT NULL,
    title_format VARCHAR(40) NOT NULL DEFAULT 'TITLE',
    show_isbn BOOLEAN NOT NULL DEFAULT TRUE,
    show_author BOOLEAN NOT NULL DEFAULT TRUE,
    show_publisher BOOLEAN NOT NULL DEFAULT TRUE,
    show_stock BOOLEAN NOT NULL DEFAULT FALSE,
    logo_url VARCHAR(1000),
    favicon_url VARCHAR(1000),
    primary_color VARCHAR(20),
    secondary_color VARCHAR(20),
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_bookstore_store_title_format CHECK (title_format IN ('TITLE', 'TITLE_AUTHOR', 'TITLE_PUBLISHER'))
);

CREATE TABLE store_domains (
    id BIGSERIAL PRIMARY KEY,
    store_id BIGINT NOT NULL REFERENCES bookstore_stores(id) ON DELETE CASCADE,
    hostname VARCHAR(255) NOT NULL UNIQUE,
    type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    verification_token VARCHAR(120),
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_store_domain_type CHECK (type IN ('ANAQUEL_SUBDOMAIN', 'CUSTOM')),
    CONSTRAINT ck_store_domain_status CHECK (status IN ('PENDING', 'VERIFIED', 'ACTIVE', 'FAILED'))
);

CREATE INDEX idx_store_domains_store ON store_domains(store_id);

CREATE TABLE store_publications (
    id BIGSERIAL PRIMARY KEY,
    store_id BIGINT NOT NULL REFERENCES bookstore_stores(id) ON DELETE CASCADE,
    inventory_id BIGINT NOT NULL REFERENCES inventory(id) ON DELETE CASCADE,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMPTZ,
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    featured_order INTEGER,
    custom_title VARCHAR(500),
    custom_description TEXT,
    seo_title VARCHAR(255),
    seo_description VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_store_publication_inventory UNIQUE (store_id, inventory_id)
);

CREATE INDEX idx_store_publications_store_published ON store_publications(store_id, published);
CREATE INDEX idx_store_publications_store_featured ON store_publications(store_id, featured, featured_order);
CREATE INDEX idx_store_publications_inventory ON store_publications(inventory_id);
