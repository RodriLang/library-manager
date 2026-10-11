ALTER TABLE notifications
    ADD COLUMN in_app_visible BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE bookstore_notification_settings (
    bookstore_id BIGINT PRIMARY KEY REFERENCES bookstores(id) ON DELETE CASCADE,
    email_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    email_address VARCHAR(320),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE bookstore_notification_preferences (
    id BIGSERIAL PRIMARY KEY,
    bookstore_id BIGINT NOT NULL REFERENCES bookstores(id) ON DELETE CASCADE,
    event_type VARCHAR(60) NOT NULL,
    in_app_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    email_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_bookstore_notification_preference UNIQUE (bookstore_id, event_type)
);

CREATE INDEX idx_bookstore_notification_preferences_bookstore
    ON bookstore_notification_preferences(bookstore_id);

CREATE TABLE notification_delivery_outbox (
    id BIGSERIAL PRIMARY KEY,
    notification_id BIGINT NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
    channel VARCHAR(30) NOT NULL,
    recipient VARCHAR(320) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error VARCHAR(1000),
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_notification_delivery_channel UNIQUE (notification_id, channel),
    CONSTRAINT ck_notification_delivery_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_notification_delivery_due
    ON notification_delivery_outbox(status, next_attempt_at, created_at);
