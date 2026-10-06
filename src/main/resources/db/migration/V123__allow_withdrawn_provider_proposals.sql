-- Permite retirar una propuesta de proveedor creada por una librería antes de su aprobación global.
-- El registro se conserva para mantener la trazabilidad histórica, pero deja de estar operativo.
ALTER TABLE providers
    DROP CONSTRAINT IF EXISTS chk_providers_verification_status;

ALTER TABLE providers
    ADD CONSTRAINT chk_providers_verification_status
        CHECK (verification_status IN ('VERIFIED', 'PENDING_REVIEW', 'REJECTED', 'WITHDRAWN'));
