-- =========================================================
-- NORMALIZE DUPLICATE PUBLISHERS
-- =========================================================
--
-- Merges publishers that have been confirmed to represent
-- the same canonical publisher.
--
-- After this migration, publishers.name_normalized becomes
-- unique so technical duplicates cannot be created again.
-- =========================================================


-- =========================================================
-- TEMPORARY MERGE TABLE
-- =========================================================

CREATE TEMP TABLE publisher_merges
(
    source_name VARCHAR(200) NOT NULL,
    target_name VARCHAR(200) NOT NULL
) ON COMMIT DROP;


INSERT INTO publisher_merges (
    source_name,
    target_name
)
VALUES
    ('Adn.', 'Adn'),
    ('Alianza.', 'Alianza'),
    ('Aulas Y Andamios -', 'Aulas Y Andamios'),
    ('Capicua.', 'Capicua'),
    ('Catedra.', 'Catedra'),
    ('Crack-up', 'Crack Up'),
    ('Dib-buks', 'Dib Buks'),
    ('Edic.univ.diego Portales', 'Edic. Univ. Diego Portales'),
    ('G.division Libros', 'G Division Libros'),
    ('Hispano-europea', 'Hispano Europea'),
    ('Malas Tierras - Underwood', 'Malas Tierras & Underwood'),
    ('#numeral', 'Numeral'),
    ('Ob-stare', 'Ob Stare'),
    ('Prensas Univ.zaragoza', 'Prensas Univ. Zaragoza'),
    ('Univ Pais Vasco', 'Univ. Pais Vasco'),
    ('Univ.pontificia Comillas Madrid', 'Univ. Pontificia Comillas Madrid'),
    ('V.& R.', 'V&r'),
    ('Vera.', 'Vera'),
    ('Zig - Zag', 'Zig Zag'),
    ('Zig-zag', 'Zig Zag');


-- =========================================================
-- VALIDATE TARGET PUBLISHERS
-- =========================================================

DO
$$
    DECLARE
        missing_targets TEXT;
    BEGIN
        SELECT STRING_AGG(DISTINCT merge_data.target_name, ', ')
        INTO missing_targets
        FROM publisher_merges merge_data
        WHERE NOT EXISTS (SELECT 1
                          FROM publishers publisher
                          WHERE publisher.name = merge_data.target_name);

        IF missing_targets IS NOT NULL THEN
            RAISE EXCEPTION
                'Missing canonical publisher targets: %',
                missing_targets;
        END IF;
    END
$$;


-- =========================================================
-- MOVE BOOK REFERENCES
-- =========================================================

UPDATE books book
SET publisher_id = target.id
FROM publisher_merges merge_data
         JOIN publishers source
              ON source.name = merge_data.source_name
         JOIN publishers target
              ON target.name = merge_data.target_name
WHERE book.publisher_id = source.id;


-- =========================================================
-- MOVE PROVIDER MAPPING REFERENCES
-- =========================================================
--
-- Usually these duplicates should not currently be referenced
-- here, but keeping this makes the merge safe for future data.
-- =========================================================

UPDATE provider_publisher_mappings mapping
SET publisher_id = target.id
FROM publisher_merges merge_data
         JOIN publishers source
              ON source.name = merge_data.source_name
         JOIN publishers target
              ON target.name = merge_data.target_name
WHERE mapping.publisher_id = source.id;


-- =========================================================
-- DELETE DUPLICATE PUBLISHERS
-- =========================================================

DELETE
FROM publishers source
    USING publisher_merges merge_data
WHERE source.name = merge_data.source_name
  AND NOT EXISTS (SELECT 1
                  FROM books book
                  WHERE book.publisher_id = source.id)
  AND NOT EXISTS (SELECT 1
                  FROM provider_publisher_mappings mapping
                  WHERE mapping.publisher_id = source.id);


-- =========================================================
-- VERIFY THAT NO DUPLICATE NORMALIZED NAMES REMAIN
-- =========================================================

DO
$$
    DECLARE
        duplicate_names TEXT;
    BEGIN
        SELECT STRING_AGG(name_normalized, ', ')
        INTO duplicate_names
        FROM (SELECT name_normalized
              FROM publishers
              GROUP BY name_normalized
              HAVING COUNT(*) > 1) duplicates;

        IF duplicate_names IS NOT NULL THEN
            RAISE EXCEPTION
                'Duplicate publisher normalized names remain: %',
                duplicate_names;
        END IF;
    END
$$;


-- =========================================================
-- ENFORCE UNIQUE NORMALIZED PUBLISHER NAMES
-- =========================================================

DROP INDEX IF EXISTS idx_publishers_name_normalized;

CREATE UNIQUE INDEX uk_publishers_name_normalized
    ON publishers (name_normalized);