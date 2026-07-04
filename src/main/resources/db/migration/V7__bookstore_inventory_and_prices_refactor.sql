CREATE TABLE bookstores
(
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

INSERT INTO bookstores (id, name, active, created_at, updated_at)
VALUES (1, 'Librería principal', TRUE, NOW(), NOW());

SELECT setval(
               pg_get_serial_sequence('bookstores', 'id'),
               (SELECT MAX(id) FROM bookstores)
       );

ALTER TABLE books
    ADD COLUMN genre_name              VARCHAR(255),
    ADD COLUMN catalog_status          VARCHAR(50),
    ADD COLUMN created_by_bookstore_id BIGINT;

UPDATE books
SET catalog_status = CASE
                         WHEN source = 'MANUAL' THEN 'PENDING_REVIEW'
                         ELSE 'VERIFIED'
    END;

ALTER TABLE books
    ALTER COLUMN catalog_status SET NOT NULL;

ALTER TABLE books
    ADD CONSTRAINT fk_books_created_by_bookstore
        FOREIGN KEY (created_by_bookstore_id) REFERENCES bookstores (id);

CREATE TABLE editorial_prices
(
    id         BIGSERIAL PRIMARY KEY,
    book_id    BIGINT         NOT NULL,
    price      NUMERIC(12, 2) NOT NULL,
    currency   VARCHAR(3)     NOT NULL DEFAULT 'ARS',
    source     VARCHAR(50)    NOT NULL,
    valid_from DATE           NOT NULL,
    active     BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,

    CONSTRAINT fk_editorial_prices_book
        FOREIGN KEY (book_id) REFERENCES books (id),

    CONSTRAINT uk_editorial_prices_book_source_valid_from
        UNIQUE (book_id, source, valid_from)
);

CREATE INDEX idx_editorial_prices_lookup
    ON editorial_prices (book_id, active, valid_from DESC);

INSERT INTO editorial_prices (book_id,
                              price,
                              currency,
                              source,
                              valid_from,
                              active,
                              created_at,
                              updated_at)
SELECT b.id,
       b.retail_price,
       'ARS',
       COALESCE(b.price_list_source, 'UNKNOWN'),
       b.retail_price_updated_at,
       TRUE,
       NOW(),
       NOW()
FROM books b
WHERE b.retail_price IS NOT NULL;

ALTER TABLE inventory
    ADD COLUMN bookstore_id BIGINT,
    ADD COLUMN condition    VARCHAR(50),
    ADD COLUMN sale_price   NUMERIC(12, 2);

UPDATE inventory i
SET bookstore_id = 1,
    condition    = 'NEW',
    sale_price   = COALESCE(b.retail_price, 0)
FROM books b
WHERE i.book_id = b.id;

ALTER TABLE inventory
    ALTER COLUMN bookstore_id SET NOT NULL,
    ALTER COLUMN condition SET NOT NULL,
    ALTER COLUMN sale_price SET NOT NULL;

ALTER TABLE inventory
    ADD CONSTRAINT fk_inventory_bookstore
        FOREIGN KEY (bookstore_id) REFERENCES bookstores (id);

ALTER TABLE inventory
    DROP CONSTRAINT IF EXISTS uk_inventory_book;

ALTER TABLE inventory
    DROP CONSTRAINT IF EXISTS inventory_book_id_key;

ALTER TABLE inventory
    ADD CONSTRAINT uk_inventory_book_bookstore_condition
        UNIQUE (book_id, bookstore_id, condition);

ALTER TABLE books
    DROP COLUMN retail_price,
    DROP COLUMN retail_price_updated_at,
    DROP COLUMN price_list_source;

ALTER TABLE price_list_import_jobs
    ADD COLUMN valid_from       DATE,
    ADD COLUMN created_prices   INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN updated_prices   INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN unchanged_prices INTEGER NOT NULL DEFAULT 0;

UPDATE price_list_import_jobs
SET valid_from = CURRENT_DATE
WHERE valid_from IS NULL;

ALTER TABLE price_list_import_jobs
    ALTER COLUMN valid_from SET NOT NULL;

ALTER TABLE price_list_import_jobs
    DROP COLUMN updated_books;