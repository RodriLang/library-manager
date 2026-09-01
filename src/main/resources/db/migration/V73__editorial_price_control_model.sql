-- =========================================================
-- A) EDITORIAL_PRICES PASA A REPRESENTAR OBSERVACIONES
-- =========================================================

ALTER TABLE editorial_prices
    ADD COLUMN origin VARCHAR(30);

UPDATE editorial_prices
SET origin = 'PRICE_LIST'
WHERE origin IS NULL;

ALTER TABLE editorial_prices
    ALTER COLUMN origin SET DEFAULT 'PRICE_LIST',
    ALTER COLUMN origin SET NOT NULL;

ALTER TABLE editorial_prices
    ADD COLUMN external_source_type VARCHAR(30),
    ADD COLUMN source_name          VARCHAR(255),
    ADD COLUMN source_url           VARCHAR(1000),
    ADD COLUMN source_note          TEXT,
    ADD COLUMN created_by_username  VARCHAR(255);

-- Un precio manual o externo puede no tener distribuidor.
ALTER TABLE editorial_prices
    ALTER COLUMN provider_id DROP NOT NULL;

-- Eliminar la unicidad histórica:
-- book + provider + valid_from.
-- Ahora necesitamos permitir PRICE_LIST y MANUAL_DISTRIBUTOR
-- para el mismo distribuidor y vigencia.
DO
$$
    DECLARE
        constraint_record RECORD;
    BEGIN
        FOR constraint_record IN
            SELECT c.conname
            FROM pg_constraint c
                     JOIN pg_class t
                          ON t.oid = c.conrelid
                     JOIN pg_namespace n
                          ON n.oid = t.relnamespace
            WHERE n.nspname = 'public'
              AND t.relname = 'editorial_prices'
              AND c.contype = 'u'
              AND pg_get_constraintdef(c.oid)
                = 'UNIQUE (book_id, provider_id, valid_from)'
            LOOP
                EXECUTE format(
                        'ALTER TABLE public.editorial_prices DROP CONSTRAINT %I',
                        constraint_record.conname
                        );
            END LOOP;
    END
$$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_editorial_prices_price_list
    ON editorial_prices (book_id, provider_id, valid_from)
    WHERE origin = 'PRICE_LIST';

CREATE UNIQUE INDEX IF NOT EXISTS uk_editorial_prices_manual_distributor
    ON editorial_prices (book_id, provider_id, valid_from)
    WHERE origin = 'MANUAL_DISTRIBUTOR';

ALTER TABLE editorial_prices
    ADD CONSTRAINT ck_editorial_prices_origin
        CHECK (
            origin IN (
                       'PRICE_LIST',
                       'MANUAL_DISTRIBUTOR',
                       'MANUAL_PUBLISHER',
                       'MANUAL_EXTERNAL'
                )
            );

ALTER TABLE editorial_prices
    ADD CONSTRAINT ck_editorial_prices_external_source_type
        CHECK (
            external_source_type IS NULL
                OR external_source_type IN (
                                            'BOOKSTORE',
                                            'MARKETPLACE',
                                            'WEBSITE',
                                            'OTHER'
                )
            );

ALTER TABLE editorial_prices
    ADD CONSTRAINT ck_editorial_prices_origin_data
        CHECK (
            (
                origin = 'PRICE_LIST'
                    AND provider_id IS NOT NULL
                )
                OR
            (
                origin = 'MANUAL_DISTRIBUTOR'
                    AND provider_id IS NOT NULL
                )
                OR
            (
                origin = 'MANUAL_PUBLISHER'
                    AND source_name IS NOT NULL
                )
                OR
            (
                origin = 'MANUAL_EXTERNAL'
                    AND source_name IS NOT NULL
                    AND external_source_type IS NOT NULL
                )
            );

CREATE INDEX IF NOT EXISTS idx_editorial_prices_book_origin_valid_from
    ON editorial_prices (book_id, origin, valid_from DESC);

-- =========================================================
-- B) CONFIRMACIONES MANUALES
-- =========================================================

CREATE TABLE editorial_price_confirmations
(
    id                   BIGSERIAL PRIMARY KEY,

    editorial_price_id   BIGINT       NOT NULL,
    confirmed_on         DATE         NOT NULL,

    source_type          VARCHAR(20)  NOT NULL,
    provider_id          BIGINT,
    external_source_type VARCHAR(30),

    source_name          VARCHAR(255),
    source_url           VARCHAR(1000),
    note                 TEXT,

    created_by_username  VARCHAR(255) NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_editorial_price_confirmations_price
        FOREIGN KEY (editorial_price_id)
            REFERENCES editorial_prices (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_editorial_price_confirmations_provider
        FOREIGN KEY (provider_id)
            REFERENCES price_list_providers (id),

    CONSTRAINT ck_editorial_price_confirmations_source
        CHECK (
            source_type IN (
                            'DISTRIBUTOR',
                            'PUBLISHER',
                            'EXTERNAL'
                )
            ),

    CONSTRAINT ck_editorial_price_confirmations_external_type
        CHECK (
            external_source_type IS NULL
                OR external_source_type IN (
                                            'BOOKSTORE',
                                            'MARKETPLACE',
                                            'WEBSITE',
                                            'OTHER'
                )
            )
);

CREATE INDEX idx_editorial_price_confirmations_price_date
    ON editorial_price_confirmations (
                                      editorial_price_id,
                                      confirmed_on DESC,
                                      id DESC
        );

-- =========================================================
-- C) SCOPE DE IMPORTACIÓN
-- =========================================================

ALTER TABLE price_list_import_jobs
    ADD COLUMN import_scope VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN';

ALTER TABLE price_list_import_jobs
    ADD CONSTRAINT ck_price_list_import_jobs_scope
        CHECK (
            import_scope IN (
                             'FULL_CATALOG',
                             'PARTIAL_UPDATE',
                             'UNKNOWN'
                )
            );

-- Los jobs históricos quedan UNKNOWN.
-- No inferimos que hayan sido listas completas.

-- =========================================================
-- D) RESOLUCIONES MANUALES DE CONFLICTOS
-- =========================================================

CREATE TABLE editorial_price_resolutions
(
    id                          BIGSERIAL PRIMARY KEY,

    book_id                     BIGINT         NOT NULL,
    valid_from                  DATE           NOT NULL,

    selected_editorial_price_id BIGINT         NOT NULL,
    resolved_price              NUMERIC(12, 2) NOT NULL,

    resolution_type             VARCHAR(30)    NOT NULL,

    note                        TEXT,

    resolved_by_username        VARCHAR(255)   NOT NULL,
    created_at                  TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    active                      BOOLEAN        NOT NULL DEFAULT TRUE,

    supersedes_resolution_id    BIGINT,

    CONSTRAINT fk_editorial_price_resolutions_book
        FOREIGN KEY (book_id)
            REFERENCES books (id),

    CONSTRAINT fk_editorial_price_resolutions_selected_price
        FOREIGN KEY (selected_editorial_price_id)
            REFERENCES editorial_prices (id),

    CONSTRAINT fk_editorial_price_resolutions_supersedes
        FOREIGN KEY (supersedes_resolution_id)
            REFERENCES editorial_price_resolutions (id),

    CONSTRAINT ck_editorial_price_resolution_type
        CHECK (
            resolution_type IN (
                                'SOURCE_SELECTION',
                                'MANUAL_OVERRIDE'
                )
            )
);

CREATE UNIQUE INDEX uk_editorial_price_resolutions_active
    ON editorial_price_resolutions (book_id, valid_from)
    WHERE active = TRUE;

CREATE INDEX idx_editorial_price_resolutions_book_date
    ON editorial_price_resolutions (
                                    book_id,
                                    valid_from DESC,
                                    id DESC
        );

-- =========================================================
-- E) PRECIO EFECTIVO DE ANAQUEL
-- =========================================================

CREATE TABLE effective_editorial_prices
(
    id                          BIGSERIAL PRIMARY KEY,

    book_id                     BIGINT         NOT NULL,
    price                       NUMERIC(12, 2) NOT NULL,
    currency                    VARCHAR(3)     NOT NULL DEFAULT 'ARS',

    valid_from                  DATE           NOT NULL,

    determination_type          VARCHAR(40)    NOT NULL,
    authority                   VARCHAR(30)    NOT NULL,

    selected_editorial_price_id BIGINT,
    resolution_id               BIGINT,

    active                      BOOLEAN        NOT NULL DEFAULT TRUE,

    invalidated_at              TIMESTAMPTZ,
    invalidation_reason         VARCHAR(40),

    created_at                  TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_effective_editorial_prices_book
        FOREIGN KEY (book_id)
            REFERENCES books (id),

    CONSTRAINT fk_effective_editorial_prices_selected_price
        FOREIGN KEY (selected_editorial_price_id)
            REFERENCES editorial_prices (id),

    CONSTRAINT fk_effective_editorial_prices_resolution
        FOREIGN KEY (resolution_id)
            REFERENCES editorial_price_resolutions (id),

    CONSTRAINT ck_effective_editorial_price_determination
        CHECK (
            determination_type IN (
                                   'AUTO_SINGLE_SOURCE',
                                   'AUTO_SOURCE_AGREEMENT',
                                   'MANUAL_SOURCE_SELECTION',
                                   'MANUAL_OVERRIDE'
                )
            ),

    CONSTRAINT ck_effective_editorial_price_authority
        CHECK (
            authority IN (
                          'OFFICIAL',
                          'EXTERNAL_REFERENCE'
                )
            ),

    CONSTRAINT ck_effective_editorial_price_invalidation
        CHECK (
            invalidation_reason IS NULL
                OR invalidation_reason IN (
                                           'SOURCE_CONFLICT',
                                           'SUPERSEDED_CORRECTION',
                                           'COMPACTED_DUPLICATE'
                )
            )
);

CREATE INDEX idx_effective_editorial_prices_current
    ON effective_editorial_prices (
                                   book_id,
                                   valid_from DESC,
                                   id DESC
        )
    WHERE active = TRUE;

CREATE INDEX idx_effective_editorial_prices_selected_source
    ON effective_editorial_prices (selected_editorial_price_id);

CREATE INDEX idx_effective_editorial_prices_resolution
    ON effective_editorial_prices (resolution_id);