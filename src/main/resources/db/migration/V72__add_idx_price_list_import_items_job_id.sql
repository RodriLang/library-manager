CREATE INDEX idx_price_list_import_items_job_id
    ON price_list_import_items (
                                job_id,
                                id
        );