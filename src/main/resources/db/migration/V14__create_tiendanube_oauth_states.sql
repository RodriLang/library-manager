CREATE TABLE tiendanube_oauth_states
(
    id           BIGSERIAL PRIMARY KEY,
    state_hash   VARCHAR(64)              NOT NULL,
    bookstore_id BIGINT                   NOT NULL,
    expires_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at      TIMESTAMP WITH TIME ZONE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT uk_tiendanube_oauth_state_hash
        UNIQUE (state_hash),

    CONSTRAINT fk_tiendanube_oauth_state_bookstore
        FOREIGN KEY (bookstore_id)
            REFERENCES bookstores (id)
            ON DELETE CASCADE
);

CREATE INDEX idx_tiendanube_oauth_states_expires_at
    ON tiendanube_oauth_states (expires_at);

CREATE INDEX idx_tiendanube_oauth_states_bookstore_id
    ON tiendanube_oauth_states (bookstore_id);


-- bookstore_id ya fue creada en una migración anterior.

UPDATE tiendanube_stores
SET bookstore_id = 1
WHERE bookstore_id IS NULL;

ALTER TABLE tiendanube_stores
    ALTER COLUMN bookstore_id SET NOT NULL;

ALTER TABLE tiendanube_stores
    ADD CONSTRAINT uk_tiendanube_store_bookstore
        UNIQUE (bookstore_id);

ALTER TABLE tiendanube_stores
    ADD CONSTRAINT fk_tiendanube_store_bookstore
        FOREIGN KEY (bookstore_id)
            REFERENCES bookstores (id);

ALTER TABLE tiendanube_stores
    ADD COLUMN last_verified_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE tiendanube_stores
    ADD COLUMN last_error TEXT;