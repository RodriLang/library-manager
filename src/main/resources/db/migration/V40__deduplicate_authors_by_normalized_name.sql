CREATE TEMP TABLE author_dedup_map AS
SELECT
    a.id AS duplicate_id,
    canonical.canonical_id
FROM authors a
         JOIN (
    SELECT
        name_normalized,
        MIN(id) AS canonical_id
    FROM authors
    GROUP BY name_normalized
    HAVING COUNT(*) > 1
) canonical
              ON canonical.name_normalized = a.name_normalized
WHERE a.id <> canonical.canonical_id;


INSERT INTO book_authors (
    book_id,
    author_id
)
SELECT DISTINCT
    ba.book_id,
    mapping.canonical_id
FROM book_authors ba
         JOIN author_dedup_map mapping
              ON mapping.duplicate_id = ba.author_id
WHERE NOT EXISTS (
    SELECT 1
    FROM book_authors existing
    WHERE existing.book_id = ba.book_id
      AND existing.author_id = mapping.canonical_id
);


DELETE FROM book_authors ba
    USING author_dedup_map mapping
WHERE ba.author_id = mapping.duplicate_id;


DELETE FROM authors a
    USING author_dedup_map mapping
WHERE a.id = mapping.duplicate_id;


DROP INDEX IF EXISTS idx_authors_name_normalized;

CREATE UNIQUE INDEX uk_authors_name_normalized
    ON authors (name_normalized);