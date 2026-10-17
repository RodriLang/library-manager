ALTER TABLE store_mercado_pago_configs
    ADD COLUMN IF NOT EXISTS account_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS account_nickname VARCHAR(255),
    ADD COLUMN IF NOT EXISTS account_first_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS account_last_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS account_country_id VARCHAR(10);
