-- =============================================================================
-- V79__add_assignments_count_to_teachers.sql
-- Sprint 5 / DEBT-TEA-1 — Teacher workload counter.
-- =============================================================================
-- Caches the number of active teacher_assignments rows per teacher so the
-- "carga docente" reports (Sprint 7+ roadmap) and the new
-- TeacherAssignmentWorkloadListener can keep an incrementally-maintained
-- counter instead of recomputing COUNT(*) on every read.
--
-- Strategy:
--   1. Add the column NOT NULL DEFAULT 0 so existing rows immediately have
--      a sane value (Spring Boot admin tooling is still on Boot 3.4 / PG 16,
--      a single ALTER is atomic and metadata-only when no row needs the
--      default).
--   2. Back-fill with a single UPDATE from the source of truth
--      (teacher_assignments WHERE unassigned_at IS NULL AND deleted = false).
--      This back-fills the exact count for any teacher that already had
--      assignments before this migration (workshop demos, dry-runs).
--   3. Drop the DEFAULT so future application writes MUST go through the
--      JPA path (which only inserts new teachers with 0 via the explicit
--      column default in Teacher.java) — prevents silent drift.
--   4. Partial index for the "top-N teachers by workload" report.
-- =============================================================================

ALTER TABLE edushift.teachers
    ADD COLUMN IF NOT EXISTS assignments_count INTEGER NOT NULL DEFAULT 0;

-- Back-fill from the authoritative source-of-truth table.
-- Wrapped in a single statement for atomicity per tenant; idempotent
-- because every row's previous value is overwritten.
UPDATE edushift.teachers t
SET assignments_count = sub.cnt
FROM (
    SELECT teacher_id, COUNT(*)::int AS cnt
    FROM edushift.teacher_assignments
    WHERE unassigned_at IS NULL
      AND deleted = false
    GROUP BY teacher_id
) sub
WHERE t.id = sub.teacher_id;

-- Cap value to a sane upper bound; defends against any accidental
-- negative drift introduced by future decrement bugs.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_teachers_assignments_count_nonneg'
    ) THEN
        ALTER TABLE edushift.teachers
            ADD CONSTRAINT chk_teachers_assignments_count_nonneg
                CHECK (assignments_count >= 0);
    END IF;
END$$;

-- Drop the default so application code must manage the value explicitly
-- via TeacherRepository.incrementAssignmentsCountByPublicUuid / a future
-- decrement method (keeps the counter auditable in code review).
ALTER TABLE edushift.teachers
    ALTER COLUMN assignments_count DROP DEFAULT;

-- Partial index for "top teachers by workload" reports.
CREATE INDEX IF NOT EXISTS idx_teachers_assignments_count_active
    ON edushift.teachers (tenant_id, assignments_count)
    WHERE assignments_count > 0;

COMMENT ON COLUMN edushift.teachers.assignments_count IS
    'Cache of active (unassigned_at IS NULL) teacher_assignments for this teacher. '
    'Maintained by TeacherAssignmentWorkloadListener in-tx; backstop is COUNT(*) on teacher_assignments.';
