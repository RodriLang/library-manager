-- =========================================================
-- A) EDITORIAL PRICES: UNICIDAD POR TIPO DE ORIGEN
-- =========================================================

CREATE UNIQUE INDEX IF NOT EXISTS uk_editorial_prices_price_list
    ON editorial_prices (book_id, provider_id, valid_from)
    WHERE origin = 'PRICE_LIST';

CREATE UNIQUE INDEX IF NOT EXISTS uk_editorial_prices_manual_distributor
    ON editorial_prices (book_id, provider_id, valid_from)
    WHERE origin = 'MANUAL_DISTRIBUTOR';

-- La restricción histórica ya no representa el modelo actual.
ALTER TABLE editorial_prices
    DROP CONSTRAINT IF EXISTS uk_editorial_prices_book_provider_valid_from;

DROP INDEX IF EXISTS uk_editorial_prices_book_provider_valid_from;


-- =========================================================
-- B) EFFECTIVE EDITORIAL PRICES
-- =========================================================

DO
$$
    BEGIN
        IF EXISTS (SELECT 1
                   FROM effective_editorial_prices
                   WHERE active = TRUE
                   GROUP BY book_id, valid_from
                   HAVING COUNT(*) > 1) THEN
            RAISE EXCEPTION
                'Abortado: existen múltiples effective_editorial_prices activos para el mismo libro y vigencia';
        END IF;
    END
$$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_effective_editorial_prices_active_book_valid_from
    ON effective_editorial_prices (book_id, valid_from)
    WHERE active = TRUE;