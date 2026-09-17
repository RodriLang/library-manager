ALTER TABLE price_list_providers
    RENAME TO providers;

ALTER SEQUENCE IF EXISTS price_list_providers_id_seq
    RENAME TO providers_id_seq;

ALTER TABLE providers
    RENAME CONSTRAINT price_list_providers_pkey TO providers_pkey;

ALTER TABLE providers
    RENAME CONSTRAINT uk_price_list_providers_code TO uk_providers_code;

ALTER TABLE providers
    ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'COMMERCIAL';

UPDATE providers
SET type = 'SYSTEM'
WHERE code = 'UNIFICADO';

ALTER TABLE providers
    ADD CONSTRAINT chk_providers_type
        CHECK (type IN ('COMMERCIAL', 'SYSTEM'));
