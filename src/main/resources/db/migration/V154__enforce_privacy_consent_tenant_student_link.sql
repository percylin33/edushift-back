-- =============================================================================
-- V154 — Enforce the tenant boundary for privacy-consent student references.
-- Consent endpoints now accept a student's public UUID and verify that the
-- authenticated parent has an active guardian link before reading or writing.
-- =============================================================================

CREATE UNIQUE INDEX IF NOT EXISTS uk_students_id_tenant_privacy
    ON edushift.students (id, tenant_id);

ALTER TABLE edushift.privacy_consents
    ADD CONSTRAINT fk_privacy_consents_student_tenant
    FOREIGN KEY (student_id, tenant_id)
    REFERENCES edushift.students (id, tenant_id)
    ON DELETE RESTRICT
    NOT VALID;

COMMENT ON CONSTRAINT fk_privacy_consents_student_tenant
    ON edushift.privacy_consents IS
    'Prevents a consent record in one school from referencing a student in another school.';

