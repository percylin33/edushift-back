-- =============================================================================
-- V109 — Grade register templates + categories (formal registro F1)
-- Catalog (is_system) per-tenant seed; tenant copies via adopt/fork.
-- =============================================================================

CREATE TABLE IF NOT EXISTS edushift.grade_register_templates (
    id                      uuid          PRIMARY KEY,
    tenant_id               uuid          NOT NULL,
    public_uuid             uuid          NOT NULL,
    created_at              timestamptz   NOT NULL,
    updated_at              timestamptz   NOT NULL,
    created_by              uuid,
    updated_by              uuid,
    deleted                 boolean       NOT NULL DEFAULT false,
    deleted_at              timestamptz,

    academic_year_id        uuid,
    course_id               uuid,
    source_template_id      uuid,
    name                    varchar(120)  NOT NULL,
    description             varchar(500),
    scope                   varchar(16)   NOT NULL DEFAULT 'TENANT',
    is_system               boolean       NOT NULL DEFAULT false,
    is_default              boolean       NOT NULL DEFAULT false,
    catalog_code            varchar(40),

    CONSTRAINT uk_grade_register_templates_public_uuid UNIQUE (public_uuid),

    CONSTRAINT chk_grade_register_templates_scope CHECK (
        scope IN ('TENANT', 'COURSE')
    ),

    CONSTRAINT chk_grade_register_templates_deleted_at CHECK (
        (deleted = false AND deleted_at IS NULL)
     OR (deleted = true  AND deleted_at IS NOT NULL)
    ),

    CONSTRAINT chk_grade_register_templates_system_year CHECK (
        (is_system = true AND academic_year_id IS NULL)
     OR (is_system = false AND academic_year_id IS NOT NULL)
    ),

    CONSTRAINT chk_grade_register_templates_course_scope CHECK (
        (scope = 'TENANT' AND course_id IS NULL)
     OR (scope = 'COURSE' AND (is_system = true OR course_id IS NOT NULL))
    ),

    CONSTRAINT fk_grade_register_templates_year
        FOREIGN KEY (academic_year_id)
        REFERENCES edushift.academic_years (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_grade_register_templates_course
        FOREIGN KEY (course_id)
        REFERENCES edushift.courses (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_grade_register_templates_source
        FOREIGN KEY (source_template_id)
        REFERENCES edushift.grade_register_templates (id)
        ON DELETE SET NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_grt_tenant_catalog_code_active
    ON edushift.grade_register_templates (tenant_id, lower(catalog_code))
    WHERE deleted = false AND catalog_code IS NOT NULL AND is_system = true;

CREATE UNIQUE INDEX IF NOT EXISTS uk_grt_tenant_year_default_active
    ON edushift.grade_register_templates (tenant_id, academic_year_id)
    WHERE deleted = false AND is_default = true AND is_system = false
      AND scope = 'TENANT';

CREATE INDEX IF NOT EXISTS idx_grt_tenant_year
    ON edushift.grade_register_templates (tenant_id, academic_year_id)
    WHERE deleted = false;

CREATE INDEX IF NOT EXISTS idx_grt_tenant_system
    ON edushift.grade_register_templates (tenant_id, is_system)
    WHERE deleted = false;

COMMENT ON TABLE edushift.grade_register_templates IS
    'Formal grade-register templates: system catalog (is_system) or tenant working copies after adopt.';

CREATE TABLE IF NOT EXISTS edushift.grade_categories (
    id                      uuid          PRIMARY KEY,
    tenant_id               uuid          NOT NULL,
    public_uuid             uuid          NOT NULL,
    created_at              timestamptz   NOT NULL,
    updated_at              timestamptz   NOT NULL,
    created_by              uuid,
    updated_by              uuid,
    deleted                 boolean       NOT NULL DEFAULT false,
    deleted_at              timestamptz,

    template_id             uuid          NOT NULL,
    name                    varchar(80)   NOT NULL,
    color_token             varchar(32)   NOT NULL DEFAULT 'neutral',
    sort_order              int           NOT NULL DEFAULT 1,
    weight                  numeric(6,2)  NOT NULL DEFAULT 0,
    contributes_to_average  boolean       NOT NULL DEFAULT true,
    allows_teacher_columns  boolean       NOT NULL DEFAULT true,

    CONSTRAINT uk_grade_categories_public_uuid UNIQUE (public_uuid),

    CONSTRAINT chk_grade_categories_deleted_at CHECK (
        (deleted = false AND deleted_at IS NULL)
     OR (deleted = true  AND deleted_at IS NOT NULL)
    ),

    CONSTRAINT chk_grade_categories_weight CHECK (weight >= 0 AND weight <= 999.99),

    CONSTRAINT fk_grade_categories_template
        FOREIGN KEY (template_id)
        REFERENCES edushift.grade_register_templates (id)
        ON DELETE RESTRICT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_grade_categories_template_name_ci
    ON edushift.grade_categories (tenant_id, template_id, lower(name))
    WHERE deleted = false;

CREATE INDEX IF NOT EXISTS idx_grade_categories_template_order
    ON edushift.grade_categories (tenant_id, template_id, sort_order)
    WHERE deleted = false;

COMMENT ON TABLE edushift.grade_categories IS
    'Column groups inside a grade register template (Participación, Exámenes, …).';

ALTER TABLE edushift.evaluations
    ADD COLUMN IF NOT EXISTS category_id uuid;

ALTER TABLE edushift.evaluations
    DROP CONSTRAINT IF EXISTS fk_evaluations_grade_category;

ALTER TABLE edushift.evaluations
    ADD CONSTRAINT fk_evaluations_grade_category
        FOREIGN KEY (category_id)
        REFERENCES edushift.grade_categories (id)
        ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_evaluations_tenant_category
    ON edushift.evaluations (tenant_id, category_id)
    WHERE deleted = false AND category_id IS NOT NULL;

COMMENT ON COLUMN edushift.evaluations.category_id IS
    'Optional GradeCategory from the tenant grade-register template (F1 formal register).';

CREATE OR REPLACE TRIGGER set_updated_at_grade_register_templates
    BEFORE UPDATE ON edushift.grade_register_templates
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

CREATE OR REPLACE TRIGGER set_updated_at_grade_categories
    BEFORE UPDATE ON edushift.grade_categories
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();
