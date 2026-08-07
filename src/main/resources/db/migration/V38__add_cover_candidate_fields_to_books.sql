ALTER TABLE books
    ADD COLUMN cover_candidate_url             TEXT,
    ADD COLUMN cover_candidate_status          VARCHAR(30),
    ADD COLUMN cover_candidate_attempts        INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN cover_candidate_next_attempt_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN cover_candidate_error           TEXT,
    ADD COLUMN cover_candidate_started_at      TIMESTAMP WITH TIME ZONE;

ALTER TABLE books
    ADD CONSTRAINT ck_books_cover_candidate_attempts
        CHECK (cover_candidate_attempts >= 0);

CREATE INDEX idx_books_pending_cover_candidates
    ON books (
              cover_candidate_status,
              cover_candidate_next_attempt_at,
              id
        )
    WHERE cover_candidate_url IS NOT NULL;