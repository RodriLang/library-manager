CREATE UNIQUE INDEX uk_purchase_orders_draft_bookstore_provider
    ON purchase_orders (bookstore_id, provider_id)
    WHERE status = 'DRAFT';