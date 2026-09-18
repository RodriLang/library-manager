CREATE TABLE bookstore_fiscal_settings
(
    id                      BIGSERIAL PRIMARY KEY,
    bookstore_id            BIGINT       NOT NULL,
    cuit                    VARCHAR(11)  NOT NULL,
    legal_name              VARCHAR(200) NOT NULL,
    tax_condition           VARCHAR(40)  NOT NULL,
    gross_income_number     VARCHAR(50)  NOT NULL,
    activity_start_date     DATE         NOT NULL,
    fiscal_address          VARCHAR(250) NOT NULL,
    city                    VARCHAR(120) NOT NULL,
    province                VARCHAR(120) NOT NULL,
    postal_code             VARCHAR(20),
    point_of_sale           INTEGER      NOT NULL,
    arca_status             VARCHAR(40)  NOT NULL DEFAULT 'PENDING_AUTHORIZATION',
    verified_at             TIMESTAMPTZ,
    last_verification_error VARCHAR(1000),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_bookstore_fiscal_settings_bookstore
        FOREIGN KEY (bookstore_id) REFERENCES bookstores (id),
    CONSTRAINT uk_bookstore_fiscal_settings_bookstore UNIQUE (bookstore_id),
    CONSTRAINT uk_bookstore_fiscal_settings_cuit_pos UNIQUE (cuit, point_of_sale),
    CONSTRAINT ck_bookstore_fiscal_settings_cuit CHECK (cuit ~ '^[0-9]{11}$'),
    CONSTRAINT ck_bookstore_fiscal_settings_point_of_sale CHECK (point_of_sale BETWEEN 1 AND 99999)
);

CREATE TABLE fiscal_documents
(
    id                           BIGSERIAL PRIMARY KEY,
    bookstore_id                 BIGINT         NOT NULL,
    sale_id                      BIGINT         NOT NULL,
    created_by_user_id           BIGINT         NOT NULL,
    document_type                VARCHAR(30)    NOT NULL,
    status                       VARCHAR(40)    NOT NULL,
    voucher_class                VARCHAR(5)     NOT NULL,
    voucher_type_code            INTEGER        NOT NULL,
    point_of_sale                INTEGER        NOT NULL,
    voucher_number               BIGINT,
    issue_date                   DATE           NOT NULL,
    currency                     VARCHAR(3)     NOT NULL DEFAULT 'PES',
    exchange_rate                NUMERIC(18, 6) NOT NULL DEFAULT 1,
    net_amount                   NUMERIC(14, 2) NOT NULL DEFAULT 0,
    exempt_amount                NUMERIC(14, 2) NOT NULL DEFAULT 0,
    vat_amount                   NUMERIC(14, 2) NOT NULL DEFAULT 0,
    tax_amount                   NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_amount                 NUMERIC(14, 2) NOT NULL,
    recipient_vat_condition      VARCHAR(40)    NOT NULL,
    recipient_vat_condition_id   INTEGER        NOT NULL,
    recipient_document_type      VARCHAR(30)    NOT NULL,
    recipient_document_type_code INTEGER        NOT NULL,
    recipient_document_number    VARCHAR(20),
    recipient_name               VARCHAR(200),
    recipient_address            VARCHAR(250),
    cae                          VARCHAR(20),
    cae_expiration_date          DATE,
    authorized_at                TIMESTAMPTZ,
    arca_result                  VARCHAR(5),
    arca_observations            VARCHAR(2000),
    arca_errors                  VARCHAR(2000),
    qr_url                       TEXT,
    created_at                   TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at                   TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_fiscal_documents_bookstore
        FOREIGN KEY (bookstore_id) REFERENCES bookstores (id),
    CONSTRAINT fk_fiscal_documents_sale
        FOREIGN KEY (sale_id) REFERENCES sales (id),
    CONSTRAINT fk_fiscal_documents_created_by
        FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CONSTRAINT uk_fiscal_documents_sale_type UNIQUE (sale_id, document_type),
    CONSTRAINT ck_fiscal_documents_point_of_sale CHECK (point_of_sale BETWEEN 1 AND 99999),
    CONSTRAINT ck_fiscal_documents_total CHECK (total_amount >= 0)
);

CREATE UNIQUE INDEX uk_fiscal_documents_voucher
    ON fiscal_documents (bookstore_id, voucher_type_code, point_of_sale, voucher_number)
    WHERE voucher_number IS NOT NULL;

CREATE INDEX idx_fiscal_documents_bookstore_created
    ON fiscal_documents (bookstore_id, created_at DESC);

CREATE INDEX idx_fiscal_documents_sale
    ON fiscal_documents (sale_id);

CREATE INDEX idx_fiscal_documents_status
    ON fiscal_documents (bookstore_id, status);
