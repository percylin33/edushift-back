ALTER TABLE edushift.ai_source_documents
    DROP CONSTRAINT chk_ai_source_status;

ALTER TABLE edushift.ai_source_documents
    ADD CONSTRAINT chk_ai_source_status CHECK (status IN
        ('INDEXING','PROCESSING','READY','PUBLISHED','NEEDS_OCR','FAILED','RETIRED'));

ALTER TABLE edushift.ai_source_documents
    ADD COLUMN index_error_code varchar(60);

CREATE INDEX idx_ai_source_documents_index_queue
    ON edushift.ai_source_documents(status, updated_at)
    WHERE deleted = false AND status IN ('INDEXING','PROCESSING');
