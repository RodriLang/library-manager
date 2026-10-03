ALTER TABLE inventory_price_import_items
    ADD COLUMN IF NOT EXISTS selected_for_apply BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE inventory_price_imports
    ADD COLUMN IF NOT EXISTS processing_started_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS processing_finished_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS skipped_rows INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS processing_error TEXT;

UPDATE inventory_price_imports
SET processing_finished_at = applied_at
WHERE status = 'APPLIED'
  AND processing_finished_at IS NULL;
