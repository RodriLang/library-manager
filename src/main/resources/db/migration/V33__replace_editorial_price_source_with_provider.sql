-- ============================================================
-- EDITORIAL PRICES: ELIMINAR ORIGEN LEGACY
-- ============================================================

ALTER TABLE editorial_prices
    DROP CONSTRAINT IF EXISTS chk_editor_prices_origin;

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

DROP INDEX IF EXISTS uk_editorial_prices_book_provider_valid_from;

CREATE UNIQUE INDEX uk_editorial_prices_book_provider_valid_from
    ON editorial_prices (
                         book_id,
                         provider_id,
                         valid_from
        );

ALTER TABLE editorial_prices
    DROP COLUMN IF EXISTS source;