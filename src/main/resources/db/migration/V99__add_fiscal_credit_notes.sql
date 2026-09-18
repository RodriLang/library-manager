ALTER TABLE fiscal_documents
    ADD COLUMN associated_document_id BIGINT,
    ADD COLUMN reason VARCHAR(500);

ALTER TABLE fiscal_documents
    ADD CONSTRAINT fk_fiscal_documents_associated_document
        FOREIGN KEY (associated_document_id) REFERENCES fiscal_documents (id);

CREATE UNIQUE INDEX uk_fiscal_documents_associated_document
    ON fiscal_documents (associated_document_id)
    WHERE associated_document_id IS NOT NULL;

CREATE INDEX idx_fiscal_documents_bookstore_issue_date
    ON fiscal_documents (bookstore_id, issue_date DESC, id DESC);
