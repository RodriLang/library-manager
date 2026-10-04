CREATE INDEX IF NOT EXISTS idx_bookstore_invitations_status
    ON bookstore_invitations(created_at DESC, used_at, revoked_at, expires_at);
