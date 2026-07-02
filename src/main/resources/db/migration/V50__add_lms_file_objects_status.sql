-- =============================================================================
-- V50__add_lms_file_objects_status.sql
--
-- Adds a `status` column to lms_file_objects to support the
-- "backend-mints-signed-PUT-URL, client uploads directly to Firebase,
-- client confirms" flow (docs/infra/firebase.md).
--
-- Flow:
--   1. Client → POST /api/v1/files/upload-requests  (BE mints signed PUT URL)
--   2. Client → PUT <signedUrl>                    (raw bytes, no bearer)
--   3. Client → POST /api/v1/files/{uuid}/confirm  (BE flips PENDING → READY)
--
-- The CHECK constraint enforces the state machine: a row is born
-- PENDING, optionally transitions to READY on confirm, or stays PENDING
-- (the housekeeping job — DEBT-7A-1 follow-up — GCs stale rows after 24h).
--
-- Existing rows from the BE-proxied flow (status defaults to READY) stay
-- valid; their bytes already live in the provider. This is a non-breaking
-- additive change.
-- =============================================================================

ALTER TABLE edushift.lms_file_objects
    ADD COLUMN status varchar(16) NOT NULL DEFAULT 'READY';

ALTER TABLE edushift.lms_file_objects
    ADD CONSTRAINT chk_file_objects_status CHECK (
        status IN ('PENDING', 'READY', 'FAILED')
    );

-- Hot path: list pending uploads (housekeeping) by tenant + age.
CREATE INDEX idx_file_objects_tenant_status_created
    ON edushift.lms_file_objects (tenant_id, status, created_at)
    WHERE deleted = false;

COMMENT ON COLUMN edushift.lms_file_objects.status IS
    'Upload lifecycle. PENDING: signed URL minted, bytes not yet in provider. '
    'READY: bytes persisted in provider (default for the BE-proxied flow). '
    'FAILED: client reported PUT failure (housekeeping GCs after 24h, DEBT-7A-1).';