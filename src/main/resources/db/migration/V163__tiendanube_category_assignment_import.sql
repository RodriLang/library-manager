-- Persist remote category snapshots before any local assignment changes.
CREATE TABLE tiendanube_category_assignment_imports (
    id BIGSERIAL PRIMARY KEY,
    bookstore_id BIGINT NOT NULL REFERENCES bookstores(id),
    store_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCANNING',
    mode VARCHAR(20),
    products_scanned INTEGER NOT NULL DEFAULT 0,
    linked_inventories INTEGER NOT NULL DEFAULT 0,
    unlinked_products INTEGER NOT NULL DEFAULT 0,
    unknown_categories INTEGER NOT NULL DEFAULT 0,
    processed INTEGER NOT NULL DEFAULT 0,
    changed INTEGER NOT NULL DEFAULT 0,
    skipped INTEGER NOT NULL DEFAULT 0,
    errors INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ,
    CONSTRAINT chk_tn_category_import_status CHECK (status IN ('SCANNING','READY','APPLYING','COMPLETED','FAILED'))
);
CREATE INDEX idx_tn_category_assignment_imports_bookstore
    ON tiendanube_category_assignment_imports(bookstore_id, created_at DESC);

CREATE TABLE tiendanube_category_assignment_import_items (
    import_id BIGINT NOT NULL REFERENCES tiendanube_category_assignment_imports(id) ON DELETE CASCADE,
    bookstore_id BIGINT NOT NULL REFERENCES bookstores(id),
    inventory_id BIGINT NOT NULL REFERENCES inventory(id) ON DELETE CASCADE,
    remote_category_ids TEXT NOT NULL DEFAULT '',
    local_category_ids TEXT NOT NULL DEFAULT '',
    existing_category_ids TEXT NOT NULL DEFAULT '',
    missing_remote_category_ids TEXT NOT NULL DEFAULT '',
    status VARCHAR(20) NOT NULL DEFAULT 'READY',
    message TEXT,
    PRIMARY KEY (import_id, inventory_id)
);
CREATE INDEX idx_tn_category_assignment_import_items_status
    ON tiendanube_category_assignment_import_items(import_id, status);
