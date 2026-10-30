-- EduShift-owned RAG corpus. These tables intentionally have no tenant_id:
-- only the platform SUPER_ADMIN API writes them, while tenant retrieval can
-- read published, audience-eligible entries alongside the current tenant's corpus.
CREATE TABLE edushift.ai_platform_source_documents (
    id uuid PRIMARY KEY,
    public_uuid uuid NOT NULL UNIQUE,
    title varchar(240) NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'INDEXING',
    audiences jsonb NOT NULL,
    source_url varchar(1000) NOT NULL,
    source_version varchar(120) NOT NULL,
    effective_at date,
    review_after date NOT NULL,
    storage_provider varchar(20) NOT NULL,
    storage_remote_key varchar(1000) NOT NULL,
    original_name varchar(240) NOT NULL,
    content_type varchar(160) NOT NULL,
    size_bytes bigint NOT NULL,
    checksum_sha256 varchar(64) NOT NULL,
    text_bytes bigint NOT NULL DEFAULT 0,
    index_error_code varchar(60),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    updated_by uuid,
    deleted boolean NOT NULL DEFAULT false,
    deleted_at timestamptz,
    CONSTRAINT chk_ai_platform_source_status CHECK (status IN
        ('INDEXING','PROCESSING','READY','PUBLISHED','NEEDS_OCR','FAILED','RETIRED')),
    CONSTRAINT chk_ai_platform_source_audiences CHECK (jsonb_typeof(audiences) = 'array'),
    CONSTRAINT chk_ai_platform_source_size CHECK (size_bytes BETWEEN 1 AND 25000000),
    CONSTRAINT chk_ai_platform_source_text_bytes CHECK (text_bytes >= 0),
    CONSTRAINT chk_ai_platform_source_deleted_at CHECK (
        (deleted = false AND deleted_at IS NULL)
        OR (deleted = true AND deleted_at IS NOT NULL))
);

CREATE INDEX idx_ai_platform_source_documents_status
    ON edushift.ai_platform_source_documents(status, updated_at)
    WHERE deleted = false;
CREATE INDEX idx_ai_platform_source_documents_published
    ON edushift.ai_platform_source_documents(review_after, effective_at)
    WHERE deleted = false AND status = 'PUBLISHED';
CREATE INDEX idx_ai_platform_source_documents_checksum
    ON edushift.ai_platform_source_documents(checksum_sha256)
    WHERE deleted = false;

CREATE TABLE edushift.ai_platform_source_chunks (
    id uuid PRIMARY KEY,
    document_id uuid NOT NULL REFERENCES edushift.ai_platform_source_documents(id)
        ON DELETE CASCADE,
    ordinal integer NOT NULL,
    page_number integer,
    content text NOT NULL,
    search_vector tsvector GENERATED ALWAYS AS
        (to_tsvector('spanish'::regconfig, content)) STORED,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    updated_by uuid,
    deleted boolean NOT NULL DEFAULT false,
    deleted_at timestamptz,
    CONSTRAINT uq_ai_platform_source_chunks_ordinal UNIQUE (document_id, ordinal),
    CONSTRAINT chk_ai_platform_source_chunks_ordinal CHECK (ordinal >= 0),
    CONSTRAINT chk_ai_platform_source_chunks_page CHECK (page_number IS NULL OR page_number > 0),
    CONSTRAINT chk_ai_platform_source_chunks_deleted_at CHECK (
        (deleted = false AND deleted_at IS NULL)
        OR (deleted = true AND deleted_at IS NOT NULL))
);

CREATE INDEX idx_ai_platform_source_chunks_document
    ON edushift.ai_platform_source_chunks(document_id) WHERE deleted = false;
CREATE INDEX idx_ai_platform_source_chunks_search
    ON edushift.ai_platform_source_chunks USING gin(search_vector) WHERE deleted = false;

CREATE TRIGGER set_updated_at_ai_platform_source_documents
    BEFORE UPDATE ON edushift.ai_platform_source_documents
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();
CREATE TRIGGER set_updated_at_ai_platform_source_chunks
    BEFORE UPDATE ON edushift.ai_platform_source_chunks
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON TABLE edushift.ai_platform_source_documents IS
    'EduShift-managed national RAG corpus, separate from every tenant-owned source.';
COMMENT ON TABLE edushift.ai_platform_source_chunks IS
    'Searchable passages for the published EduShift-managed national RAG corpus.';
COMMENT ON COLUMN edushift.ai_platform_source_documents.storage_remote_key IS
    'Object stored under the edushift-system storage scope; not a tenant file reference.';
