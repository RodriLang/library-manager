ALTER TABLE books
    ADD COLUMN title_sort VARCHAR(500);

UPDATE books
SET title_sort = trim(
        regexp_replace(
                lower(unaccent(title)),
                '^[^a-z0-9]+',
                '',
                'g'
        )
                 );

ALTER TABLE books
    ALTER COLUMN title_sort SET NOT NULL;

CREATE INDEX idx_books_title_sort ON books (title_sort);