ALTER TABLE inventory_count_sessions
    ADD COLUMN IF NOT EXISTS default_editorial_price_sync_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS default_publish_on_tiendanube BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS default_tiendanube_price_sync_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS default_minimum_stock INTEGER NOT NULL DEFAULT 0;

ALTER TABLE inventory_count_items
    ADD COLUMN IF NOT EXISTS editorial_price_sync_override BOOLEAN,
    ADD COLUMN IF NOT EXISTS publish_on_tiendanube_override BOOLEAN,
    ADD COLUMN IF NOT EXISTS tiendanube_price_sync_override BOOLEAN,
    ADD COLUMN IF NOT EXISTS minimum_stock_override INTEGER;

CREATE INDEX IF NOT EXISTS idx_inventory_count_items_pending_book
    ON inventory_count_items (book_id, session_id)
    WHERE applied_at IS NULL AND book_id IS NOT NULL;
