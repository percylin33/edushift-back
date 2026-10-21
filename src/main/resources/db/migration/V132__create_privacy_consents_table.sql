-- Flyway Migration V132: Create privacy_consents table
-- Compliance with Ley N° 29733 (Art. 28 Reglamento D.S. 003-2013-JUS - Datos de Menores)

CREATE TABLE edushift.privacy_consents (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    public_uuid UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    guardian_user_id UUID NOT NULL,
    student_id UUID NOT NULL,
    policy_version VARCHAR(20) NOT NULL DEFAULT 'v1.0',
    consent_granted BOOLEAN NOT NULL DEFAULT TRUE,
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(45),
    user_agent TEXT,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(128),
    updated_by VARCHAR(128)
);

CREATE INDEX idx_privacy_consents_tenant ON edushift.privacy_consents(tenant_id);
CREATE INDEX idx_privacy_consents_student ON edushift.privacy_consents(tenant_id, student_id);
CREATE INDEX idx_privacy_consents_guardian ON edushift.privacy_consents(tenant_id, guardian_user_id);
