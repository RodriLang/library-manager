-- Modelo de acceso de Anaquel: roles globales, permisos y membresías por librería.
ALTER TABLE roles
    ADD COLUMN IF NOT EXISTS scope VARCHAR(20) NOT NULL DEFAULT 'BOOKSTORE',
    ADD COLUMN IF NOT EXISTS system_role BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE roles SET scope = 'PLATFORM' WHERE role_name = 'ADMIN';
UPDATE roles SET scope = 'BOOKSTORE' WHERE role_name IN ('BOOKSTORE_ADMIN', 'BOOKSTORE_USER');

CREATE TABLE IF NOT EXISTS permissions (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(120) NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL,
    scope VARCHAR(20) NOT NULL,
    CONSTRAINT ck_permissions_scope CHECK (scope IN ('PLATFORM', 'BOOKSTORE'))
);

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS bookstore_memberships (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    bookstore_id BIGINT NOT NULL REFERENCES bookstores(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_bookstore_memberships_user_bookstore UNIQUE (user_id, bookstore_id)
);

CREATE INDEX IF NOT EXISTS idx_bookstore_memberships_user
    ON bookstore_memberships(user_id, enabled);
CREATE INDEX IF NOT EXISTS idx_bookstore_memberships_bookstore
    ON bookstore_memberships(bookstore_id, enabled);

CREATE TABLE IF NOT EXISTS bookstore_membership_roles (
    membership_id BIGINT NOT NULL REFERENCES bookstore_memberships(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    PRIMARY KEY (membership_id, role_id)
);

-- Migración compatible: la librería actual pasa a ser una membresía.
INSERT INTO bookstore_memberships (user_id, bookstore_id, enabled)
SELECT u.id, u.bookstore_id, u.enabled
FROM users u
WHERE u.bookstore_id IS NOT NULL
ON CONFLICT (user_id, bookstore_id) DO NOTHING;

INSERT INTO bookstore_membership_roles (membership_id, role_id)
SELECT bm.id, ur.role_id
FROM bookstore_memberships bm
JOIN user_roles ur ON ur.user_id = bm.user_id
JOIN roles r ON r.id = ur.role_id
WHERE r.role_name IN ('BOOKSTORE_ADMIN', 'BOOKSTORE_USER')
ON CONFLICT DO NOTHING;

-- user_roles queda reservado para roles globales. Las membresías conservan los roles locales.
DELETE FROM user_roles ur
USING roles r
WHERE ur.role_id = r.id
  AND r.scope = 'BOOKSTORE';

-- Un ADMIN global ya no necesita pertenecer artificialmente a una librería.
ALTER TABLE users ALTER COLUMN bookstore_id DROP NOT NULL;

INSERT INTO permissions(code, description, scope) VALUES
('platform.dashboard.read', 'Ver el estado general de Anaquel', 'PLATFORM'),
('platform.users.read', 'Ver usuarios globales', 'PLATFORM'),
('platform.users.manage', 'Administrar usuarios globales', 'PLATFORM'),
('platform.access.manage', 'Administrar roles, permisos y membresías', 'PLATFORM'),
('platform.bookstores.read', 'Ver librerías', 'PLATFORM'),
('platform.bookstores.manage', 'Administrar librerías', 'PLATFORM'),
('platform.invitations.manage', 'Administrar invitaciones', 'PLATFORM'),
('catalog.books.read', 'Ver catálogo global', 'PLATFORM'),
('catalog.books.create', 'Crear libros globales', 'PLATFORM'),
('catalog.books.edit', 'Modificar libros globales', 'PLATFORM'),
('catalog.quality.read', 'Ver calidad del catálogo', 'PLATFORM'),
('catalog.enrichment.run', 'Ejecutar enriquecimiento del catálogo', 'PLATFORM'),
('prices.read', 'Ver precios globales', 'PLATFORM'),
('prices.manage', 'Administrar precios globales', 'PLATFORM'),
('prices.import', 'Importar listas de precios', 'PLATFORM'),
('providers.manage', 'Administrar proveedores globales', 'PLATFORM'),
('audit.read', 'Ver auditoría administrativa', 'PLATFORM'),
('bookstore.inventory.read', 'Ver inventario de librería', 'BOOKSTORE'),
('bookstore.inventory.manage', 'Administrar inventario de librería', 'BOOKSTORE'),
('bookstore.sales.manage', 'Administrar ventas', 'BOOKSTORE'),
('bookstore.purchasing.manage', 'Administrar compras y reposición', 'BOOKSTORE'),
('bookstore.users.manage', 'Administrar usuarios de la librería', 'BOOKSTORE'),
('bookstore.settings.manage', 'Administrar configuración de la librería', 'BOOKSTORE')
ON CONFLICT (code) DO NOTHING;

-- ADMIN recibe todos los permisos de plataforma.
INSERT INTO role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.role_name = 'ADMIN' AND p.scope = 'PLATFORM'
ON CONFLICT DO NOTHING;

-- Los roles locales reciben un conjunto inicial razonable.
INSERT INTO role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.role_name = 'BOOKSTORE_ADMIN' AND p.scope = 'BOOKSTORE'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
  'bookstore.inventory.read','bookstore.inventory.manage','bookstore.sales.manage','bookstore.purchasing.manage'
)
WHERE r.role_name = 'BOOKSTORE_USER'
ON CONFLICT DO NOTHING;
