-- =========================================================
-- PROVIDER PUBLISHER MAPPINGS
-- =========================================================

CREATE TABLE provider_publisher_mappings
(
    id                       BIGSERIAL PRIMARY KEY,

    provider_id              BIGINT       NOT NULL,

    external_name            VARCHAR(200) NOT NULL,

    external_name_normalized VARCHAR(200) NOT NULL,

    resolution_type          VARCHAR(20)  NOT NULL,

    publisher_id             BIGINT       NULL,

    CONSTRAINT fk_provider_publisher_mapping_provider
        FOREIGN KEY (provider_id)
            REFERENCES price_list_providers (id),

    CONSTRAINT fk_provider_publisher_mapping_publisher
        FOREIGN KEY (publisher_id)
            REFERENCES publishers (id),

    CONSTRAINT uk_provider_publisher_mapping_provider_external_name
        UNIQUE (provider_id, external_name_normalized),

    CONSTRAINT ck_provider_publisher_mapping_resolution
        CHECK (
            (
                resolution_type = 'MAP'
                    AND publisher_id IS NOT NULL
                )
                OR
            (
                resolution_type = 'IGNORE'
                    AND publisher_id IS NULL
                )
            )
);


CREATE INDEX idx_provider_publisher_mapping_publisher
    ON provider_publisher_mappings (publisher_id);


-- =========================================================
-- VALIDATE LUONGO PROVIDER
-- =========================================================

DO
$$
    DECLARE
        provider_count INTEGER;
    BEGIN
        SELECT COUNT(*)
        INTO provider_count
        FROM price_list_providers
        WHERE name = 'Distribuidora Alberto Luongo';

        IF provider_count <> 1 THEN
            RAISE EXCEPTION
                'Expected exactly one provider named Distribuidora Alberto Luongo, found %',
                provider_count;
        END IF;
    END
$$;


-- =========================================================
-- VALIDATE CANONICAL PUBLISHERS
-- =========================================================

DO
$$
    DECLARE
        missing_publishers TEXT;
    BEGIN
        SELECT STRING_AGG(expected.name, ', ')
        INTO missing_publishers
        FROM (VALUES ('Argonauta'),
                     ('Alsina'),
                     ('Letemendia'),
                     ('Gustavo Gili'),
                     ('H Kliczkowski'),
                     ('Konemann'),
                     ('Rm Editorial')) AS expected(name)
        WHERE NOT EXISTS (SELECT 1
                          FROM publishers p
                          WHERE p.name = expected.name);

        IF missing_publishers IS NOT NULL THEN
            RAISE EXCEPTION
                'Missing canonical publishers: %',
                missing_publishers;
        END IF;
    END
$$;


-- =========================================================
-- MAP: LUONGO EXTERNAL VALUES -> CANONICAL PUBLISHERS
-- =========================================================

INSERT INTO provider_publisher_mappings (provider_id,
                                         external_name,
                                         external_name_normalized,
                                         resolution_type,
                                         publisher_id)
SELECT provider.id,
       mapping.external_name,
       mapping.external_name_normalized,
       'MAP',
       publisher.id
FROM price_list_providers provider
         CROSS JOIN (VALUES ('CUSP/ARGON',
                             'cusp argon',
                             'Argonauta'),
                            ('ALSI/CUSPI',
                             'alsi cuspi',
                             'Alsina'),
                            ('CUSP/ALFAO',
                             'cusp alfao',
                             'Alsina'),
                            ('CUSP/LETEM',
                             'cusp letem',
                             'Letemendia'),
                            ('CUSP/DESCA',
                             'cusp desca',
                             'Gustavo Gili'),
                            ('CUSP/KLICZ',
                             'cusp klicz',
                             'H Kliczkowski'),
                            ('CUSP/KONEM',
                             'cusp konem',
                             'Konemann'),
                            ('CUSP/OMICR',
                             'cusp omicr',
                             'Rm Editorial')) AS mapping(
                                                         external_name,
                                                         external_name_normalized,
                                                         canonical_publisher_name
    )
         JOIN publishers publisher
              ON publisher.name = mapping.canonical_publisher_name
WHERE provider.name = 'Distribuidora Alberto Luongo';


-- =========================================================
-- IGNORE: VALUES THAT ARE NOT PUBLISHERS
-- =========================================================

INSERT INTO provider_publisher_mappings (provider_id,
                                         external_name,
                                         external_name_normalized,
                                         resolution_type,
                                         publisher_id)
SELECT provider.id,
       mapping.external_name,
       mapping.external_name_normalized,
       'IGNORE',
       NULL
FROM price_list_providers provider
         CROSS JOIN (VALUES ('DIST/FIRME', 'dist firme'),
                            ('IAMIQ/DIST', 'iamiq dist'),
                            ('OFERTA/ESP', 'oferta esp')) AS mapping(
                                                                     external_name,
                                                                     external_name_normalized
    )
WHERE provider.name = 'Distribuidora Alberto Luongo';


-- =========================================================
-- MIGRATE EXISTING BOOKS FROM CONFIRMED ALIASES
-- =========================================================

WITH publisher_merges AS (SELECT *
                          FROM (VALUES ('Cusp/argon', 'Argonauta'),
                                       ('Alsi/cuspi', 'Alsina'),
                                       ('Cusp/alfao', 'Alsina'),
                                       ('Cusp/letem', 'Letemendia'),
                                       ('Cusp/desca', 'Gustavo Gili'),
                                       ('Cusp/klicz', 'H Kliczkowski'),
                                       ('Cusp/konem', 'Konemann'),
                                       ('Cusp/omicr', 'Rm Editorial')) AS merge_data(
                                                                                     source_name,
                                                                                     target_name
                              ))
UPDATE books book
SET publisher_id = target.id
FROM publishers source,
     publishers target,
     publisher_merges merge
WHERE book.publisher_id = source.id
  AND source.name = merge.source_name
  AND target.name = merge.target_name;


-- =========================================================
-- REMOVE PUBLISHERS THAT WERE ONLY EXTERNAL ALIASES
-- =========================================================

DELETE
FROM publishers publisher
WHERE publisher.name IN (
                         'Cusp/argon',
                         'Alsi/cuspi',
                         'Cusp/alfao',
                         'Cusp/letem',
                         'Cusp/desca',
                         'Cusp/klicz',
                         'Cusp/konem',
                         'Cusp/omicr'
    )
  AND NOT EXISTS (SELECT 1
                  FROM books book
                  WHERE book.publisher_id = publisher.id);