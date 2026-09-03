-- V76__editorial_price_control_and_monthly_price_lists.sql

-- =========================================================
-- 1. VALIDACIONES PREVIAS
-- =========================================================

-- valid_from es obligatorio en editorial_prices.
DO
$$
    BEGIN
        IF EXISTS (SELECT 1
                   FROM editorial_prices
                   WHERE valid_from IS NULL) THEN
            RAISE EXCEPTION
                'Abortado: existen editorial_prices con valid_from NULL.';
        END IF;
    END
$$;


-- valid_from es obligatorio en las importaciones.
DO
$$
    BEGIN
        IF EXISTS (SELECT 1
                   FROM price_list_import_jobs
                   WHERE valid_from IS NULL) THEN
            RAISE EXCEPTION
                'Abortado: existen price_list_import_jobs con valid_from NULL.';
        END IF;
    END
$$;


-- Antes de llevar todos los PRICE_LIST al primer día del mes,
-- comprobar que no se generarán duplicados para:
--
-- book + provider + mes
--
-- Se valida sobre todas las filas, no sólo las activas.
DO
$$
    BEGIN
        IF EXISTS (SELECT 1
                   FROM editorial_prices ep
                   WHERE ep.origin = 'PRICE_LIST'
                   GROUP BY ep.book_id,
                            ep.provider_id,
                            DATE_TRUNC('month', ep.valid_from)::date
                   HAVING COUNT(*) > 1) THEN
            RAISE EXCEPTION
                'Abortado: existen múltiples PRICE_LIST para el mismo libro, proveedor y mes. Deben resolverse antes de normalizar valid_from.';
        END IF;
    END
$$;


-- =========================================================
-- 2. NORMALIZAR LISTAS DE PRECIOS A PERÍODO MENSUAL
-- =========================================================

-- PRICE_LIST utiliza granularidad mensual.
-- La representación canónica en DB es siempre el día 01.
UPDATE editorial_prices
SET valid_from = DATE_TRUNC('month', valid_from)::date
WHERE origin = 'PRICE_LIST'
  AND EXTRACT(DAY FROM valid_from) <> 1;


-- El job debe conservar el mismo período canónico utilizado
-- por los precios que produjo.
UPDATE price_list_import_jobs
SET valid_from = DATE_TRUNC('month', valid_from)::date
WHERE EXTRACT(DAY FROM valid_from) <> 1;


-- =========================================================
-- 3. REFORZAR INVARIANTES DE FECHA
-- =========================================================

ALTER TABLE editorial_prices
    ALTER COLUMN valid_from SET NOT NULL;

ALTER TABLE price_list_import_jobs
    ALTER COLUMN valid_from SET NOT NULL;


-- Impedir que en el futuro un PRICE_LIST pueda quedar con
-- un día distinto del primero del mes, incluso si algún flujo
-- evita accidentalmente la normalización del backend.
ALTER TABLE editorial_prices
    DROP CONSTRAINT IF EXISTS ck_editorial_prices_price_list_month_start;

ALTER TABLE editorial_prices
    ADD CONSTRAINT ck_editorial_prices_price_list_month_start
        CHECK (
            origin <> 'PRICE_LIST'
                OR EXTRACT(DAY FROM valid_from) = 1
            );


-- Todos los price_list_import_jobs representan listas,
-- por lo que su período también debe estar normalizado.
ALTER TABLE price_list_import_jobs
    DROP CONSTRAINT IF EXISTS ck_price_list_import_jobs_month_start;

ALTER TABLE price_list_import_jobs
    ADD CONSTRAINT ck_price_list_import_jobs_month_start
        CHECK (EXTRACT(DAY FROM valid_from) = 1);


-- =========================================================
-- 4. SNAPSHOT COMPLETO DE LAS RESOLUCIONES
-- =========================================================

ALTER TABLE editorial_price_resolutions
    ADD COLUMN IF NOT EXISTS resolved_currency VARCHAR(3);


-- Backfill a partir del precio que fue seleccionado para
-- realizar cada resolución.
UPDATE editorial_price_resolutions r
SET resolved_currency = UPPER(ep.currency)
FROM editorial_prices ep
WHERE ep.id = r.selected_editorial_price_id
  AND r.resolved_currency IS NULL;


-- La resolución debe tener siempre precio + moneda.
DO
$$
    BEGIN
        IF EXISTS (SELECT 1
                   FROM editorial_price_resolutions
                   WHERE resolved_currency IS NULL) THEN
            RAISE EXCEPTION
                'Abortado: no fue posible determinar resolved_currency para todas las resoluciones.';
        END IF;
    END
$$;

ALTER TABLE editorial_price_resolutions
    ALTER COLUMN resolved_currency SET NOT NULL;


-- =========================================================
-- 5. AUDITORÍA DE BAJA DE RESOLUCIONES
-- =========================================================

ALTER TABLE editorial_price_resolutions
    ADD COLUMN IF NOT EXISTS deactivated_at          TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS deactivated_by_username VARCHAR(255),
    ADD COLUMN IF NOT EXISTS deactivation_note       TEXT;


-- =========================================================
-- 6. AUDITORÍA DE BAJA DE PRECIOS / FUENTES
-- =========================================================

ALTER TABLE editorial_prices
    ADD COLUMN IF NOT EXISTS deactivated_at          TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS deactivated_by_username VARCHAR(255),
    ADD COLUMN IF NOT EXISTS deactivation_note       TEXT;


-- =========================================================
-- 7. ÍNDICE PARA PROTEGER BAJAS DE PRECIOS UTILIZADOS
-- =========================================================

-- EditorialPriceControlService consulta si un EditorialPrice
-- está siendo utilizado por una resolución activa antes de
-- permitir su baja.
CREATE INDEX IF NOT EXISTS idx_editorial_price_resolutions_active_selected_price
    ON editorial_price_resolutions (selected_editorial_price_id)
    WHERE active = TRUE;


-- =========================================================
-- 8. VALIDACIONES FINALES
-- =========================================================

-- No debe quedar ningún PRICE_LIST con día distinto de 01.
DO
$$
    BEGIN
        IF EXISTS (SELECT 1
                   FROM editorial_prices
                   WHERE origin = 'PRICE_LIST'
                     AND EXTRACT(DAY FROM valid_from) <> 1) THEN
            RAISE EXCEPTION
                'Error de migración: quedaron PRICE_LIST sin normalizar al primer día del mes.';
        END IF;
    END
$$;


-- Tampoco deben quedar jobs con día distinto de 01.
DO
$$
    BEGIN
        IF EXISTS (SELECT 1
                   FROM price_list_import_jobs
                   WHERE EXTRACT(DAY FROM valid_from) <> 1) THEN
            RAISE EXCEPTION
                'Error de migración: quedaron price_list_import_jobs sin normalizar al primer día del mes.';
        END IF;
    END
$$;