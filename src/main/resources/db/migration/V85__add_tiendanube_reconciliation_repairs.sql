ALTER TABLE tiendanube_reconciliation_items
    ADD COLUMN repair_source VARCHAR(20),
    ADD COLUMN repair_job_id BIGINT,
    ADD COLUMN repair_requested_at TIMESTAMPTZ,
    ADD COLUMN repair_error_type VARCHAR(100),
    ADD COLUMN repair_error_message TEXT;

ALTER TABLE tiendanube_reconciliation_items
    ADD CONSTRAINT fk_tiendanube_reconciliation_items_repair_job
        FOREIGN KEY (repair_job_id)
        REFERENCES tiendanube_sync_jobs(id)
        ON DELETE SET NULL;

ALTER TABLE tiendanube_reconciliation_items
    ADD CONSTRAINT ck_tiendanube_reconciliation_items_repair_source
        CHECK (repair_source IS NULL OR repair_source IN ('MANUAL', 'AUTOMATIC'));

CREATE INDEX idx_tiendanube_reconciliation_items_repair_job
    ON tiendanube_reconciliation_items (repair_job_id)
    WHERE repair_job_id IS NOT NULL;

CREATE INDEX idx_tiendanube_reconciliation_items_repair_pending
    ON tiendanube_reconciliation_items (run_id, id)
    WHERE issue_type IN ('STOCK_MISMATCH', 'PRICE_MISMATCH')
      AND repair_requested_at IS NULL;
