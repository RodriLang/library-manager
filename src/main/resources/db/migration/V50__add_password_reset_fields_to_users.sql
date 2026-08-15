ALTER TABLE users
    ADD COLUMN password_reset_token VARCHAR(255);

ALTER TABLE users
    ADD COLUMN password_reset_token_expiration TIMESTAMP WITH TIME ZONE;