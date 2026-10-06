-- Permite que cada librería proponga proveedores sin publicarlos automáticamente
-- al resto de Anaquel. Los proveedores existentes se consideran verificados.
ALTER TABLE providers
    ADD COLUMN IF NOT EXISTS verification_status VARCHAR(20) NOT NULL DEFAULT 'VERIFIED',
    ADD COLUMN IF NOT EXISTS source VARCHAR(20) NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN IF NOT EXISTS created_by_bookstore_id BIGINT REFERENCES bookstores(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS created_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS reviewed_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE providers
    ADD CONSTRAINT chk_providers_verification_status
        CHECK (verification_status IN ('VERIFIED', 'PENDING_REVIEW', 'REJECTED'));

ALTER TABLE providers
    ADD CONSTRAINT chk_providers_source
        CHECK (source IN ('LEGACY', 'ADMIN', 'BOOKSTORE'));

CREATE INDEX IF NOT EXISTS idx_providers_review_queue
    ON providers(verification_status, created_at DESC)
    WHERE type = 'COMMERCIAL';

CREATE INDEX IF NOT EXISTS idx_providers_created_by_bookstore
    ON providers(created_by_bookstore_id, verification_status)
    WHERE created_by_bookstore_id IS NOT NULL;
