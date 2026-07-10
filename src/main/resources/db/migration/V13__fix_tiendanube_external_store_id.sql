ALTER TABLE tiendanube_product_links
    DROP CONSTRAINT fk_tiendanube_product_links_store;

UPDATE tiendanube_stores
SET store_id = 7948456
WHERE store_id = 12345678;

UPDATE tiendanube_product_links
SET tiendanube_store_id = 7948456
WHERE tiendanube_store_id = 12345678;

UPDATE tiendanube_processed_events
SET store_id = 7948456
WHERE store_id = 12345678;

ALTER TABLE tiendanube_product_links
    ADD CONSTRAINT fk_tiendanube_product_links_store
        FOREIGN KEY (tiendanube_store_id)
            REFERENCES tiendanube_stores (store_id);