CREATE TABLE tiendanube_stores
(
    id           BIGSERIAL PRIMARY KEY,
    store_id     BIGINT       NOT NULL UNIQUE,
    access_token VARCHAR(500) NOT NULL,
    token_type   VARCHAR(50),
    scope        VARCHAR(500),
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    connected_at TIMESTAMP    NOT NULL
);

CREATE TABLE tiendanube_product_links
(
    id                    BIGSERIAL PRIMARY KEY,
    book_id               BIGINT    NOT NULL,
    tiendanube_store_id   BIGINT    NOT NULL,
    tiendanube_product_id BIGINT    NOT NULL,
    tiendanube_variant_id BIGINT    NOT NULL,
    sku                   VARCHAR(100),
    active                BOOLEAN   NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_tiendanube_product_links_books
        FOREIGN KEY (book_id) REFERENCES books (id),

    CONSTRAINT fk_tiendanube_product_links_store
        FOREIGN KEY (tiendanube_store_id) REFERENCES tiendanube_stores (id),

    CONSTRAINT uk_tiendanube_store_variant
        UNIQUE (tiendanube_store_id, tiendanube_variant_id)
);

CREATE TABLE tiendanube_processed_events
(
    id           BIGSERIAL PRIMARY KEY,
    store_id     BIGINT       NOT NULL,
    resource_id  BIGINT       NOT NULL,
    event        VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_tiendanube_processed_event
        UNIQUE (store_id, resource_id, event)
);