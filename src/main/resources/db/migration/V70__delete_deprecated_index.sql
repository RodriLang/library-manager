-- El índice sobre (provider_id, reported_isbn) fue creado originalmente
-- para consultas por el ISBN informado por el proveedor.
--
-- Actualmente reported_isbn se conserva como dato de trazabilidad durante
-- la importación, pero no existen consultas que filtren por esta combinación.
-- El índice tampoco registra usos en producción desde el último reset de
-- estadísticas y ocupa aproximadamente 14 MB.

DROP INDEX IF EXISTS idx_provider_books_reported_isbn;


-- El índice trigram global sobre books.title ya no es utilizado
-- por las búsquedas generales, que actualmente usan title_search + FTS.
--
-- La única consulta restante que utiliza immutable_unaccent(lower(title))
-- está acotada por publisher_id, y PostgreSQL utiliza
-- idx_books_publisher_id en lugar de este índice.
--
-- Las estadísticas registran 0 scans desde 2026-07-24.

DROP INDEX IF EXISTS idx_books_search_title_trgm;



DROP INDEX IF EXISTS idx_provider_books_provider;