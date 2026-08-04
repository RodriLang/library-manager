ALTER TABLE editorial_prices
    ALTER COLUMN source DROP NOT NULL;

ALTER TABLE editorial_prices
    ADD COLUMN provider_id BIGINT;

ALTER TABLE editorial_prices
    ADD CONSTRAINT fk_editorial_prices_provider
        FOREIGN KEY (provider_id)
            REFERENCES price_list_providers (id);

ALTER TABLE editorial_prices
    DROP CONSTRAINT uk_editorial_prices_book_source_valid_from;

CREATE UNIQUE INDEX uk_editorial_prices_book_source_valid_from
    ON editorial_prices (book_id, source, valid_from)
    WHERE source IS NOT NULL;

CREATE UNIQUE INDEX uk_editorial_prices_book_provider_valid_from
    ON editorial_prices (book_id, provider_id, valid_from)
    WHERE provider_id IS NOT NULL;

ALTER TABLE editorial_prices
    ADD CONSTRAINT chk_editorial_prices_origin
        CHECK (
            (
                source IS NOT NULL
                    AND provider_id IS NULL
                )
                OR
            (
                source IS NULL
                    AND provider_id IS NOT NULL
                )
            );