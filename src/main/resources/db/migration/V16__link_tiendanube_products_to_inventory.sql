ALTER TABLE inventory
    DROP CONSTRAINT uk_inventory_book_id;


ALTER TABLE tiendanube_product_links
    ADD COLUMN inventory_id BIGINT;


UPDATE tiendanube_product_links link
SET inventory_id = inventory.id
FROM inventory
         JOIN tiendanube_stores store
              ON store.bookstore_id = inventory.bookstore_id
WHERE inventory.book_id = link.book_id
  AND inventory.condition = 'NEW'
  AND store.store_id = link.tiendanube_store_id;


DO $$
    DECLARE
        unresolved_links INTEGER;
    BEGIN
        SELECT COUNT(*)
        INTO unresolved_links
        FROM tiendanube_product_links
        WHERE inventory_id IS NULL;

        IF unresolved_links > 0 THEN
            RAISE EXCEPTION
                'No se pudieron asociar % vínculos de Tiendanube con un inventario NEW',
                unresolved_links;
        END IF;
    END $$;


ALTER TABLE tiendanube_product_links
    ALTER COLUMN inventory_id SET NOT NULL;


ALTER TABLE tiendanube_product_links
    ADD CONSTRAINT fk_tiendanube_product_links_inventory
        FOREIGN KEY (inventory_id)
            REFERENCES inventory (id);



ALTER TABLE tiendanube_product_links
    ADD CONSTRAINT uk_tiendanube_store_inventory
        UNIQUE (tiendanube_store_id, inventory_id);


ALTER TABLE tiendanube_product_links
    DROP CONSTRAINT fk_tiendanube_product_links_books;


ALTER TABLE tiendanube_product_links
    DROP COLUMN book_id;


ALTER TABLE inventory
    ADD COLUMN tiendanube_status VARCHAR(40)
        NOT NULL
        DEFAULT 'DISABLED';


UPDATE inventory inventory
SET tiendanube_status = 'LINKED'
WHERE EXISTS (
    SELECT 1
    FROM tiendanube_product_links link
    WHERE link.inventory_id = inventory.id
      AND link.active = TRUE
);


ALTER TABLE tiendanube_product_links
    ADD COLUMN last_synced_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN last_error TEXT;


ALTER TABLE books
    ADD COLUMN weight_grams NUMERIC(10, 2),
    ADD COLUMN width_cm NUMERIC(10, 2),
    ADD COLUMN height_cm NUMERIC(10, 2),
    ADD COLUMN depth_cm NUMERIC(10, 2);