ALTER TABLE tiendanube_product_links
    DROP CONSTRAINT IF EXISTS uk_tiendanube_store_inventory;

ALTER TABLE tiendanube_product_links
    DROP CONSTRAINT IF EXISTS uk_tiendanube_store_variant;

CREATE UNIQUE INDEX uk_tiendanube_store_inventory_active
    ON tiendanube_product_links (
                                 tiendanube_store_id,
                                 inventory_id
        )
    WHERE active = TRUE;

CREATE UNIQUE INDEX uk_tiendanube_store_variant_active
    ON tiendanube_product_links (
                                 tiendanube_store_id,
                                 tiendanube_variant_id
        )
    WHERE active = TRUE;