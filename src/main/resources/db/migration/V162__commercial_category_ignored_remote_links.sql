-- Categorías desvinculadas intencionalmente o eliminadas solo de Anaquel.
-- Impide que una importación posterior vuelva a vincularlas sin confirmación.
CREATE TABLE IF NOT EXISTS bookstore_ignored_tiendanube_categories (
    bookstore_id BIGINT NOT NULL,
    tiendanube_category_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (bookstore_id, tiendanube_category_id)
);
