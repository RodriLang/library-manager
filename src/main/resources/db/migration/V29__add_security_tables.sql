CREATE TABLE roles
(
    id        BIGSERIAL PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL,

    CONSTRAINT uk_roles_role_name
        UNIQUE (role_name)
);

CREATE TABLE users
(
    id             BIGSERIAL PRIMARY KEY,
    username       VARCHAR(150) NOT NULL,
    password       VARCHAR(255) NOT NULL,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    account_locked BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_users_username
        UNIQUE (username)
);

CREATE TABLE user_roles
(
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,

    CONSTRAINT pk_user_roles
        PRIMARY KEY (user_id, role_id),

    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_user_roles_role
        FOREIGN KEY (role_id)
            REFERENCES roles (id)
            ON DELETE RESTRICT
);

CREATE TABLE refresh_tokens
(
    id         VARCHAR(36) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    user_id    BIGINT      NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_at    TIMESTAMPTZ,

    revoked    BOOLEAN     NOT NULL DEFAULT FALSE,

    CONSTRAINT pk_refresh_tokens
        PRIMARY KEY (id),

    CONSTRAINT uk_refresh_tokens_token_hash
        UNIQUE (token_hash),

    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_user_id
    ON refresh_tokens (user_id);

CREATE INDEX idx_refresh_tokens_expires_at
    ON refresh_tokens (expires_at);

CREATE INDEX idx_refresh_tokens_user_active
    ON refresh_tokens (user_id, revoked);

INSERT INTO roles (role_name)
VALUES ('ADMIN'),
       ('BOOKSTORE_ADMIN'),
       ('BOOKSTORE_USER');