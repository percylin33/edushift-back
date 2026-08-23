-- =============================================================================
-- V101__add_document_to_user_invitations.sql
-- Snapshot identity document on pending invitations so admins commit to a
-- legal id at invite time (manual §1.5) and audit logs stay traceable.
--
-- Nullable for legacy rows created before this migration; new invites
-- require both fields at the API layer.
-- =============================================================================

ALTER TABLE edushift.user_invitations
    ADD COLUMN document_type   varchar(20),
    ADD COLUMN document_number varchar(20);

ALTER TABLE edushift.user_invitations
    ADD CONSTRAINT chk_user_invitations_document_type CHECK (
        document_type IS NULL
        OR document_type IN ('DNI', 'CE', 'PASSPORT', 'OTHER')
    );

COMMENT ON COLUMN edushift.user_invitations.document_type
    IS 'Identity document kind snapshotted at invite time (nullable for legacy rows).';
COMMENT ON COLUMN edushift.user_invitations.document_number
    IS 'Identity document number snapshotted at invite time (nullable for legacy rows).';
