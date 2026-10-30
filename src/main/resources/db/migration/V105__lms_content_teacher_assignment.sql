-- =============================================================================
-- V105__lms_content_teacher_assignment.sql
-- Anchor LMS tasks / materials / quizzes to TeacherAssignment (section + course
-- + period). Owned by the `lms` module.
--
-- Key design decisions:
--   * teacher_assignment_id is NULLABLE for pre-V105 legacy rows
--   * New creates require an active assignment (enforced in service layer)
--   * section_id is retained (denormalized) for list hot-paths
--   * FK ON DELETE RESTRICT: ending an assignment must not orphan silently
-- =============================================================================

-- ---------------------------------------------------------------------------
-- lms_tasks
-- ---------------------------------------------------------------------------
ALTER TABLE edushift.lms_tasks
    ADD COLUMN teacher_assignment_id uuid;

ALTER TABLE edushift.lms_tasks
    ADD CONSTRAINT fk_lms_tasks_teacher_assignment
        FOREIGN KEY (teacher_assignment_id)
        REFERENCES edushift.teacher_assignments (id)
        ON DELETE RESTRICT;

CREATE INDEX idx_lms_tasks_tenant_assignment
    ON edushift.lms_tasks (tenant_id, teacher_assignment_id)
    WHERE deleted = false AND teacher_assignment_id IS NOT NULL;

CREATE INDEX idx_lms_tasks_tenant_section_assignment
    ON edushift.lms_tasks (tenant_id, section_id, teacher_assignment_id)
    WHERE deleted = false;

COMMENT ON COLUMN edushift.lms_tasks.teacher_assignment_id IS
    'FK -> teacher_assignments(id). Scopes the task to (teacher, section, course, period). NULL = legacy pre-V105.';

-- ---------------------------------------------------------------------------
-- lms_materials
-- ---------------------------------------------------------------------------
ALTER TABLE edushift.lms_materials
    ADD COLUMN teacher_assignment_id uuid;

ALTER TABLE edushift.lms_materials
    ADD CONSTRAINT fk_lms_materials_teacher_assignment
        FOREIGN KEY (teacher_assignment_id)
        REFERENCES edushift.teacher_assignments (id)
        ON DELETE RESTRICT;

CREATE INDEX idx_lms_materials_tenant_assignment
    ON edushift.lms_materials (tenant_id, teacher_assignment_id)
    WHERE deleted = false AND teacher_assignment_id IS NOT NULL;

CREATE INDEX idx_lms_materials_tenant_section_assignment
    ON edushift.lms_materials (tenant_id, section_id, teacher_assignment_id)
    WHERE deleted = false;

COMMENT ON COLUMN edushift.lms_materials.teacher_assignment_id IS
    'FK -> teacher_assignments(id). Scopes the material to a teacher assignment. NULL = legacy pre-V105.';

-- ---------------------------------------------------------------------------
-- lms_quizzes
-- ---------------------------------------------------------------------------
ALTER TABLE edushift.lms_quizzes
    ADD COLUMN teacher_assignment_id uuid;

ALTER TABLE edushift.lms_quizzes
    ADD CONSTRAINT fk_lms_quizzes_teacher_assignment
        FOREIGN KEY (teacher_assignment_id)
        REFERENCES edushift.teacher_assignments (id)
        ON DELETE RESTRICT;

CREATE INDEX idx_lms_quizzes_tenant_assignment
    ON edushift.lms_quizzes (tenant_id, teacher_assignment_id)
    WHERE deleted = false AND teacher_assignment_id IS NOT NULL;

CREATE INDEX idx_lms_quizzes_tenant_section_assignment
    ON edushift.lms_quizzes (tenant_id, section_id, teacher_assignment_id)
    WHERE deleted = false;

COMMENT ON COLUMN edushift.lms_quizzes.teacher_assignment_id IS
    'FK -> teacher_assignments(id). Scopes the quiz to a teacher assignment. NULL = legacy pre-V105.';
