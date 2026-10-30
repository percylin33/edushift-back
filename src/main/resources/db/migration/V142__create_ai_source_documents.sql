-- Sprint 21a: tenant-owned and curated official documents for grounded AI answers.
CREATE UNIQUE INDEX uq_lms_file_objects_tenant_public_uuid
    ON edushift.lms_file_objects(tenant_id, public_uuid);

CREATE TABLE edushift.ai_source_documents (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES edushift.tenants(id) ON DELETE RESTRICT,
    public_uuid uuid NOT NULL UNIQUE,
    file_public_uuid uuid NOT NULL,
    title varchar(240) NOT NULL,
    kind varchar(20) NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'READY',
    audiences jsonb NOT NULL,
    source_url varchar(1000),
    source_version varchar(120),
    effective_at date,
    review_after date,
    checksum_sha256 varchar(64) NOT NULL,
    text_bytes bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    updated_by uuid,
    deleted boolean NOT NULL DEFAULT false,
    deleted_at timestamptz,
    CONSTRAINT chk_ai_source_kind CHECK (kind IN ('INSTITUTIONAL','OFFICIAL')),
    CONSTRAINT chk_ai_source_status CHECK (status IN
        ('READY','PUBLISHED','NEEDS_OCR','FAILED','RETIRED')),
    CONSTRAINT chk_ai_source_audiences CHECK (jsonb_typeof(audiences) = 'array'),
    CONSTRAINT chk_ai_source_text_bytes CHECK (text_bytes >= 0),
    CONSTRAINT fk_ai_source_file_tenant
        FOREIGN KEY (tenant_id, file_public_uuid)
        REFERENCES edushift.lms_file_objects(tenant_id, public_uuid) ON DELETE RESTRICT,
    CONSTRAINT chk_ai_source_deleted_at CHECK (
        (deleted = false AND deleted_at IS NULL)
        OR (deleted = true AND deleted_at IS NOT NULL))
);

CREATE UNIQUE INDEX uq_ai_source_documents_id_tenant
    ON edushift.ai_source_documents(id, tenant_id);
CREATE INDEX idx_ai_source_documents_tenant_status
    ON edushift.ai_source_documents(tenant_id, status) WHERE deleted = false;
CREATE UNIQUE INDEX uq_ai_source_documents_tenant_file_active
    ON edushift.ai_source_documents(tenant_id, file_public_uuid) WHERE deleted = false;

CREATE TABLE edushift.ai_source_chunks (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES edushift.tenants(id) ON DELETE RESTRICT,
    document_id uuid NOT NULL,
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
    CONSTRAINT fk_ai_source_chunks_document_tenant
        FOREIGN KEY (document_id, tenant_id)
        REFERENCES edushift.ai_source_documents(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT uq_ai_source_chunks_ordinal UNIQUE (tenant_id, document_id, ordinal),
    CONSTRAINT chk_ai_source_chunks_ordinal CHECK (ordinal >= 0),
    CONSTRAINT chk_ai_source_chunks_page CHECK (page_number IS NULL OR page_number > 0),
    CONSTRAINT chk_ai_source_chunks_deleted_at CHECK (
        (deleted = false AND deleted_at IS NULL)
        OR (deleted = true AND deleted_at IS NOT NULL))
);

CREATE INDEX idx_ai_source_chunks_tenant_document
    ON edushift.ai_source_chunks(tenant_id, document_id) WHERE deleted = false;
CREATE INDEX idx_ai_source_chunks_search
    ON edushift.ai_source_chunks USING gin(search_vector) WHERE deleted = false;

CREATE TRIGGER set_updated_at_ai_source_documents
    BEFORE UPDATE ON edushift.ai_source_documents
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();
CREATE TRIGGER set_updated_at_ai_source_chunks
    BEFORE UPDATE ON edushift.ai_source_chunks
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON TABLE edushift.ai_source_documents IS
    'Approved school or curated official sources, isolated per tenant.';
COMMENT ON TABLE edushift.ai_source_chunks IS
    'Extracted searchable text; joins require the same tenant on both tables.';
