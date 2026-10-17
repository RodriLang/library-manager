CREATE TABLE inventory_image_assets (
    id BIGSERIAL PRIMARY KEY,
    content_hash VARCHAR(64) NOT NULL,
    cloudinary_public_id VARCHAR(255) NOT NULL,
    secure_url TEXT NOT NULL,
    format VARCHAR(20),
    width INTEGER,
    height INTEGER,
    file_size BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_inventory_image_assets_hash UNIQUE (content_hash),
    CONSTRAINT uk_inventory_image_assets_public_id UNIQUE (cloudinary_public_id),
    CONSTRAINT ck_inventory_image_assets_width CHECK (width IS NULL OR width > 0),
    CONSTRAINT ck_inventory_image_assets_height CHECK (height IS NULL OR height > 0),
    CONSTRAINT ck_inventory_image_assets_file_size CHECK (file_size IS NULL OR file_size >= 0)
);

CREATE TABLE inventory_images (
    id BIGSERIAL PRIMARY KEY,
    inventory_id BIGINT NOT NULL,
    asset_id BIGINT,
    external_url TEXT,
    source VARCHAR(20) NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_inventory_images_inventory
        FOREIGN KEY (inventory_id) REFERENCES inventory(id) ON DELETE CASCADE,
    CONSTRAINT fk_inventory_images_asset
        FOREIGN KEY (asset_id) REFERENCES inventory_image_assets(id),
    CONSTRAINT ck_inventory_images_source
        CHECK (source IN ('UPLOAD', 'URL')),
    CONSTRAINT ck_inventory_images_sort_order
        CHECK (sort_order BETWEEN 0 AND 4),
    CONSTRAINT ck_inventory_images_location
        CHECK (
            (source = 'UPLOAD' AND asset_id IS NOT NULL AND external_url IS NULL)
            OR
            (source = 'URL' AND asset_id IS NULL AND external_url IS NOT NULL)
        )
);

CREATE INDEX idx_inventory_images_inventory_id
    ON inventory_images(inventory_id);

CREATE INDEX idx_inventory_images_asset_id
    ON inventory_images(asset_id);
