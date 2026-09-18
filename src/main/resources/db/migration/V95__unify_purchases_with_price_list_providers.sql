ALTER TABLE price_list_providers
    ADD COLUMN IF NOT EXISTS tax_id VARCHAR(30),
    ADD COLUMN IF NOT EXISTS email VARCHAR(160),
    ADD COLUMN IF NOT EXISTS phone VARCHAR(50),
    ADD COLUMN IF NOT EXISTS notes TEXT;

-- Preserve metadata from V94 suppliers when a provider with the same business name already exists.
WITH supplier_data AS (
    SELECT DISTINCT ON (LOWER(TRIM(name)))
           LOWER(TRIM(name)) AS normalized_name,
           tax_id,
           email,
           phone,
           notes
    FROM suppliers
    ORDER BY LOWER(TRIM(name)), id
)
UPDATE price_list_providers provider
SET tax_id = COALESCE(provider.tax_id, supplier_data.tax_id),
    email = COALESCE(provider.email, supplier_data.email),
    phone = COALESCE(provider.phone, supplier_data.phone),
    notes = COALESCE(provider.notes, supplier_data.notes)
FROM supplier_data
WHERE LOWER(TRIM(provider.name)) = supplier_data.normalized_name;

-- A V94 supplier may have been created before this correction and not exist yet
-- in the shared provider catalog. Promote it to the canonical provider table.
WITH missing_suppliers AS (
    SELECT DISTINCT ON (LOWER(TRIM(supplier.name)))
           supplier.id,
           supplier.name,
           supplier.active,
           supplier.tax_id,
           supplier.email,
           supplier.phone,
           supplier.notes,
           supplier.created_at,
           supplier.updated_at
    FROM suppliers supplier
    WHERE NOT EXISTS (
        SELECT 1
        FROM price_list_providers provider
        WHERE LOWER(TRIM(provider.name)) = LOWER(TRIM(supplier.name))
    )
    ORDER BY LOWER(TRIM(supplier.name)), supplier.id
)
INSERT INTO price_list_providers (
    code,
    name,
    active,
    tax_id,
    email,
    phone,
    notes,
    created_at,
    updated_at
)
SELECT 'MIGRATED_SUPPLIER_' || id,
       name,
       active,
       tax_id,
       email,
       phone,
       notes,
       created_at,
       updated_at
FROM missing_suppliers;

ALTER TABLE purchases
    ADD COLUMN provider_id BIGINT;

UPDATE purchases purchase
SET provider_id = provider.id
FROM suppliers supplier
JOIN LATERAL (
    SELECT candidate.id
    FROM price_list_providers candidate
    WHERE LOWER(TRIM(candidate.name)) = LOWER(TRIM(supplier.name))
    ORDER BY candidate.id
    LIMIT 1
) provider ON TRUE
WHERE purchase.supplier_id = supplier.id;

ALTER TABLE purchases
    ALTER COLUMN provider_id SET NOT NULL,
    ADD CONSTRAINT fk_purchases_provider
        FOREIGN KEY (provider_id) REFERENCES price_list_providers(id);

DROP INDEX IF EXISTS idx_purchases_supplier;
CREATE INDEX idx_purchases_provider ON purchases(provider_id);

ALTER TABLE book_supplier_terms
    ADD COLUMN bookstore_id BIGINT,
    ADD COLUMN provider_id BIGINT;

UPDATE book_supplier_terms term
SET bookstore_id = supplier.bookstore_id,
    provider_id = provider.id
FROM suppliers supplier
JOIN LATERAL (
    SELECT candidate.id
    FROM price_list_providers candidate
    WHERE LOWER(TRIM(candidate.name)) = LOWER(TRIM(supplier.name))
    ORDER BY candidate.id
    LIMIT 1
) provider ON TRUE
WHERE term.supplier_id = supplier.id;

ALTER TABLE book_supplier_terms
    ALTER COLUMN bookstore_id SET NOT NULL,
    ALTER COLUMN provider_id SET NOT NULL,
    ADD CONSTRAINT fk_book_provider_terms_bookstore
        FOREIGN KEY (bookstore_id) REFERENCES bookstores(id),
    ADD CONSTRAINT fk_book_provider_terms_provider
        FOREIGN KEY (provider_id) REFERENCES price_list_providers(id);

ALTER TABLE book_supplier_terms
    DROP CONSTRAINT IF EXISTS uk_book_supplier_terms;

DROP INDEX IF EXISTS idx_book_supplier_terms_book;

ALTER TABLE book_supplier_terms
    DROP COLUMN supplier_id;

ALTER TABLE book_supplier_terms
    RENAME TO bookstore_provider_book_terms;

ALTER TABLE bookstore_provider_book_terms
    ADD CONSTRAINT uk_bookstore_provider_book_terms
        UNIQUE (bookstore_id, provider_id, book_id);

CREATE INDEX idx_bookstore_provider_book_terms_provider
    ON bookstore_provider_book_terms(bookstore_id, provider_id);

CREATE INDEX idx_bookstore_provider_book_terms_book
    ON bookstore_provider_book_terms(book_id);

ALTER TABLE purchases
    DROP COLUMN supplier_id;

DROP TABLE suppliers;
