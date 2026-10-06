-- V112: evaluation name uniqueness scoped to (assignment, category).
-- Allows the same slot labels (S1…S10) in different GradeCategories
-- of the same TeacherAssignment (formal register SERIES blocks).

DROP INDEX IF EXISTS edushift.uk_evaluations_tenant_assignment_name_ci;

-- Categorised evaluations: unique per (tenant, assignment, category, name)
CREATE UNIQUE INDEX uk_evaluations_assignment_category_name_ci
    ON edushift.evaluations (tenant_id, teacher_assignment_id, category_id, lower(name))
    WHERE NOT deleted AND category_id IS NOT NULL;

-- Uncategorised evaluations: unique per (tenant, assignment, name)
CREATE UNIQUE INDEX uk_evaluations_assignment_null_category_name_ci
    ON edushift.evaluations (tenant_id, teacher_assignment_id, lower(name))
    WHERE NOT deleted AND category_id IS NULL;
