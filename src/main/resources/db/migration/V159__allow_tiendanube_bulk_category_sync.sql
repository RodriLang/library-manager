-- Nueva operación masiva, con jobs independientes: no modifica precio, stock ni portada.
ALTER TABLE tiendanube_bulk_operations
    DROP CONSTRAINT ck_tiendanube_bulk_operations_action;
ALTER TABLE tiendanube_bulk_operations
    ADD CONSTRAINT ck_tiendanube_bulk_operations_action CHECK (action IN (
        'PUBLISH', 'SYNC_PUBLICATION', 'SYNC_CATEGORIES', 'SYNC_STOCK',
        'SYNC_PRICE', 'DELETE_PUBLICATION', 'UNLINK',
        'ENABLE_PRICE_SYNC', 'DISABLE_PRICE_SYNC'
    ));

ALTER TABLE tiendanube_sync_jobs
    DROP CONSTRAINT ck_tiendanube_sync_jobs_type;
ALTER TABLE tiendanube_sync_jobs
    ADD CONSTRAINT ck_tiendanube_sync_jobs_type CHECK (type IN (
        'PUBLISH', 'SYNC_STOCK', 'SYNC_PRICE', 'SYNC_PUBLICATION',
        'SYNC_CATEGORIES', 'DELETE_PUBLICATION'
    ));
