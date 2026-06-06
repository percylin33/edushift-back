-- =============================================================================
-- V19__create_teacher_assignments.sql
-- TeacherAssignment (M:N) — sprint 4 / BE-4.7.
--
-- A teacher can teach the same course in many sections of many periods.
-- The assignment row is the source of truth for "who teaches what"; it is
-- per-tenant via Hibernate's @TenantId discriminator.
--
-- Soft-end semantics
-- ------------------
--   `unassigned_at` flips an active assignment into the historical pile
--   without deleting it. The unique partial index below intentionally only
--   covers the active rows (unassigned_at IS NULL AND deleted = false), so:
--     1) past assignments with the same (teacher, section, course, period)
--        coexist with a new active one, and
--     2) the historical record for grade reports / audit stays intact.
--
-- FK strategy
-- -----------
--   ON DELETE RESTRICT for teacher / section / course / academic_period.
--   This is the dependency layer that re-activates the deferred 409 codes
--   declared by upstream modules (DEBT-ACAD-3 already moved this notion
--   to the application layer, but DB-level RESTRICT is a backstop):
--     * teachers      → TEACHER_HAS_ACTIVE_ASSIGNMENTS
--     * courses       → COURSE_IN_USE_BY_ASSIGNMENTS
--     * academic_periods → PERIOD_IN_USE_BY_ASSIGNMENTS  (DEBT-ACAD-4 closes)
--     * sections      → application uses cascade-friendly soft delete only
-- =============================================================================

CREATE TABLE edushift.teacher_assignments (
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

    -- Foreign keys to the four anchors of the assignment.
    teacher_id            uuid          NOT NULL,
    section_id            uuid          NOT NULL,
    course_id             uuid          NOT NULL,
    academic_period_id    uuid          NOT NULL,

    -- Lifecycle
    assigned_at           timestamptz   NOT NULL DEFAULT NOW(),
    unassigned_at         timestamptz,
    notes                 text,

    CONSTRAINT uk_teacher_assignments_public_uuid UNIQUE (public_uuid),

    CONSTRAINT chk_teacher_assignments_dates_consistent CHECK (
        unassigned_at IS NULL
        OR unassigned_at >= assigned_at
    ),

    CONSTRAINT chk_teacher_assignments_deleted_at_consistent CHECK (
        (deleted = false AND deleted_at IS NULL)
        OR (deleted = true  AND deleted_at IS NOT NULL)
    ),

    CONSTRAINT fk_teacher_assignments_teacher
        FOREIGN KEY (teacher_id) REFERENCES edushift.teachers(id) ON DELETE RESTRICT,

    CONSTRAINT fk_teacher_assignments_section
        FOREIGN KEY (section_id) REFERENCES edushift.sections(id) ON DELETE RESTRICT,

    CONSTRAINT fk_teacher_assignments_course
        FOREIGN KEY (course_id) REFERENCES edushift.courses(id) ON DELETE RESTRICT,

    CONSTRAINT fk_teacher_assignments_period
        FOREIGN KEY (academic_period_id) REFERENCES edushift.academic_periods(id) ON DELETE RESTRICT
);

COMMENT ON TABLE  edushift.teacher_assignments
    IS 'Many-to-many between teachers and (section, course, period) tuples (Sprint 4 / BE-4.7).';
COMMENT ON COLUMN edushift.teacher_assignments.public_uuid
    IS 'External identifier exposed via REST.';
COMMENT ON COLUMN edushift.teacher_assignments.assigned_at
    IS 'When the teacher started this assignment. Defaults to NOW() at insert.';
COMMENT ON COLUMN edushift.teacher_assignments.unassigned_at
    IS 'Soft-end timestamp; historical rows keep unassigned_at != NULL while the active one stays NULL.';


-- ----------------------------------------------------------------------------
-- Indexes
-- ----------------------------------------------------------------------------

-- Active assignments are unique per (teacher, section, course, period).
-- Historical (unassigned_at IS NOT NULL) rows are excluded so re-creating
-- after a soft-end is allowed.
CREATE UNIQUE INDEX uk_teacher_assignments_active
    ON edushift.teacher_assignments (teacher_id, section_id, course_id, academic_period_id)
    WHERE unassigned_at IS NULL AND deleted = false;

-- "Teacher's assignments" hot-path: filter by teacher + period + active.
CREATE INDEX idx_teacher_assignments_teacher_period_active
    ON edushift.teacher_assignments (teacher_id, academic_period_id)
    WHERE unassigned_at IS NULL AND deleted = false;

-- "Section's teachers" reverse view: filter by section + period + active.
CREATE INDEX idx_teacher_assignments_section_period_active
    ON edushift.teacher_assignments (section_id, academic_period_id)
    WHERE unassigned_at IS NULL AND deleted = false;

-- Existence checks for delete guards (TEACHER_HAS_ACTIVE_ASSIGNMENTS,
-- COURSE_IN_USE_BY_ASSIGNMENTS, PERIOD_IN_USE_BY_ASSIGNMENTS): the
-- service layer probes the table by single FK; the partial unique index
-- above already covers the (teacher, period) pair, but a dedicated
-- index per FK makes the EXPLAIN plan deterministic on small data.
CREATE INDEX idx_teacher_assignments_course_active
    ON edushift.teacher_assignments (course_id)
    WHERE unassigned_at IS NULL AND deleted = false;

CREATE INDEX idx_teacher_assignments_period_active
    ON edushift.teacher_assignments (academic_period_id)
    WHERE unassigned_at IS NULL AND deleted = false;


-- ----------------------------------------------------------------------------
-- Triggers
-- ----------------------------------------------------------------------------
CREATE TRIGGER set_updated_at_teacher_assignments
    BEFORE UPDATE ON edushift.teacher_assignments
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();
