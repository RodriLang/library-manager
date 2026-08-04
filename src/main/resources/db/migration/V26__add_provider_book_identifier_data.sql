ALTER TABLE provider_books
    ADD COLUMN reported_isbn     VARCHAR(32),
    ADD COLUMN identifier_status VARCHAR(50);

ALTER TABLE provider_books
    ADD CONSTRAINT chk_provider_books_identifier_status
        CHECK (
            identifier_status IS NULL
                OR identifier_status IN (
                                         'VALID_ISBN',
                                         'RECOVERED_MISSING_CHECK_DIGIT',
                                         'RECOVERED_INVALID_X',
                                         'RECOVERED_FROM_ISBN10',
                                         'RECOVERED_FROM_CODE_COLUMN',
                                         'EXTERNAL_CODE',
                                         'INVALID_UNRESOLVED',
                                         'MANUALLY_CONFIRMED'
                )
            );

CREATE INDEX idx_provider_books_reported_isbn
    ON provider_books (provider_id, reported_isbn);

CREATE INDEX idx_provider_books_identifier_status
    ON provider_books (identifier_status);