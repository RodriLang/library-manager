ALTER TABLE refresh_tokens
    RENAME COLUMN used_at TO revoked_at;