CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    bookstore_id BIGINT NOT NULL REFERENCES bookstores(id) ON DELETE CASCADE,
    type VARCHAR(60) NOT NULL,
    source VARCHAR(40) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message VARCHAR(500) NOT NULL,
    entity_type VARCHAR(60),
    entity_id BIGINT,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notifications_bookstore_created_at
    ON notifications(bookstore_id, created_at DESC);

CREATE INDEX idx_notifications_bookstore_unread
    ON notifications(bookstore_id)
    WHERE read_at IS NULL;

CREATE UNIQUE INDEX uq_notifications_business_event
    ON notifications(bookstore_id, type, source, entity_type, entity_id)
    WHERE entity_type IS NOT NULL AND entity_id IS NOT NULL;
