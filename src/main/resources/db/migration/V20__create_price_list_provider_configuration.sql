CREATE TABLE price_list_providers
(
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(50)  NOT NULL,
    name       VARCHAR(150) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,

    CONSTRAINT uk_price_list_providers_code
        UNIQUE (code)
);


CREATE TABLE price_list_import_configs
(
    id                   BIGSERIAL PRIMARY KEY,
    provider_id          BIGINT       NOT NULL,
    name                 VARCHAR(100) NOT NULL,
    sheet_strategy       VARCHAR(30)  NOT NULL,
    sheet_index          INTEGER,
    sheet_name           VARCHAR(200),
    header_strategy      VARCHAR(30)  NOT NULL,
    header_row_index     INTEGER,
    first_data_row_index INTEGER      NOT NULL,
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,

    CONSTRAINT fk_price_list_import_configs_provider
        FOREIGN KEY (provider_id)
            REFERENCES price_list_providers (id)
);


CREATE TABLE price_list_column_mappings
(
    id               BIGSERIAL PRIMARY KEY,
    import_config_id BIGINT      NOT NULL,
    target_field     VARCHAR(50) NOT NULL,
    column_index     INTEGER     NOT NULL,
    expected_header  VARCHAR(150),
    value_type       VARCHAR(30) NOT NULL,
    required         BOOLEAN     NOT NULL DEFAULT FALSE,
    active           BOOLEAN     NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_price_list_column_mappings_config
        FOREIGN KEY (import_config_id)
            REFERENCES price_list_import_configs (id),

    CONSTRAINT uk_price_list_column_mapping_field
        UNIQUE (import_config_id, target_field)
);


CREATE INDEX idx_price_list_import_configs_provider
    ON price_list_import_configs (provider_id);


CREATE INDEX idx_price_list_column_mappings_config
    ON price_list_column_mappings (import_config_id);

ALTER TABLE price_list_import_configs
    ADD CONSTRAINT chk_price_list_import_configs_sheet
        CHECK (
            (sheet_strategy = 'FIRST'
                AND sheet_index IS NULL
                AND sheet_name IS NULL)
                OR
            (sheet_strategy = 'BY_INDEX'
                AND sheet_index IS NOT NULL)
                OR
            (sheet_strategy IN ('BY_NAME', 'NAME_CONTAINS')
                AND sheet_name IS NOT NULL)
            );


ALTER TABLE price_list_import_configs
    ADD CONSTRAINT chk_price_list_import_configs_header
        CHECK (
            (header_strategy = 'NONE'
                AND header_row_index IS NULL)
                OR
            (header_strategy = 'FIXED_ROW'
                AND header_row_index IS NOT NULL)
            );


ALTER TABLE price_list_import_configs
    ADD CONSTRAINT chk_price_list_import_configs_rows
        CHECK (
            first_data_row_index >= 0
                AND (
                header_row_index IS NULL
                    OR first_data_row_index > header_row_index
                )
            );


ALTER TABLE price_list_column_mappings
    ADD CONSTRAINT chk_price_list_column_mappings_index
        CHECK (column_index >= 0);


CREATE UNIQUE INDEX uk_price_list_import_configs_active_provider
    ON price_list_import_configs (provider_id)
    WHERE active = TRUE;