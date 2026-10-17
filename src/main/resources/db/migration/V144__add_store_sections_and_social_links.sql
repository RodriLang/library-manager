CREATE TABLE IF NOT EXISTS store_sections (
    id BIGSERIAL PRIMARY KEY,
    store_id BIGINT NOT NULL REFERENCES bookstore_stores(id) ON DELETE CASCADE,
    type VARCHAR(30) NOT NULL,
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(120) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INTEGER NOT NULL DEFAULT 0,
    window_days INTEGER,
    max_items INTEGER NOT NULL DEFAULT 8,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_store_section_slug UNIQUE (store_id, slug),
    CONSTRAINT ck_store_section_window_days CHECK (window_days IS NULL OR (window_days BETWEEN 1 AND 365)),
    CONSTRAINT ck_store_section_max_items CHECK (max_items BETWEEN 1 AND 50)
);

CREATE INDEX IF NOT EXISTS idx_store_sections_store_order ON store_sections(store_id, display_order, id);

CREATE TABLE IF NOT EXISTS store_section_items (
    id BIGSERIAL PRIMARY KEY,
    section_id BIGINT NOT NULL REFERENCES store_sections(id) ON DELETE CASCADE,
    inventory_id BIGINT NOT NULL REFERENCES inventory(id) ON DELETE CASCADE,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_store_section_item UNIQUE (section_id, inventory_id)
);

CREATE INDEX IF NOT EXISTS idx_store_section_items_section_order ON store_section_items(section_id, display_order, id);

CREATE TABLE IF NOT EXISTS store_social_links (
    id BIGSERIAL PRIMARY KEY,
    store_id BIGINT NOT NULL REFERENCES bookstore_stores(id) ON DELETE CASCADE,
    type VARCHAR(30) NOT NULL,
    url VARCHAR(1000) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_store_social_link_type UNIQUE (store_id, type)
);

CREATE INDEX IF NOT EXISTS idx_store_social_links_store_order ON store_social_links(store_id, display_order, id);

INSERT INTO store_sections (store_id, type, name, slug, enabled, display_order, window_days, max_items)
SELECT id, 'FEATURED', 'Destacados', 'destacados', TRUE, 10, NULL, 8
FROM bookstore_stores s
WHERE NOT EXISTS (SELECT 1 FROM store_sections x WHERE x.store_id = s.id AND x.type = 'FEATURED');

INSERT INTO store_sections (store_id, type, name, slug, enabled, display_order, window_days, max_items)
SELECT id, 'NEW_ARRIVALS', 'Novedades', 'novedades', TRUE, 20, 45, 8
FROM bookstore_stores s
WHERE NOT EXISTS (SELECT 1 FROM store_sections x WHERE x.store_id = s.id AND x.type = 'NEW_ARRIVALS');

INSERT INTO store_sections (store_id, type, name, slug, enabled, display_order, window_days, max_items)
SELECT id, 'RESTOCKED', 'Reingresos', 'reingresos', FALSE, 30, 30, 8
FROM bookstore_stores s
WHERE NOT EXISTS (SELECT 1 FROM store_sections x WHERE x.store_id = s.id AND x.type = 'RESTOCKED');

ALTER TABLE store_publications ADD COLUMN IF NOT EXISTS first_published_at TIMESTAMPTZ;
ALTER TABLE store_publications ADD COLUMN IF NOT EXISTS last_published_at TIMESTAMPTZ;
UPDATE store_publications
SET first_published_at = COALESCE(first_published_at, published_at),
    last_published_at = COALESCE(last_published_at, published_at)
WHERE published_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_store_publications_first_published ON store_publications(store_id, first_published_at DESC);
CREATE INDEX IF NOT EXISTS idx_store_publications_last_published ON store_publications(store_id, last_published_at DESC);
