CREATE TABLE bookstore_book_provider_preferences (
    id BIGSERIAL PRIMARY KEY,
    bookstore_id BIGINT NOT NULL REFERENCES bookstores(id),
    book_id BIGINT NOT NULL REFERENCES books(id),
    provider_id BIGINT NOT NULL REFERENCES providers(id),
    source VARCHAR(30) NOT NULL,
    last_used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_bookstore_book_provider_preferences UNIQUE (bookstore_id, book_id),
    CONSTRAINT ck_bookstore_book_provider_preferences_source
        CHECK (source IN ('MANUAL', 'LAST_ORDER'))
);

CREATE INDEX idx_bookstore_book_provider_preferences_provider
    ON bookstore_book_provider_preferences(provider_id);

CREATE INDEX idx_bookstore_book_provider_preferences_book
    ON bookstore_book_provider_preferences(book_id);

-- Inicializa la preferencia con el último proveedor realmente utilizado
-- por cada librería/libro, considerando pedidos enviados y compras confirmadas.
WITH provider_usage AS (
    SELECT po.bookstore_id,
           poi.book_id,
           po.provider_id,
           COALESCE(po.sent_at, po.updated_at, po.created_at) AS used_at,
           po.id AS source_id
    FROM purchase_orders po
    JOIN purchase_order_items poi ON poi.purchase_order_id = po.id
    WHERE po.status = 'SENT'

    UNION ALL

    SELECT p.bookstore_id,
           pi.book_id,
           p.provider_id,
           COALESCE(p.updated_at, p.created_at) AS used_at,
           p.id AS source_id
    FROM purchases p
    JOIN purchase_items pi ON pi.purchase_id = p.id
    WHERE p.status = 'CONFIRMED'
),
latest_usage AS (
    SELECT DISTINCT ON (bookstore_id, book_id)
           bookstore_id,
           book_id,
           provider_id,
           used_at
    FROM provider_usage
    ORDER BY bookstore_id, book_id, used_at DESC, source_id DESC
)
INSERT INTO bookstore_book_provider_preferences (
    bookstore_id,
    book_id,
    provider_id,
    source,
    last_used_at,
    created_at,
    updated_at
)
SELECT bookstore_id,
       book_id,
       provider_id,
       'LAST_ORDER',
       used_at,
       now(),
       now()
FROM latest_usage;

UPDATE purchase_requirements requirement
SET preferred_provider_id = preference.provider_id,
    updated_at = now()
FROM bookstore_book_provider_preferences preference
WHERE requirement.bookstore_id = preference.bookstore_id
  AND requirement.book_id = preference.book_id
  AND requirement.status = 'PENDING'
  AND requirement.preferred_provider_id IS NULL;
