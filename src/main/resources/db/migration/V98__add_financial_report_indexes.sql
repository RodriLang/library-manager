-- Cash-flow reporting by payment date.
CREATE INDEX IF NOT EXISTS idx_sale_payments_created_at
    ON sale_payments (created_at, sale_id);

CREATE INDEX IF NOT EXISTS idx_purchase_payments_paid_at_active
    ON purchase_payments (paid_at, purchase_id)
    WHERE cancelled_at IS NULL;

-- Pending economic data queries by bookstore + book.
CREATE INDEX IF NOT EXISTS idx_bookstore_provider_book_terms_bookstore_book_discount
    ON bookstore_provider_book_terms (bookstore_id, book_id)
    WHERE discount_percentage IS NOT NULL;
