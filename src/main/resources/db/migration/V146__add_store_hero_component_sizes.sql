ALTER TABLE bookstore_stores
    ADD COLUMN IF NOT EXISTS hero_logo_size varchar(20) NOT NULL DEFAULT 'MEDIUM',
    ADD COLUMN IF NOT EXISTS hero_name_size varchar(20) NOT NULL DEFAULT 'MEDIUM',
    ADD COLUMN IF NOT EXISTS hero_title_size varchar(20) NOT NULL DEFAULT 'MEDIUM';
