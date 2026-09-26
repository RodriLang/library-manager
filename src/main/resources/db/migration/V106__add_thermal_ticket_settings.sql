ALTER TABLE bookstore_fiscal_settings
    ADD COLUMN ticket_printing_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN ticket_paper_width_mm INTEGER NOT NULL DEFAULT 80,
    ADD COLUMN ticket_margin_mm INTEGER NOT NULL DEFAULT 2;

ALTER TABLE bookstore_fiscal_settings
    ADD CONSTRAINT ck_bookstore_fiscal_ticket_paper_width
        CHECK (ticket_paper_width_mm IN (58, 80)),
    ADD CONSTRAINT ck_bookstore_fiscal_ticket_margin
        CHECK (ticket_margin_mm BETWEEN 0 AND 8);
