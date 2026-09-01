-- ============================================================
-- 1. METADATA DEL ARCHIVO IMPORTADO
-- ============================================================

ALTER TABLE price_list_import_jobs
    ADD COLUMN original_file_name VARCHAR(500);


-- ============================================================
-- 2. DETALLE/AUDITORÍA DE PRECIOS PROCESADOS
-- ============================================================

CREATE TABLE price_list_import_items
(
    id                 BIGSERIAL PRIMARY KEY,
    job_id             BIGINT         NOT NULL,
    book_id            BIGINT         NOT NULL,
    editorial_price_id BIGINT         NOT NULL,

    imported_price     NUMERIC(12, 2) NOT NULL,
    previous_price     NUMERIC(12, 2),

    operation          VARCHAR(20)    NOT NULL,
    price_change       VARCHAR(20)    NOT NULL,

    created_at         TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_price_list_import_items_job
        FOREIGN KEY (job_id)
            REFERENCES price_list_import_jobs (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_price_list_import_items_book
        FOREIGN KEY (book_id)
            REFERENCES books (id),

    CONSTRAINT fk_price_list_import_items_editorial_price
        FOREIGN KEY (editorial_price_id)
            REFERENCES editorial_prices (id),

    CONSTRAINT uk_price_list_import_items_job_book
        UNIQUE (job_id, book_id),

    CONSTRAINT chk_price_list_import_items_operation
        CHECK (
            operation IN (
                          'CREATED',
                          'UPDATED',
                          'UNCHANGED'
                )
            ),

    CONSTRAINT chk_price_list_import_items_price_change
        CHECK (
            price_change IN (
                             'FIRST_PRICE',
                             'INCREASED',
                             'DECREASED',
                             'UNCHANGED'
                )
            )
);


-- ============================================================
-- 3. ÍNDICES PARA LAS PANTALLAS FUTURAS
-- ============================================================

-- Detalle de una importación + filtro por resultado económico.
CREATE INDEX idx_price_list_import_items_job_change
    ON price_list_import_items (
                                job_id,
                                price_change
        );

-- Navegación desde un libro hacia sus importaciones.
CREATE INDEX idx_price_list_import_items_book_job
    ON price_list_import_items (
                                book_id,
                                job_id DESC
        );

-- Navegación desde un precio editorial hacia los eventos
-- de importación relacionados.
CREATE INDEX idx_price_list_import_items_editorial_price
    ON price_list_import_items (
                                editorial_price_id
        );