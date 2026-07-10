ALTER TABLE tiendanube_product_links
    DROP CONSTRAINT fk_tiendanube_product_links_store;

UPDATE tiendanube_product_links link
SET tiendanube_store_id = store.store_id
FROM tiendanube_stores store
WHERE link.tiendanube_store_id = store.id;

ALTER TABLE tiendanube_product_links
    ADD CONSTRAINT fk_tiendanube_product_links_store
        FOREIGN KEY (tiendanube_store_id)
            REFERENCES tiendanube_stores (store_id);