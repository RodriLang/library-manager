ALTER TABLE books
    ADD COLUMN publication_year  INTEGER,
    ADD COLUMN publication_month INTEGER;

UPDATE books
SET publication_year  = EXTRACT(YEAR FROM publication_date)::INTEGER,
    publication_month = CASE
                            WHEN EXTRACT(MONTH FROM publication_date) = 1 THEN NULL
                            ELSE EXTRACT(MONTH FROM publication_date)::INTEGER
        END
WHERE publication_date IS NOT NULL;

ALTER TABLE books
    ADD CONSTRAINT chk_books_publication_month
        CHECK (
            publication_month IS NULL
                OR publication_month BETWEEN 1 AND 12
            ),
    ADD CONSTRAINT chk_books_publication_month_requires_year
        CHECK (
            publication_month IS NULL
                OR publication_year IS NOT NULL
            );

ALTER TABLE books
    DROP COLUMN publication_date;