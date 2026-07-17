-- =============================================================================
-- V67__create_teacher_attendance_records.sql
-- Sprint 18 / BE-18.4 — Teacher Attendance
-- =============================================================================
-- Tracks daily teacher attendance per TeacherAssignment + AcademicPeriod.
-- One row per (tenant, teacher_assignment, scheduled_date).
-- =============================================================================

CREATE TABLE IF NOT EXISTS edushift.teacher_attendance_records (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    public_uuid             UUID NOT NULL,
    teacher_assignment_id   UUID NOT NULL,
    academic_period_id      UUID NOT NULL,
    scheduled_date          DATE NOT NULL,
    status                  VARCHAR(16) NOT NULL CHECK (status IN ('PRESENT','ABSENT','JUSTIFIED','LATE')),
    notes                   VARCHAR(500),
    recorded_by_user_id     UUID,
    recorded_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              UUID,
    updated_by              UUID,
    deleted_at              TIMESTAMPTZ,
    deleted                 BOOLEAN NOT NULL DEFAULT false
);

-- Indexes
CREATE UNIQUE INDEX IF NOT EXISTS uk_teacher_attendance_public_uuid
    ON edushift.teacher_attendance_records (public_uuid);

CREATE UNIQUE INDEX IF NOT EXISTS uk_teacher_attendance_assignment_date
    ON edushift.teacher_attendance_records (tenant_id, teacher_assignment_id, scheduled_date)
    WHERE deleted = false;

CREATE INDEX IF NOT EXISTS idx_teacher_attendance_tenant_period
    ON edushift.teacher_attendance_records (tenant_id, academic_period_id);

CREATE INDEX IF NOT EXISTS idx_teacher_attendance_tenant_assignment
    ON edushift.teacher_attendance_records (tenant_id, teacher_assignment_id);
