-- Flyway Migration V133: Create claims_book_entries table
-- Compliance with Ley N° 32495 (Libro de Reclamaciones Virtual - Perú)
-- Original migration, checksum preserved for Flyway history

CREATE TABLE edushift.claims_book_entries (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    claim_code VARCHAR(30) NOT NULL UNIQUE,
    consumer_name VARCHAR(200) NOT NULL,
    consumer_doc_type VARCHAR(20) NOT NULL,
    consumer_doc_number VARCHAR(20) NOT NULL,
    consumer_email VARCHAR(254) NOT NULL,
    consumer_phone VARCHAR(32) NOT NULL,
    consumer_address VARCHAR(300) NOT NULL,
    is_minor BOOLEAN NOT NULL DEFAULT FALSE,
    parent_guardian_name VARCHAR(200),
    contracted_service VARCHAR(200) NOT NULL,
    claim_amount NUMERIC(12, 2),
    claim_type VARCHAR(20) NOT NULL,
    detail_description TEXT NOT NULL,
    consumer_request TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    due_date DATE NOT NULL,
    response_text TEXT,
    responded_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(128),
    updated_by VARCHAR(128),
    CONSTRAINT chk_claims_book_type CHECK (claim_type IN ('RECLAMO', 'QUEJA')),
    CONSTRAINT chk_claims_book_status CHECK (status IN ('PENDING', 'IN_PROCESS', 'ATTENDED'))
);

CREATE INDEX idx_claims_book_code ON edushift.claims_book_entries(claim_code);
CREATE INDEX idx_claims_book_tenant ON edushift.claims_book_entries(tenant_id);
CREATE INDEX idx_claims_book_status_due ON edushift.claims_book_entries(status, due_date);