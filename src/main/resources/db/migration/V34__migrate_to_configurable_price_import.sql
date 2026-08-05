-- ============================================================
-- 1. ELIMINAR ORIGEN LEGACY DE PRECIOS
-- ============================================================

ALTER TABLE editorial_prices
    DROP CONSTRAINT IF EXISTS chk_editorial_prices_origin;

DROP INDEX IF EXISTS uk_editorial_prices_book_source_valid_from;

DO
$$
    BEGIN
        IF EXISTS (SELECT 1
                   FROM editorial_prices
                   WHERE provider_id IS NULL) THEN
            RAISE EXCEPTION
                'Existen registros en editorial_prices sin provider_id.';
        END IF;
    END
$$;

ALTER TABLE editorial_prices
    ALTER COLUMN provider_id SET NOT NULL;

ALTER TABLE editorial_prices
    DROP COLUMN IF EXISTS source;


-- ============================================================
-- 2. ELIMINAR ORIGEN LEGACY DE JOBS
-- ============================================================

DO
$$
    BEGIN
        IF EXISTS (SELECT 1
                   FROM price_list_import_jobs
                   WHERE provider_id IS NULL) THEN
            RAISE EXCEPTION
                'Existen trabajos de importación sin provider_id.';
        END IF;
    END
$$;

ALTER TABLE price_list_import_jobs
    ALTER COLUMN provider_id SET NOT NULL;

ALTER TABLE price_list_import_jobs
    DROP COLUMN IF EXISTS price_list_source;


-- ============================================================
-- 3. REEMPLAZAR FIRST POR BY_INDEX
-- ============================================================

UPDATE price_list_import_configs
SET sheet_strategy = 'BY_INDEX',
    sheet_index    = COALESCE(sheet_index, 0)
WHERE sheet_strategy = 'FIRST';


-- ============================================================
-- 4. STAGING PARA IMPORTACIONES STREAMING
-- ============================================================

CREATE TABLE IF NOT EXISTS price_list_import_staging_rows
(
    id                 BIGSERIAL PRIMARY KEY,
    job_id             BIGINT      NOT NULL,
    row_number         INTEGER     NOT NULL,
    identifier_key     VARCHAR(300),
    row_payload        JSONB       NOT NULL,
    valid              BOOLEAN     NOT NULL DEFAULT TRUE,
    validation_message TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_price_list_import_staging_job
        FOREIGN KEY (job_id)
            REFERENCES price_list_import_jobs (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_price_list_staging_job_valid_id
    ON price_list_import_staging_rows (
                                       job_id,
                                       valid,
                                       id
        );

CREATE UNIQUE INDEX IF NOT EXISTS uk_price_list_staging_job_identifier
    ON price_list_import_staging_rows (
                                       job_id,
                                       identifier_key
        )
    WHERE identifier_key IS NOT NULL
        AND valid = TRUE;