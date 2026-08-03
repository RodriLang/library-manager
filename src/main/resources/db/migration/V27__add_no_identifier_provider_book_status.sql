ALTER TABLE provider_books
    DROP CONSTRAINT IF EXISTS chk_provider_books_identifier_status;

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
                                         'NO_IDENTIFIER',
                                         'MANUALLY_CONFIRMED'
                )
            );

UPDATE provider_books
SET identifier_status = 'NO_IDENTIFIER',
    updated_at        = NOW()
WHERE identifier_status IS NULL
  AND external_code IS NULL
  AND reported_isbn IS NULL;