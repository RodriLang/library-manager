CREATE TABLE bookstore_invitations
(
    id           BIGSERIAL PRIMARY KEY,

    bookstore_id BIGINT                   NOT NULL,

    email        VARCHAR(150),

    role         VARCHAR(50)              NOT NULL,

    token_hash   VARCHAR(64)              NOT NULL,

    expires_at   TIMESTAMP WITH TIME ZONE NOT NULL,

    used_at      TIMESTAMP WITH TIME ZONE,

    revoked_at   TIMESTAMP WITH TIME ZONE,

    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    created_by   BIGINT,

    CONSTRAINT fk_bookstore_invitation_bookstore
        FOREIGN KEY (bookstore_id)
            REFERENCES bookstores (id),

    CONSTRAINT fk_bookstore_invitation_created_by
        FOREIGN KEY (created_by)
            REFERENCES users (id),

    CONSTRAINT uq_bookstore_invitation_token_hash
        UNIQUE (token_hash)
);

CREATE INDEX idx_bookstore_invitations_bookstore
    ON bookstore_invitations (bookstore_id);

CREATE INDEX idx_bookstore_invitations_email
    ON bookstore_invitations (email);

CREATE INDEX idx_bookstore_invitations_expires_at
    ON bookstore_invitations (expires_at);