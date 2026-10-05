-- Recupera la importación administrativa de catálogo sin reintroducir precios editoriales globales.

ALTER TABLE books
    ADD COLUMN IF NOT EXISTS collection_name VARCHAR(255);

ALTER TABLE provider_books
    ADD COLUMN IF NOT EXISTS first_seen_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS source VARCHAR(30) NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN IF NOT EXISTS verification_status VARCHAR(20) NOT NULL DEFAULT 'VERIFIED';

UPDATE provider_books
SET first_seen_at = COALESCE(first_seen_at, created_at, last_seen_at, NOW())
WHERE first_seen_at IS NULL;

ALTER TABLE provider_books
    ADD CONSTRAINT chk_provider_books_source
        CHECK (source IN ('LEGACY', 'ADMIN_IMPORT', 'BOOKSTORE_IMPORT'));

ALTER TABLE provider_books
    ADD CONSTRAINT chk_provider_books_verification_status
        CHECK (verification_status IN ('VERIFIED', 'OBSERVED'));

CREATE TABLE bookstore_providers (
    id BIGSERIAL PRIMARY KEY,
    bookstore_id BIGINT NOT NULL REFERENCES bookstores(id) ON DELETE CASCADE,
    provider_id BIGINT NOT NULL REFERENCES providers(id) ON DELETE CASCADE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    preferred BOOLEAN NOT NULL DEFAULT FALSE,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_bookstore_providers UNIQUE (bookstore_id, provider_id)
);

CREATE INDEX idx_bookstore_providers_bookstore ON bookstore_providers(bookstore_id);
CREATE INDEX idx_bookstore_providers_provider ON bookstore_providers(provider_id);

CREATE TABLE catalog_import_formats (
    id BIGSERIAL PRIMARY KEY,
    provider_id BIGINT NOT NULL REFERENCES providers(id),
    name VARCHAR(150) NOT NULL,
    sheet_strategy VARCHAR(30) NOT NULL,
    sheet_index INTEGER,
    sheet_name VARCHAR(150),
    header_strategy VARCHAR(30) NOT NULL,
    header_row_index INTEGER,
    first_data_row_index INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_catalog_import_formats_provider_name UNIQUE(provider_id, name)
);

CREATE TABLE catalog_import_format_mappings (
    format_id BIGINT NOT NULL REFERENCES catalog_import_formats(id) ON DELETE CASCADE,
    mapping_order INTEGER NOT NULL,
    target_field VARCHAR(40) NOT NULL,
    column_index INTEGER NOT NULL,
    expected_header VARCHAR(200),
    value_type VARCHAR(20) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (format_id, mapping_order)
);

CREATE TABLE catalog_import_jobs (
    id BIGSERIAL PRIMARY KEY,
    provider_id BIGINT NOT NULL REFERENCES providers(id),
    format_id BIGINT NOT NULL REFERENCES catalog_import_formats(id),
    requested_by_user_id BIGINT,
    original_filename VARCHAR(255),
    temporary_file_path VARCHAR(1000),
    status VARCHAR(30) NOT NULL,
    phase VARCHAR(30) NOT NULL,
    total_rows INTEGER NOT NULL DEFAULT 0,
    processed_rows INTEGER NOT NULL DEFAULT 0,
    created_books INTEGER NOT NULL DEFAULT 0,
    enriched_books INTEGER NOT NULL DEFAULT 0,
    unchanged_books INTEGER NOT NULL DEFAULT 0,
    conflicted_books INTEGER NOT NULL DEFAULT 0,
    skipped_rows INTEGER NOT NULL DEFAULT 0,
    error_count INTEGER NOT NULL DEFAULT 0,
    provider_links_created INTEGER NOT NULL DEFAULT 0,
    provider_links_updated INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    failure_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_catalog_import_jobs_provider_created ON catalog_import_jobs(provider_id, created_at DESC);
CREATE INDEX idx_catalog_import_jobs_status ON catalog_import_jobs(status);

CREATE TABLE catalog_import_job_errors (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL REFERENCES catalog_import_jobs(id) ON DELETE CASCADE,
    row_number INTEGER,
    isbn VARCHAR(32),
    title VARCHAR(500),
    error_type VARCHAR(40) NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_catalog_import_job_errors_job ON catalog_import_job_errors(job_id);

-- Una configuración local puede asociarse opcionalmente a un proveedor global.
-- El precio continúa siendo privado de la librería; esta relación sólo permite
-- registrar observaciones de catálogo compartido sin duplicar proveedores.
ALTER TABLE bookstore_price_list_formats
    ADD COLUMN IF NOT EXISTS provider_id BIGINT REFERENCES providers(id);

CREATE INDEX IF NOT EXISTS idx_bookstore_price_list_formats_provider
    ON bookstore_price_list_formats(provider_id);
