ALTER TABLE tiendanube_stores
    DROP CONSTRAINT IF EXISTS uk_tiendanube_store_bookstore,
    DROP CONSTRAINT IF EXISTS fk_tiendanube_store_bookstore;