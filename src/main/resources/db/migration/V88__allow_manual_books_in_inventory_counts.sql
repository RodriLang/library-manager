ALTER TABLE inventory_count_items
    ALTER COLUMN raw_identifier DROP NOT NULL,
    ALTER COLUMN normalized_identifier DROP NOT NULL;

ALTER TABLE inventory_count_items
    DROP CONSTRAINT uk_inventory_count_items_identifier;

CREATE UNIQUE INDEX ux_inventory_count_items_session_identifier
    ON inventory_count_items (session_id, normalized_identifier)
    WHERE normalized_identifier IS NOT NULL;

CREATE UNIQUE INDEX ux_inventory_count_items_session_book
    ON inventory_count_items (session_id, book_id)
    WHERE book_id IS NOT NULL;