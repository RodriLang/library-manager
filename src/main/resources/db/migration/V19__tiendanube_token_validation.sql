ALTER TABLE tiendanube_stores
    ADD COLUMN token_valid       BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN last_validated_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN connection_error  VARCHAR(100);