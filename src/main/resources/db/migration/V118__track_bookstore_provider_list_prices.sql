-- La importación guarda una foto del proveedor elegido. No dependemos de que
-- el formato conserve para siempre la misma asociación.
ALTER TABLE inventory_price_imports
    ADD COLUMN IF NOT EXISTS provider_id BIGINT REFERENCES providers(id);

UPDATE inventory_price_imports price_import
SET provider_id = format.provider_id
FROM bookstore_price_list_formats format
WHERE price_import.format_id = format.id
  AND price_import.provider_id IS NULL
  AND format.provider_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_inventory_price_imports_bookstore_provider_created
    ON inventory_price_imports(bookstore_id, provider_id, created_at DESC);

-- Guarda sólo el último precio de lista conocido por librería/proveedor/libro.
-- No crea historial de precios del proveedor: el historial comercial de venta
-- continúa viviendo exclusivamente en inventory_prices una vez que el libro
-- forma parte del inventario de la librería.

ALTER TABLE bookstore_provider_book_terms
    ADD COLUMN IF NOT EXISTS latest_list_price NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS latest_list_effective_from DATE,
    ADD COLUMN IF NOT EXISTS last_seen_in_price_list_at DATE,
    ADD COLUMN IF NOT EXISTS last_price_import_id BIGINT;

ALTER TABLE bookstore_provider_book_terms
    ADD CONSTRAINT ck_bookstore_provider_book_terms_latest_list_price
        CHECK (latest_list_price IS NULL OR latest_list_price > 0),
    ADD CONSTRAINT fk_bookstore_provider_book_terms_last_price_import
        FOREIGN KEY (last_price_import_id)
        REFERENCES inventory_price_imports(id)
        ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_bookstore_provider_book_terms_seen
    ON bookstore_provider_book_terms(bookstore_id, provider_id, last_seen_in_price_list_at DESC);

-- Staging transitorio para poder diferir cualquier efecto sobre el catálogo
-- global y los precios del proveedor hasta que el usuario aplique la lista.
-- Las filas se eliminan al aplicar/cancelar/fallar la importación.
CREATE TABLE inventory_price_import_provider_rows (
    id BIGSERIAL PRIMARY KEY,
    import_id BIGINT NOT NULL REFERENCES inventory_price_imports(id) ON DELETE CASCADE,
    book_id BIGINT REFERENCES books(id) ON DELETE SET NULL,
    row_number INTEGER NOT NULL,
    isbn VARCHAR(32),
    title VARCHAR(500),
    author VARCHAR(500),
    publisher VARCHAR(500),
    incoming_price NUMERIC(12,2),
    external_code VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_inventory_price_import_provider_row UNIQUE (import_id, row_number)
);

CREATE INDEX idx_inventory_price_import_provider_rows_import
    ON inventory_price_import_provider_rows(import_id);
