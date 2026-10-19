CREATE TABLE tiendanube_publication_templates (
    bookstore_id BIGINT PRIMARY KEY REFERENCES bookstores(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    commercial_message VARCHAR(4000) NOT NULL DEFAULT '',
    include_isbn BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
