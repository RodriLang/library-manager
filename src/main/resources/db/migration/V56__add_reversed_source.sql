ALTER TABLE purchase_requirement_sources
    ADD COLUMN reversed_source_id BIGINT;

ALTER TABLE purchase_requirement_sources
    ADD CONSTRAINT fk_purchase_requirement_sources_reversed
        FOREIGN KEY (reversed_source_id)
            REFERENCES purchase_requirement_sources (id);

ALTER TABLE purchase_requirement_sources
    DROP CONSTRAINT chk_purchase_requirement_sources_type;

ALTER TABLE purchase_requirement_sources
    ADD CONSTRAINT chk_purchase_requirement_sources_type
        CHECK (
            source_type IN (
                            'SALE',
                            'INVENTORY',
                            'CATALOG',
                            'LOW_STOCK',
                            'MANUAL',
                            'ADJUSTMENT',
                            'REVERSAL'
                )
            );

CREATE UNIQUE INDEX uk_purchase_requirement_sources_reversal
    ON purchase_requirement_sources (
                                     reversed_source_id
        )
    WHERE reversed_source_id IS NOT NULL;