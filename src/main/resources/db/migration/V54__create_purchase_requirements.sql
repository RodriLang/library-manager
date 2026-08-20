CREATE TABLE purchase_requirements
(
    id                    BIGSERIAL PRIMARY KEY,
    bookstore_id          BIGINT      NOT NULL,
    book_id               BIGINT      NOT NULL,
    quantity              INTEGER     NOT NULL,
    preferred_provider_id BIGINT,
    status                VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_purchase_requirements_bookstore
        FOREIGN KEY (bookstore_id)
            REFERENCES bookstores (id),

    CONSTRAINT fk_purchase_requirements_book
        FOREIGN KEY (book_id)
            REFERENCES books (id),

    CONSTRAINT fk_purchase_requirements_provider
        FOREIGN KEY (preferred_provider_id)
            REFERENCES price_list_providers (id),

    CONSTRAINT chk_purchase_requirements_quantity
        CHECK (quantity > 0),

    CONSTRAINT chk_purchase_requirements_status
        CHECK (
            status IN (
                       'PENDING',
                       'CANCELLED'
                )
            )
);


CREATE UNIQUE INDEX uk_purchase_requirements_pending_book_bookstore
    ON purchase_requirements (
                              bookstore_id,
                              book_id
        )
    WHERE status = 'PENDING';


CREATE INDEX idx_purchase_requirements_bookstore_status
    ON purchase_requirements (
                              bookstore_id,
                              status
        );


CREATE INDEX idx_purchase_requirements_provider
    ON purchase_requirements (
                              preferred_provider_id
        )
    WHERE preferred_provider_id IS NOT NULL;


CREATE TABLE purchase_requirement_sources
(
    id                      BIGSERIAL PRIMARY KEY,
    purchase_requirement_id BIGINT      NOT NULL,
    source_type             VARCHAR(30) NOT NULL,
    quantity                INTEGER     NOT NULL,
    reference_id            VARCHAR(100),
    provider_id             BIGINT,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_purchase_requirement_sources_requirement
        FOREIGN KEY (purchase_requirement_id)
            REFERENCES purchase_requirements (id),

    CONSTRAINT fk_purchase_requirement_sources_provider
        FOREIGN KEY (provider_id)
            REFERENCES price_list_providers (id),

    CONSTRAINT chk_purchase_requirement_sources_type
        CHECK (
            source_type IN (
                            'SALE',
                            'INVENTORY',
                            'CATALOG',
                            'LOW_STOCK',
                            'MANUAL',
                            'ADJUSTMENT'
                )
            ),

    CONSTRAINT chk_purchase_requirement_sources_quantity
        CHECK (quantity <> 0)
);


CREATE INDEX idx_purchase_requirement_sources_requirement
    ON purchase_requirement_sources (
                                     purchase_requirement_id
        );


CREATE INDEX idx_purchase_requirement_sources_reference
    ON purchase_requirement_sources (
                                     source_type,
                                     reference_id
        )
    WHERE reference_id IS NOT NULL;