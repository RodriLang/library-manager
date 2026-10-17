ALTER TABLE bookstore_stores
    ADD COLUMN IF NOT EXISTS header_show_logo boolean NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS header_show_name boolean NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS header_brand_layout varchar(20) NOT NULL DEFAULT 'INLINE',
    ADD COLUMN IF NOT EXISTS header_logo_size varchar(20) NOT NULL DEFAULT 'COMPACT',
    ADD COLUMN IF NOT EXISTS hero_show_eyebrow boolean NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS hero_eyebrow varchar(120) NOT NULL DEFAULT 'LIBRERÍA ONLINE',
    ADD COLUMN IF NOT EXISTS hero_show_logo boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS hero_show_name boolean NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS hero_title varchar(220),
    ADD COLUMN IF NOT EXISTS hero_description text,
    ADD COLUMN IF NOT EXISTS hero_text_alignment varchar(20) NOT NULL DEFAULT 'LEFT',
    ADD COLUMN IF NOT EXISTS hero_show_books boolean NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS hero_books_mode varchar(30) NOT NULL DEFAULT 'FEATURED_FIXED',
    ADD COLUMN IF NOT EXISTS hero_primary_button_enabled boolean NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS hero_primary_button_text varchar(80) NOT NULL DEFAULT 'Explorar catálogo',
    ADD COLUMN IF NOT EXISTS hero_secondary_button_enabled boolean NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS hero_secondary_button_text varchar(80) NOT NULL DEFAULT 'Ver novedades',
    ADD COLUMN IF NOT EXISTS discover_enabled boolean NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS discover_eyebrow varchar(120) NOT NULL DEFAULT 'ENCONTRÁ TU PRÓXIMA LECTURA',
    ADD COLUMN IF NOT EXISTS discover_title varchar(220) NOT NULL DEFAULT 'Todo el catálogo en un solo lugar',
    ADD COLUMN IF NOT EXISTS discover_description text NOT NULL DEFAULT 'Buscá por título, autor, editorial o ISBN y filtrá por categorías, géneros, precio y disponibilidad.',
    ADD COLUMN IF NOT EXISTS discover_button_text varchar(80) NOT NULL DEFAULT 'Ver catálogo completo';

CREATE TABLE IF NOT EXISTS store_hero_items (
    id bigserial PRIMARY KEY,
    store_id bigint NOT NULL REFERENCES bookstore_stores(id) ON DELETE CASCADE,
    inventory_id bigint NOT NULL REFERENCES inventory(id) ON DELETE CASCADE,
    display_order integer NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_store_hero_item_inventory UNIQUE (store_id, inventory_id),
    CONSTRAINT uq_store_hero_item_order UNIQUE (store_id, display_order),
    CONSTRAINT ck_store_hero_item_order CHECK (display_order BETWEEN 0 AND 2)
);

CREATE INDEX IF NOT EXISTS idx_store_hero_items_store ON store_hero_items(store_id, display_order);
