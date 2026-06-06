-- =============================================================================
-- V20__create_student_enrollments.sql
-- StudentEnrollment — sprint 4 / BE-4.8.
--
-- Pivot table that records the matricula of a student in a section for a
-- given academic year. Per-tenant via Hibernate's @TenantId discriminator.
--
-- Lifecycle (status enum, mirrored in StudentEnrollmentStatus.java)
-- -----------------------------------------------------------------
--   ACTIVE        Default insert; one row per (student, year) at a time.
--   WITHDRAWN     Student left the institution.
--   TRANSFERRED   Student moved to another section / school.
--   GRADUATED     Student finished the cycle (terminal).
--
-- Why a separate enum from `students.enrollment_status`
-- ------------------------------------------------------
-- The student-level enum (`PENDING / ENROLLED / GRADUATED / TRANSFERRED /
-- WITHDRAWN`) is an institution-wide lifecycle. This one is the per-year
-- placement lifecycle, so a student can be GRADUATED at the institution
-- level while still having ACTIVE enrollments in a final period (or vice
-- versa during the cut-over). Keeping them apart avoids ambiguous reads.
--
-- Unique partial index `uk_student_enrollments_active`
-- ----------------------------------------------------
-- Forces "one ACTIVE enrollment per (student, year)". Soft-ended rows
-- (status != 'ACTIVE') drop out of the index, so a transfer scenario
-- (withdraw + new enrollment in the same year) is allowed.
--
-- FK strategy
-- -----------
--   ON DELETE RESTRICT for student / section / academic_year.
--   The application layer surfaces the 409 (`SECTION_HAS_ENROLLMENTS`,
--   `STUDENT_HAS_ACTIVE_ENROLLMENT`, `YEAR_HAS_ENROLLMENTS`); the DB
--   constraint is the backstop.
-- =============================================================================

CREATE TABLE edushift.student_enrollments (
    id                    uuid          PRIMARY KEY,
    tenant_id             uuid          NOT NULL,
    public_uuid           uuid          NOT NULL,

    -- Audit (inherited from AuditableEntity / BaseEntity)
    created_at            timestamptz   NOT NULL,
    updated_at            timestamptz   NOT NULL,
    created_by            uuid,
    updated_by            uuid,
    deleted               boolean       NOT NULL DEFAULT false,
    deleted_at            timestamptz,

    -- Foreign keys
    student_id            uuid          NOT NULL,
    section_id            uuid          NOT NULL,
    academic_year_id      uuid          NOT NULL,

    -- Lifecycle
    enrolled_at           date          NOT NULL,
    withdrawn_at          date,
    status                varchar(30)   NOT NULL DEFAULT 'ACTIVE',
    notes                 text,

    CONSTRAINT uk_student_enrollments_public_uuid UNIQUE (public_uuid),

    CONSTRAINT chk_student_enrollments_status CHECK (
        status IN ('ACTIVE', 'WITHDRAWN', 'TRANSFERRED', 'GRADUATED')
    ),

    -- ACTIVE rows must NOT have a withdrawn_at; non-ACTIVE rows MUST.
    CONSTRAINT chk_student_enrollments_terminal_dates CHECK (
        (status = 'ACTIVE'  AND withdrawn_at IS NULL)
        OR
        (status <> 'ACTIVE' AND withdrawn_at IS NOT NULL)
    ),

    CONSTRAINT chk_student_enrollments_dates_order CHECK (
        withdrawn_at IS NULL OR withdrawn_at >= enrolled_at
    ),

    CONSTRAINT chk_student_enrollments_deleted_at_consistent CHECK (
        (deleted = false AND deleted_at IS NULL)
        OR (deleted = true  AND deleted_at IS NOT NULL)
    ),

    CONSTRAINT fk_student_enrollments_student
        FOREIGN KEY (student_id) REFERENCES edushift.students(id) ON DELETE RESTRICT,

    CONSTRAINT fk_student_enrollments_section
        FOREIGN KEY (section_id) REFERENCES edushift.sections(id) ON DELETE RESTRICT,

    CONSTRAINT fk_student_enrollments_year
        FOREIGN KEY (academic_year_id) REFERENCES edushift.academic_years(id) ON DELETE RESTRICT
);

COMMENT ON TABLE  edushift.student_enrollments
    IS 'Per-year placement of a student in a section (Sprint 4 / BE-4.8).';
COMMENT ON COLUMN edushift.student_enrollments.public_uuid
    IS 'External identifier exposed via REST.';
COMMENT ON COLUMN edushift.student_enrollments.status
    IS 'Per-year lifecycle: ACTIVE | WITHDRAWN | TRANSFERRED | GRADUATED.';
COMMENT ON COLUMN edushift.student_enrollments.enrolled_at
    IS 'Date the student joined this section.';
COMMENT ON COLUMN edushift.student_enrollments.withdrawn_at
    IS 'Date the enrollment ended; NULL while ACTIVE, set when terminal.';

-- ----------------------------------------------------------------------------
-- Indexes
-- ----------------------------------------------------------------------------

-- One ACTIVE enrollment per (student, academic_year). Historical rows
-- (status != 'ACTIVE') are excluded so transferring within the same year
-- (withdraw + new enrollment in another section) is allowed.
CREATE UNIQUE INDEX uk_student_enrollments_active
    ON edushift.student_enrollments (student_id, academic_year_id)
    WHERE status = 'ACTIVE' AND deleted = false;

-- Section roster hot-path: "students currently in section X".
CREATE INDEX idx_student_enrollments_section_status
    ON edushift.student_enrollments (section_id, status)
    WHERE deleted = false;

-- Student timeline hot-path: "history of enrollments for student S".
CREATE INDEX idx_student_enrollments_student_enrolled_at
    ON edushift.student_enrollments (student_id, enrolled_at DESC)
    WHERE deleted = false;

-- StudentService.list filter "currentSectionId" / "currentAcademicYearId":
-- a single index covers the "current ACTIVE row by student" join.
CREATE INDEX idx_student_enrollments_active_lookup
    ON edushift.student_enrollments (student_id, academic_year_id, section_id)
    WHERE status = 'ACTIVE' AND deleted = false;


-- ----------------------------------------------------------------------------
-- Triggers
-- ----------------------------------------------------------------------------
CREATE TRIGGER set_updated_at_student_enrollments
    BEFORE UPDATE ON edushift.student_enrollments
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();
