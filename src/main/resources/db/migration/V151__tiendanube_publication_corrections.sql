CREATE TABLE tiendanube_publication_correction_jobs (
 id BIGSERIAL PRIMARY KEY, bookstore_id BIGINT NOT NULL REFERENCES bookstores(id), store_id BIGINT NOT NULL,
 action VARCHAR(30) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'PREVIEW', created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 started_at TIMESTAMPTZ, finished_at TIMESTAMPTZ
);
CREATE TABLE tiendanube_publication_correction_items (
 id BIGSERIAL PRIMARY KEY, job_id BIGINT NOT NULL REFERENCES tiendanube_publication_correction_jobs(id) ON DELETE CASCADE,
 product_id BIGINT NOT NULL, title TEXT, status VARCHAR(20) NOT NULL, reason TEXT,
 before_value TEXT, after_value TEXT, inventory_id BIGINT,
 UNIQUE(job_id, product_id)
);
CREATE INDEX idx_tn_correction_bookstore ON tiendanube_publication_correction_jobs(bookstore_id, created_at DESC);
