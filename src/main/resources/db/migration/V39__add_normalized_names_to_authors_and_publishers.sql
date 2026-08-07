ALTER TABLE authors
    ADD COLUMN name_normalized VARCHAR(255);

ALTER TABLE publishers
    ADD COLUMN name_normalized VARCHAR(255);

UPDATE authors
SET name_normalized =
        TRIM(
                REGEXP_REPLACE(
                        REGEXP_REPLACE(
                                LOWER(unaccent(name)),
                                '[^a-z0-9]+',
                                ' ',
                                'g'
                        ),
                        '\s+',
                        ' ',
                        'g'
                )
        );

UPDATE publishers
SET name_normalized =
        TRIM(
                REGEXP_REPLACE(
                        REGEXP_REPLACE(
                                LOWER(unaccent(name)),
                                '[^a-z0-9]+',
                                ' ',
                                'g'
                        ),
                        '\s+',
                        ' ',
                        'g'
                )
        );

ALTER TABLE authors
    ALTER COLUMN name_normalized SET NOT NULL;

ALTER TABLE publishers
    ALTER COLUMN name_normalized SET NOT NULL;

CREATE INDEX idx_authors_name_normalized
    ON authors (name_normalized);

CREATE INDEX idx_publishers_name_normalized
    ON publishers (name_normalized);