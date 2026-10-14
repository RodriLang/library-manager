ALTER TABLE bookstore_stores
    ADD COLUMN IF NOT EXISTS home_title_color varchar(20),
    ADD COLUMN IF NOT EXISTS home_accent_color varchar(20),
    ADD COLUMN IF NOT EXISTS home_text_color varchar(20);
