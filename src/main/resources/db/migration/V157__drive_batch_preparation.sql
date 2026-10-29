-- A selected Drive file is not eligible for batch analysis until its mapping is confirmed.
ALTER TABLE drive_price_import_batch_items ADD COLUMN format_id BIGINT REFERENCES bookstore_price_list_formats(id);
ALTER TABLE drive_price_import_batch_items ADD COLUMN mapping_json TEXT;
ALTER TABLE drive_price_import_batch_items ADD COLUMN effective_from DATE;
ALTER TABLE drive_price_import_batch_items ADD COLUMN prepared_at TIMESTAMPTZ;
ALTER TABLE drive_price_import_batch_items ADD COLUMN analysis_started_at TIMESTAMPTZ;
ALTER TABLE drive_price_import_batch_items ADD COLUMN analysis_finished_at TIMESTAMPTZ;
CREATE INDEX idx_drive_batch_items_pending ON drive_price_import_batch_items(batch_id, status);
