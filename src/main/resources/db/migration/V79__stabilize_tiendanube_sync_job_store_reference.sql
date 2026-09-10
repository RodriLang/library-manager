-- El store_id de Tiendanube es un identificador remoto y puede cambiar si una librería
-- desconecta una cuenta y conecta otra. Los jobs deben quedar vinculados al registro
-- interno de tiendanube_stores y conservar store_id sólo como snapshot remoto.

ALTER TABLE tiendanube_sync_jobs
    ADD COLUMN tiendanube_store_id BIGINT;

UPDATE tiendanube_sync_jobs job
SET tiendanube_store_id = store.id
FROM tiendanube_stores store
WHERE store.store_id = job.store_id;

DO
$$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM tiendanube_sync_jobs
        WHERE tiendanube_store_id IS NULL
    ) THEN
        RAISE EXCEPTION 'No se pudo resolver tiendanube_store_id para todos los jobs existentes';
    END IF;
END
$$;

ALTER TABLE tiendanube_sync_jobs
    ALTER COLUMN tiendanube_store_id SET NOT NULL;

ALTER TABLE tiendanube_sync_jobs
    DROP CONSTRAINT fk_tiendanube_sync_jobs_store;

ALTER TABLE tiendanube_sync_jobs
    ADD CONSTRAINT fk_tiendanube_sync_jobs_store
        FOREIGN KEY (tiendanube_store_id) REFERENCES tiendanube_stores (id) ON DELETE CASCADE;

CREATE INDEX idx_tiendanube_sync_jobs_internal_store
    ON tiendanube_sync_jobs (tiendanube_store_id, status, created_at DESC);
