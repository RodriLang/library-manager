-- Categorías importadas desde Tiendanube siguen siendo administradas desde ese canal.
-- Las vinculaciones creadas manualmente preservan nombre y jerarquía propios de Anaquel.
ALTER TABLE bookstore_commercial_categories
    ADD COLUMN remote_managed BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE bookstore_commercial_categories
SET remote_managed = TRUE
WHERE tiendanube_category_id IS NOT NULL;
