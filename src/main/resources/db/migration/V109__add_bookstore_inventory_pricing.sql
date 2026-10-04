ALTER TABLE inventory
    ALTER COLUMN sale_price DROP NOT NULL;

ALTER TABLE inventory
    ADD COLUMN IF NOT EXISTS last_price_checked_at DATE;

CREATE TABLE inventory_price_imports (
    id BIGSERIAL PRIMARY KEY,
    bookstore_id BIGINT NOT NULL,
    format_id BIGINT NULL,
    provider_id BIGINT NULL,
    original_filename VARCHAR(255) NOT NULL,
    effective_from DATE NOT NULL,
    status VARCHAR(30) NOT NULL,
    normalized_file_public_id VARCHAR(255),
    normalized_file_url TEXT,
    total_rows INTEGER NOT NULL DEFAULT 0,
    matched_rows INTEGER NOT NULL DEFAULT 0,
    unmatched_rows INTEGER NOT NULL DEFAULT 0,
    new_price_rows INTEGER NOT NULL DEFAULT 0,
    increase_rows INTEGER NOT NULL DEFAULT 0,
    decrease_rows INTEGER NOT NULL DEFAULT 0,
    unchanged_rows INTEGER NOT NULL DEFAULT 0,
    conflict_rows INTEGER NOT NULL DEFAULT 0,
    review_rows INTEGER NOT NULL DEFAULT 0,
    applied_rows INTEGER NOT NULL DEFAULT 0,
    created_by_user_id BIGINT,
    applied_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_inventory_price_import_bookstore FOREIGN KEY (bookstore_id) REFERENCES bookstores(id),
    CONSTRAINT fk_inventory_price_import_provider FOREIGN KEY (provider_id) REFERENCES providers(id),
    CONSTRAINT fk_inventory_price_import_user FOREIGN KEY (created_by_user_id) REFERENCES users(id)
);

CREATE TABLE bookstore_price_list_formats (
    id BIGSERIAL PRIMARY KEY,
    bookstore_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    standard BOOLEAN NOT NULL DEFAULT FALSE,
    sheet_index INTEGER NOT NULL DEFAULT 0,
    first_data_row_index INTEGER NOT NULL DEFAULT 1,
    isbn_column INTEGER,
    title_column INTEGER,
    author_column INTEGER,
    publisher_column INTEGER,
    price_column INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_bookstore_price_list_format_bookstore FOREIGN KEY (bookstore_id) REFERENCES bookstores(id),
    CONSTRAINT uk_bookstore_price_list_format_name UNIQUE (bookstore_id, name)
);

ALTER TABLE inventory_price_imports
    ADD CONSTRAINT fk_inventory_price_import_format
        FOREIGN KEY (format_id) REFERENCES bookstore_price_list_formats(id);

CREATE TABLE inventory_prices (
    id BIGSERIAL PRIMARY KEY,
    inventory_id BIGINT NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    effective_from DATE NOT NULL,
    source VARCHAR(40) NOT NULL,
    price_import_id BIGINT,
    created_by_user_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_inventory_price_inventory FOREIGN KEY (inventory_id) REFERENCES inventory(id) ON DELETE CASCADE,
    CONSTRAINT fk_inventory_price_import FOREIGN KEY (price_import_id) REFERENCES inventory_price_imports(id) ON DELETE SET NULL,
    CONSTRAINT fk_inventory_price_user FOREIGN KEY (created_by_user_id) REFERENCES users(id),
    CONSTRAINT uk_inventory_price_effective_from UNIQUE (inventory_id, effective_from),
    CONSTRAINT ck_inventory_price_positive CHECK (amount > 0)
);

CREATE TABLE inventory_price_import_items (
    id BIGSERIAL PRIMARY KEY,
    import_id BIGINT NOT NULL,
    inventory_id BIGINT,
    row_number INTEGER NOT NULL,
    isbn VARCHAR(32),
    title VARCHAR(500),
    author VARCHAR(500),
    incoming_price NUMERIC(12,2),
    current_price NUMERIC(12,2),
    existing_scheduled_price NUMERIC(12,2),
    change_percent NUMERIC(12,2),
    classification VARCHAR(40) NOT NULL,
    conflict_reason TEXT,
    selected_default BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_inventory_price_import_item_import FOREIGN KEY (import_id) REFERENCES inventory_price_imports(id) ON DELETE CASCADE,
    CONSTRAINT fk_inventory_price_import_item_inventory FOREIGN KEY (inventory_id) REFERENCES inventory(id) ON DELETE SET NULL,
    CONSTRAINT uk_inventory_price_import_item_row UNIQUE (import_id, row_number)
);

CREATE INDEX idx_inventory_prices_current
    ON inventory_prices (inventory_id, effective_from DESC);

CREATE INDEX idx_inventory_prices_future
    ON inventory_prices (effective_from, inventory_id);

CREATE INDEX idx_inventory_price_imports_bookstore_created
    ON inventory_price_imports (bookstore_id, created_at DESC);

CREATE INDEX idx_inventory_price_import_items_import_classification
    ON inventory_price_import_items (import_id, classification);

CREATE INDEX idx_bookstore_price_list_formats_bookstore_active
    ON bookstore_price_list_formats (bookstore_id, active);

INSERT INTO inventory_prices (
    inventory_id,
    amount,
    effective_from,
    source,
    created_at,
    updated_at
)
SELECT
    i.id,
    i.sale_price,
    (CURRENT_TIMESTAMP AT TIME ZONE 'America/Argentina/Buenos_Aires')::date,
    'LEGACY_MIGRATION',
    NOW(),
    NOW()
FROM inventory i
WHERE i.sale_price IS NOT NULL
  AND i.sale_price > 0
ON CONFLICT (inventory_id, effective_from) DO NOTHING;

-- Preserve already-known future editorial prices only for inventories whose
-- owner had explicitly enabled the legacy automatic editorial-price sync.
-- From this migration onward those values become local scheduled prices and
-- no longer remain linked to the global editorial-price subsystem.
INSERT INTO inventory_prices (
    inventory_id,
    amount,
    effective_from,
    source,
    created_at,
    updated_at
)
SELECT
    i.id,
    eep.price,
    eep.valid_from,
    'LEGACY_MIGRATION',
    NOW(),
    NOW()
FROM inventory i
JOIN effective_editorial_prices eep
  ON eep.book_id = i.book_id
 AND eep.active = TRUE
 AND eep.valid_from > (CURRENT_TIMESTAMP AT TIME ZONE 'America/Argentina/Buenos_Aires')::date
WHERE i.editorial_price_sync_enabled = TRUE
  AND eep.price > 0
ON CONFLICT (inventory_id, effective_from) DO NOTHING;

UPDATE inventory
SET editorial_price_sync_enabled = FALSE
WHERE editorial_price_sync_enabled = TRUE;

UPDATE inventory
SET last_price_checked_at = (CURRENT_TIMESTAMP AT TIME ZONE 'America/Argentina/Buenos_Aires')::date
WHERE sale_price IS NOT NULL
  AND last_price_checked_at IS NULL;

UPDATE inventory_count_items
SET status = 'RESOLVED'
WHERE status = 'PENDING_PRICE'
  AND book_id IS NOT NULL;

UPDATE inventory_count_sessions
SET default_editorial_price_sync_enabled = FALSE
WHERE default_editorial_price_sync_enabled = TRUE;
