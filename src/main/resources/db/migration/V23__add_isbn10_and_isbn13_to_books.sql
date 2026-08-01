ALTER TABLE books
    ADD COLUMN isbn_10 VARCHAR(10),
    ADD COLUMN isbn_13 VARCHAR(13);

UPDATE books
SET isbn_13 = regexp_replace(isbn, '[^0-9]', '', 'g')
WHERE isbn IS NOT NULL
  AND regexp_replace(isbn, '[^0-9]', '', 'g') ~ '^[0-9]{13}$';

UPDATE books
SET isbn_10 = UPPER(regexp_replace(isbn, '[^0-9Xx]', '', 'g'))
WHERE isbn IS NOT NULL
  AND UPPER(regexp_replace(isbn, '[^0-9Xx]', '', 'g')) ~ '^[0-9]{9}[0-9X]$';

CREATE UNIQUE INDEX uk_books_isbn_13
    ON books (isbn_13)
    WHERE isbn_13 IS NOT NULL;

CREATE UNIQUE INDEX uk_books_isbn_10
    ON books (isbn_10)
    WHERE isbn_10 IS NOT NULL;

ALTER TABLE books
    ADD CONSTRAINT chk_books_isbn_13_format
        CHECK (
            isbn_13 IS NULL
                OR isbn_13 ~ '^[0-9]{13}$'
            );

ALTER TABLE books
    ADD CONSTRAINT chk_books_isbn_10_format
        CHECK (
            isbn_10 IS NULL
                OR isbn_10 ~ '^[0-9]{9}[0-9X]$'
            );