ALTER TABLE tiendanube_stores
    ALTER COLUMN access_token TYPE TEXT;


ALTER TABLE tiendanube_stores
    ALTER COLUMN token_type TYPE VARCHAR(20);

ALTER TABLE tiendanube_stores
    ALTER COLUMN scope TYPE VARCHAR(500);

ALTER TABLE tiendanube_stores
    ALTER COLUMN connected_at TYPE TIMESTAMP WITH TIME ZONE
        USING connected_at AT TIME ZONE 'America/Argentina/Buenos_Aires';


ALTER TABLE tiendanube_stores
    ALTER COLUMN active SET DEFAULT TRUE;

UPDATE tiendanube_stores
SET active = TRUE
WHERE active IS NULL;

ALTER TABLE tiendanube_stores
    ALTER COLUMN active SET NOT NULL;