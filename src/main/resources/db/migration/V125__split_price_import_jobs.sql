CREATE TABLE inventory_price_import_jobs (
    id BIGSERIAL PRIMARY KEY,
    import_id BIGINT NOT NULL REFERENCES inventory_price_imports(id) ON DELETE CASCADE,
    job_type VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_rows INTEGER NOT NULL DEFAULT 0,
    processed_rows INTEGER NOT NULL DEFAULT 0,
    success_rows INTEGER NOT NULL DEFAULT 0,
    skipped_rows INTEGER NOT NULL DEFAULT 0,
    error_rows INTEGER NOT NULL DEFAULT 0,
    last_staging_id BIGINT NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    heartbeat_at TIMESTAMPTZ,
    error_message TEXT,
    requested_by_user_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_inventory_price_import_jobs UNIQUE (import_id, job_type),
    CONSTRAINT ck_inventory_price_import_job_type CHECK (job_type IN ('INVENTORY_PRICE_APPLY', 'PROVIDER_CATALOG_SYNC')),
    CONSTRAINT ck_inventory_price_import_job_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_inventory_price_import_job_counters CHECK (
        total_rows >= 0 AND processed_rows >= 0 AND success_rows >= 0 AND skipped_rows >= 0 AND error_rows >= 0
    )
);

CREATE INDEX idx_inventory_price_import_jobs_status_created
    ON inventory_price_import_jobs(status, created_at);

CREATE INDEX idx_inventory_price_import_jobs_import
    ON inventory_price_import_jobs(import_id);

-- The provider staging is now consumed asynchronously and must survive the
-- inventory-price APPLY. It is removed only after provider sync completion.
CREATE INDEX IF NOT EXISTS idx_inventory_price_import_provider_rows_import_id
    ON inventory_price_import_provider_rows(import_id, id);

-- Preview work was also in-memory before durable jobs. A stale preview cannot
-- resume because its temporary source file was local to the old JVM.
UPDATE inventory_price_imports
SET status = 'FAILED',
    processing_finished_at = NOW(),
    processing_error = COALESCE(processing_error, 'El preview quedó interrumpido antes de completarse.'),
    updated_at = NOW()
WHERE status = 'PREVIEW_PROCESSING'
  AND processing_started_at < NOW() - INTERVAL '30 minutes';

-- Recover legacy in-memory async applies that were left PROCESSING before the
-- durable job table existed. New jobs are protected by their persisted row.
UPDATE inventory_price_imports i
SET status = 'FAILED',
    processing_finished_at = NOW(),
    processing_error = COALESCE(processing_error, 'El proceso anterior quedó interrumpido antes de la migración a trabajos recuperables.'),
    updated_at = NOW()
WHERE i.status = 'PROCESSING'
  AND i.processing_started_at < NOW() - INTERVAL '30 minutes';

-- If there is already a fully staged list when this migration is deployed,
-- enqueue only the newest complete list per bookstore/provider. This makes the
-- production list that was already previewed useful without replaying every
-- historical upload of the same supplier.
WITH complete_imports AS (
    SELECT DISTINCT ON (i.bookstore_id, i.provider_id)
           i.id AS import_id,
           COUNT(r.id)::INTEGER AS staged_rows
    FROM inventory_price_imports i
    JOIN inventory_price_import_provider_rows r ON r.import_id = i.id
    WHERE i.provider_id IS NOT NULL
      AND i.total_rows > 0
      AND i.status IN ('PREVIEW_READY', 'PROCESSING', 'APPLIED', 'FAILED')
    GROUP BY i.id, i.bookstore_id, i.provider_id, i.effective_from
    HAVING COUNT(r.id) = MAX(i.total_rows)
    ORDER BY i.bookstore_id, i.provider_id, i.effective_from DESC, i.id DESC
)
INSERT INTO inventory_price_import_jobs (
    import_id, job_type, status, total_rows, processed_rows, success_rows,
    skipped_rows, error_rows, last_staging_id, created_at, updated_at
)
SELECT import_id, 'PROVIDER_CATALOG_SYNC', 'PENDING', staged_rows, 0, 0, 0, 0, 0, NOW(), NOW()
FROM complete_imports
ON CONFLICT (import_id, job_type) DO NOTHING;
