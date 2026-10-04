-- Anaquel deja de mantener precios editoriales globales.
-- inventory_prices pasa a ser la única fuente persistente de precios de venta por librería.

-- V109 ya migró el precio vigente. Repetimos el backfill defensivamente antes de
-- eliminar inventory.sale_price para instalaciones que hayan quedado a mitad de transición.
INSERT INTO inventory_prices (inventory_id,
                              amount,
                              effective_from,
                              source,
                              created_at,
                              updated_at)
SELECT i.id,
       i.sale_price,
       (CURRENT_TIMESTAMP AT TIME ZONE 'America/Argentina/Buenos_Aires')::date,
       'LEGACY_MIGRATION',
       NOW(),
       NOW()
FROM inventory i
WHERE i.sale_price IS NOT NULL
  AND i.sale_price > 0
ON CONFLICT (inventory_id, effective_from) DO NOTHING;

-- El precio de venta ya no se duplica en inventory.
ALTER TABLE inventory
    DROP COLUMN IF EXISTS sale_price;

-- Desaparece la sincronización con precio editorial global.
ALTER TABLE inventory
    DROP COLUMN IF EXISTS editorial_price_sync_enabled;

ALTER TABLE inventory_count_sessions
    DROP COLUMN IF EXISTS default_editorial_price_sync_enabled;

ALTER TABLE inventory_count_items
    DROP COLUMN IF EXISTS editorial_price_sync_override;

-- Las importaciones locales dejan de depender de proveedores globales.
ALTER TABLE inventory_price_imports
    ADD COLUMN IF NOT EXISTS source_name VARCHAR(150);

UPDATE inventory_price_imports ipi
SET source_name = p.name
FROM providers p
WHERE ipi.provider_id = p.id
  AND ipi.source_name IS NULL;

ALTER TABLE inventory_price_imports
    DROP CONSTRAINT IF EXISTS fk_inventory_price_import_provider;

ALTER TABLE inventory_price_imports
    DROP COLUMN IF EXISTS provider_id;

-- En Compras esta foto representa el precio de venta local existente al comprar.
DO
$$
    BEGIN
        IF EXISTS (SELECT 1
                   FROM information_schema.columns
                   WHERE table_schema = 'public'
                     AND table_name = 'purchase_items'
                     AND column_name = 'editorial_price_snapshot') AND NOT EXISTS (SELECT 1
                                                                                   FROM information_schema.columns
                                                                                   WHERE table_schema = 'public'
                                                                                     AND table_name = 'purchase_items'
                                                                                     AND column_name = 'sale_price_snapshot') THEN
            ALTER TABLE purchase_items
                RENAME COLUMN editorial_price_snapshot TO sale_price_snapshot;
        END IF;
    END
$$;

-- Los jobs de portada podían quedar vinculados al importador global de precios.
DROP INDEX IF EXISTS idx_book_cover_jobs_price_list_import_job;

ALTER TABLE book_cover_jobs
    DROP CONSTRAINT IF EXISTS fk_book_cover_jobs_price_list_import_job;

ALTER TABLE book_cover_jobs
    DROP COLUMN IF EXISTS price_list_import_job_id;

-- Primero quitamos la constraint antigua, porque no reconoce el nuevo valor IMPORTED.
ALTER TABLE books
    DROP CONSTRAINT IF EXISTS chk_books_source;

-- Normalizamos valores históricos para que el modelo persistido tampoco conserve
-- enums ligados al subsistema editorial eliminado.
UPDATE books
SET source = 'IMPORTED'
WHERE source = 'EDITORIAL_PRICE_LIST';

ALTER TABLE books
    ADD CONSTRAINT chk_books_source
        CHECK (
            source IN (
                       'MANUAL',
                       'EXTERNAL_METADATA',
                       'IMPORTED'
                )
            );

-- Mismo criterio: primero retiramos la constraint con los valores legacy.
ALTER TABLE inventory_cost_layers
    DROP CONSTRAINT IF EXISTS ck_inventory_cost_layers_reference_source;

UPDATE inventory_cost_layers
SET reference_price_source = CASE
                                 WHEN reference_price_source = 'EDITORIAL_PRICE'
                                     THEN 'LEGACY_PRICE'
                                 WHEN reference_price_source = 'INVENTORY_SALE_PRICE'
                                     THEN 'INVENTORY_PRICE'
                                 ELSE reference_price_source
    END
WHERE reference_price_source IN (
                                 'EDITORIAL_PRICE',
                                 'INVENTORY_SALE_PRICE'
    );

ALTER TABLE inventory_cost_layers
    ADD CONSTRAINT ck_inventory_cost_layers_reference_source
        CHECK (
            reference_price_source IS NULL
                OR reference_price_source IN (
                                              'LEGACY_PRICE',
                                              'INVENTORY_PRICE'
                )
            );

-- Eliminación física del subsistema global de listas/precios.
-- Se conservan providers y provider_books porque hoy también pertenecen a Compras,
-- pedidos, reposición y preferencias de proveedor.
DROP TABLE IF EXISTS price_list_import_items;
DROP TABLE IF EXISTS editorial_price_confirmations;
DROP TABLE IF EXISTS effective_editorial_prices;
DROP TABLE IF EXISTS editorial_price_resolutions;
DROP TABLE IF EXISTS price_list_import_price_staging;
DROP TABLE IF EXISTS price_list_import_staging_rows;
DROP TABLE IF EXISTS price_list_import_job_errors;
DROP TABLE IF EXISTS price_list_import_jobs;
DROP TABLE IF EXISTS price_list_column_mappings;
DROP TABLE IF EXISTS price_list_import_configs;
DROP TABLE IF EXISTS provider_publisher_mappings;
DROP TABLE IF EXISTS editorial_prices;