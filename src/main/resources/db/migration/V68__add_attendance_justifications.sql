-- =============================================================================
-- V68__add_attendance_justifications.sql
-- Sprint 18 / BE-18.5 — Attendance Justifications
-- =============================================================================
-- Adds justification columns to attendance_records so students/parents
-- can submit justifications for absences and teachers/admins can
-- approve/reject them.
-- =============================================================================

ALTER TABLE edushift.attendance_records
    ADD COLUMN IF NOT EXISTS justification_status  VARCHAR(16),
    ADD COLUMN IF NOT EXISTS justification_text    VARCHAR(2000),
    ADD COLUMN IF NOT EXISTS approved_by_user_id   UUID,
    ADD COLUMN IF NOT EXISTS approved_at           TIMESTAMPTZ;

-- Constraint: justification_status must be one of PENDING, APPROVED, REJECTED
ALTER TABLE edushift.attendance_records
    ADD CONSTRAINT chk_attendance_justification_status
        CHECK (justification_status IS NULL
            OR justification_status IN ('PENDING', 'APPROVED', 'REJECTED'));

-- Index for querying pending justifications
CREATE INDEX IF NOT EXISTS idx_attendance_records_justification_status
    ON edushift.attendance_records (tenant_id, justification_status)
    WHERE justification_status IS NOT NULL;
