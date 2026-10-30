-- =============================================================================
-- V114__invoice_payment_evidence.sql
-- Parent voucher upload for manual payment reconciliation.
-- Soft-FK to lms_file_objects (same pattern as V98 attendance justification).
-- Status PENDING_REVIEW = voucher submitted, awaiting staff approval.
-- =============================================================================

ALTER TABLE edushift.invoices
    ALTER COLUMN status TYPE varchar(20);

ALTER TABLE edushift.invoices
    ADD COLUMN evidence_file_public_uuid uuid,
    ADD COLUMN evidence_submitted_at timestamptz,
    ADD COLUMN evidence_notes text,
    ADD COLUMN evidence_rejection_reason text;

ALTER TABLE edushift.invoices
    ADD CONSTRAINT fk_invoices_evidence_file
        FOREIGN KEY (evidence_file_public_uuid)
        REFERENCES edushift.lms_file_objects (public_uuid)
        ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_invoices_status_pending_review
    ON edushift.invoices (tenant_id, status)
    WHERE status = 'PENDING_REVIEW' AND deleted = false;

COMMENT ON COLUMN edushift.invoices.evidence_file_public_uuid IS
    'Parent-uploaded voucher (image/PDF) pending staff reconciliation.';
COMMENT ON COLUMN edushift.invoices.evidence_submitted_at IS
    'When the parent last submitted payment evidence.';
COMMENT ON COLUMN edushift.invoices.evidence_notes IS
    'Optional bank/operation note from the parent.';
COMMENT ON COLUMN edushift.invoices.evidence_rejection_reason IS
    'Last staff rejection reason (cleared on re-submit or approve).';
COMMENT ON COLUMN edushift.invoices.status IS
    'PENDING | PENDING_REVIEW | PAID | OVERDUE | CANCELLED | REFUNDED';
