ALTER TABLE users
    ADD COLUMN email      VARCHAR(150),
    ADD COLUMN first_name VARCHAR(80),
    ADD COLUMN last_name  VARCHAR(80);

UPDATE users
SET email      = username || '+' || id || '@local.invalid',
    first_name = INITCAP(username),
    last_name  = 'Usuario'
WHERE email IS NULL;

ALTER TABLE users
    ALTER COLUMN email SET NOT NULL,
    ALTER COLUMN first_name SET NOT NULL,
    ALTER COLUMN last_name SET NOT NULL;

ALTER TABLE users
    ADD CONSTRAINT uk_users_email UNIQUE (email);