CREATE TABLE drive_price_import_batches (
    id BIGSERIAL PRIMARY KEY,
    bookstore_id BIGINT NOT NULL REFERENCES bookstores(id),
    created_by_user_id BIGINT NOT NULL,
    folder_url TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_drive_price_batches_bookstore ON drive_price_import_batches(bookstore_id, created_at DESC);
CREATE TABLE drive_price_import_batch_items (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL REFERENCES drive_price_import_batches(id) ON DELETE CASCADE,
    drive_file_id VARCHAR(150) NOT NULL,
    filename TEXT NOT NULL,
    file_size BIGINT NOT NULL,
    position INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    import_id BIGINT REFERENCES inventory_price_imports(id),
    error_message TEXT,
    UNIQUE(batch_id, drive_file_id),
    UNIQUE(batch_id, position)
);
