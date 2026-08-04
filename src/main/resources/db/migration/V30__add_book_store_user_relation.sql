ALTER TABLE users
    ADD COLUMN bookstore_id BIGINT;

UPDATE users
SET bookstore_id = 1
WHERE bookstore_id IS NULL;

ALTER TABLE users
    ALTER COLUMN bookstore_id SET NOT NULL;

ALTER TABLE users
    ADD CONSTRAINT fk_users_bookstore
        FOREIGN KEY (bookstore_id)
            REFERENCES bookstores (id)
            ON DELETE RESTRICT;

CREATE INDEX idx_users_bookstore_id
    ON users (bookstore_id);