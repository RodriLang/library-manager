-- Track the last verified category state without querying Tiendanube for every inventory page.
CREATE TABLE tiendanube_category_sync_states (
    bookstore_id BIGINT NOT NULL REFERENCES bookstores(id),
    inventory_id BIGINT NOT NULL REFERENCES inventory(id) ON DELETE CASCADE,
    store_id BIGINT NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    revision BIGINT NOT NULL DEFAULT 0,
    last_verified_at TIMESTAMPTZ,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_tiendanube_category_sync_states PRIMARY KEY (bookstore_id, inventory_id, store_id),
    CONSTRAINT chk_tiendanube_category_sync_state CHECK (state IN ('PENDING', 'SYNCED'))
);
CREATE INDEX idx_tiendanube_category_sync_state_filter
    ON tiendanube_category_sync_states (bookstore_id, store_id, state);

-- Fast last-result lookup for the category state badge and FILTER selections.
CREATE INDEX idx_tiendanube_category_jobs_last
    ON tiendanube_sync_jobs (inventory_id, tiendanube_store_id, store_id, created_at DESC, id DESC)
    WHERE type = 'SYNC_CATEGORIES';
