CREATE TABLE bookstore_excluded_publishers
(
    id           BIGSERIAL PRIMARY KEY,
    bookstore_id BIGINT      NOT NULL,
    publisher_id BIGINT      NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_bookstore_excluded_publishers_bookstore
        FOREIGN KEY (bookstore_id)
            REFERENCES bookstores (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_bookstore_excluded_publishers_publisher
        FOREIGN KEY (publisher_id)
            REFERENCES publishers (id)
            ON DELETE CASCADE,

    CONSTRAINT uk_bookstore_excluded_publisher
        UNIQUE (bookstore_id, publisher_id)
);

CREATE INDEX idx_bookstore_excluded_publishers_publisher
    ON bookstore_excluded_publishers (publisher_id);