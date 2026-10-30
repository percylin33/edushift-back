-- =============================================================================
-- V125 — Optional LEVEL scope on grade_register_templates.
-- One active working copy per (tenant, academic year, academic level).
-- Sections of a level use that copy; levels without one fall back to the
-- year TENANT default. Rollback: drop the new constraint, index, FK and column,
-- then restore the previous scope / course checks (TENANT | COURSE only).
-- =============================================================================

ALTER TABLE edushift.grade_register_templates
    ADD COLUMN IF NOT EXISTS academic_level_id uuid;

ALTER TABLE edushift.grade_register_templates
    DROP CONSTRAINT IF EXISTS fk_grade_register_templates_level;

ALTER TABLE edushift.grade_register_templates
    ADD CONSTRAINT fk_grade_register_templates_level
        FOREIGN KEY (academic_level_id)
        REFERENCES edushift.academic_levels (id)
        ON DELETE RESTRICT;

ALTER TABLE edushift.grade_register_templates
    DROP CONSTRAINT IF EXISTS chk_grade_register_templates_scope;

ALTER TABLE edushift.grade_register_templates
    ADD CONSTRAINT chk_grade_register_templates_scope CHECK (
        scope IN ('TENANT', 'COURSE', 'LEVEL')
    );

ALTER TABLE edushift.grade_register_templates
    DROP CONSTRAINT IF EXISTS chk_grade_register_templates_course_scope;

ALTER TABLE edushift.grade_register_templates
    ADD CONSTRAINT chk_grade_register_templates_course_scope CHECK (
        (scope = 'COURSE' AND (is_system = true OR course_id IS NOT NULL))
     OR (scope <> 'COURSE' AND course_id IS NULL)
    );

ALTER TABLE edushift.grade_register_templates
    DROP CONSTRAINT IF EXISTS chk_grade_register_templates_level_scope;

ALTER TABLE edushift.grade_register_templates
    ADD CONSTRAINT chk_grade_register_templates_level_scope CHECK (
        (scope = 'LEVEL' AND academic_level_id IS NOT NULL AND is_system = false)
     OR (scope <> 'LEVEL' AND academic_level_id IS NULL)
    );

CREATE UNIQUE INDEX IF NOT EXISTS uk_grt_tenant_year_level_active
    ON edushift.grade_register_templates (tenant_id, academic_year_id, academic_level_id)
    WHERE deleted = false AND is_system = false AND scope = 'LEVEL';

COMMENT ON COLUMN edushift.grade_register_templates.academic_level_id IS
    'Set only when scope=LEVEL. Sections of this level use this working copy; null means the year default.';
