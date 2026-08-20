ALTER TABLE tiendanube_product_links
    ADD COLUMN tiendanube_image_id   BIGINT,
    ADD COLUMN last_synced_cover_url TEXT;