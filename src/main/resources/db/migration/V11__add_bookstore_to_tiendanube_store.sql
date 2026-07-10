ALTER TABLE tiendanube_stores
    ADD COLUMN bookstore_id BIGINT;

UPDATE tiendanube_stores
SET bookstore_id = 1
WHERE bookstore_id IS NULL;

ALTER TABLE tiendanube_stores
    ALTER COLUMN bookstore_id SET NOT NULL;

ALTER TABLE tiendanube_stores
    ADD CONSTRAINT fk_tiendanube_stores_bookstore
        FOREIGN KEY (bookstore_id)
            REFERENCES bookstores (id);

ALTER TABLE tiendanube_stores
    ADD CONSTRAINT uk_tiendanube_stores_bookstore
        UNIQUE (bookstore_id);