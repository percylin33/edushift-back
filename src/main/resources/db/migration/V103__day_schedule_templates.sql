-- =============================================================================
-- V103 — Day schedule templates, recess policy, teaching mode, homeroom,
--         schedule source documents (horarios / recreos / polidocencia).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- A) Grade.teaching_mode + Section.homeroom_teacher_id
-- ---------------------------------------------------------------------------

ALTER TABLE edushift.grades
    ADD COLUMN IF NOT EXISTS teaching_mode varchar(20) NOT NULL DEFAULT 'POLIDOCENTE';

ALTER TABLE edushift.grades
    DROP CONSTRAINT IF EXISTS chk_grades_teaching_mode;

ALTER TABLE edushift.grades
    ADD CONSTRAINT chk_grades_teaching_mode CHECK (
        teaching_mode IN ('MONODOCENTE', 'POLIDOCENTE', 'MIXTO')
    );

COMMENT ON COLUMN edushift.grades.teaching_mode IS
    'MONODOCENTE | POLIDOCENTE | MIXTO — policy for how teachers are assigned to sections of this grade.';

-- Seed-ish backfill by level code + ordinal (Peruvian defaults).
UPDATE edushift.grades g
SET teaching_mode = 'MONODOCENTE'
FROM edushift.academic_levels l
WHERE g.level_id = l.id
  AND l.code = 'INICIAL'
  AND g.deleted = false;

UPDATE edushift.grades g
SET teaching_mode = CASE
    WHEN g.ordinal <= 4 THEN 'MONODOCENTE'
    ELSE 'MIXTO'
END
FROM edushift.academic_levels l
WHERE g.level_id = l.id
  AND l.code = 'PRIMARIA'
  AND g.deleted = false;

UPDATE edushift.grades g
SET teaching_mode = 'POLIDOCENTE'
FROM edushift.academic_levels l
WHERE g.level_id = l.id
  AND l.code = 'SECUNDARIA'
  AND g.deleted = false;

ALTER TABLE edushift.sections
    ADD COLUMN IF NOT EXISTS homeroom_teacher_id uuid;

ALTER TABLE edushift.sections
    DROP CONSTRAINT IF EXISTS fk_sections_homeroom_teacher;

ALTER TABLE edushift.sections
    ADD CONSTRAINT fk_sections_homeroom_teacher
        FOREIGN KEY (homeroom_teacher_id)
        REFERENCES edushift.teachers (id)
        ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_sections_homeroom_teacher
    ON edushift.sections (tenant_id, homeroom_teacher_id)
    WHERE NOT deleted AND homeroom_teacher_id IS NOT NULL;

COMMENT ON COLUMN edushift.sections.homeroom_teacher_id IS
    'Optional tutor / profesor de aula for the section.';

-- ---------------------------------------------------------------------------
-- B) Day schedule templates + blocks
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS edushift.day_schedule_templates (
    id                      uuid          PRIMARY KEY,
    tenant_id               uuid          NOT NULL,
    public_uuid             uuid          NOT NULL,
    created_at              timestamptz   NOT NULL,
    updated_at              timestamptz   NOT NULL,
    created_by              uuid,
    updated_by              uuid,
    deleted                 boolean       NOT NULL DEFAULT false,
    deleted_at              timestamptz,

    academic_year_id        uuid          NOT NULL,
    academic_level_id       uuid          NOT NULL,
    grade_id                uuid,
    shift                   varchar(20),
    name                    varchar(120)  NOT NULL,
    recess_share_group      varchar(80),

    CONSTRAINT uk_day_schedule_templates_public_uuid UNIQUE (public_uuid),

    CONSTRAINT chk_day_schedule_templates_shift CHECK (
        shift IS NULL OR shift IN ('MORNING', 'AFTERNOON')
    ),

    CONSTRAINT chk_day_schedule_templates_deleted_at CHECK (
        (deleted = false AND deleted_at IS NULL)
     OR (deleted = true  AND deleted_at IS NOT NULL)
    ),

    CONSTRAINT fk_day_schedule_templates_year
        FOREIGN KEY (academic_year_id)
        REFERENCES edushift.academic_years (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_day_schedule_templates_level
        FOREIGN KEY (academic_level_id)
        REFERENCES edushift.academic_levels (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_day_schedule_templates_grade
        FOREIGN KEY (grade_id)
        REFERENCES edushift.grades (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_day_schedule_templates_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES edushift.tenants (id)
        ON DELETE RESTRICT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_day_schedule_templates_scope_active
    ON edushift.day_schedule_templates (
        tenant_id,
        academic_year_id,
        academic_level_id,
        COALESCE(grade_id, '00000000-0000-0000-0000-000000000000'::uuid),
        COALESCE(shift, '')
    )
    WHERE NOT deleted;

CREATE INDEX IF NOT EXISTS idx_day_schedule_templates_tenant_year
    ON edushift.day_schedule_templates (tenant_id, academic_year_id)
    WHERE NOT deleted;

CREATE TRIGGER set_updated_at_day_schedule_templates
    BEFORE UPDATE ON edushift.day_schedule_templates
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON TABLE edushift.day_schedule_templates IS
    'School-day structure (recess/lunch/etc.) per year + level (optional grade override).';

CREATE TABLE IF NOT EXISTS edushift.day_schedule_blocks (
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
    day_of_week             smallint,
    start_time              time          NOT NULL,
    end_time                time          NOT NULL,
    block_type              varchar(32)   NOT NULL,
    label                   varchar(80)   NOT NULL,

    CONSTRAINT uk_day_schedule_blocks_public_uuid UNIQUE (public_uuid),

    CONSTRAINT chk_day_schedule_blocks_day CHECK (
        day_of_week IS NULL OR day_of_week BETWEEN 1 AND 7
    ),

    CONSTRAINT chk_day_schedule_blocks_time_range CHECK (end_time > start_time),

    CONSTRAINT chk_day_schedule_blocks_type CHECK (
        block_type IN ('RECESS', 'LUNCH', 'ASSEMBLY', 'GUIDANCE', 'SPECIALIST_RESERVED')
    ),

    CONSTRAINT chk_day_schedule_blocks_deleted_at CHECK (
        (deleted = false AND deleted_at IS NULL)
     OR (deleted = true  AND deleted_at IS NOT NULL)
    ),

    CONSTRAINT fk_day_schedule_blocks_template
        FOREIGN KEY (template_id)
        REFERENCES edushift.day_schedule_templates (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_day_schedule_blocks_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES edushift.tenants (id)
        ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_day_schedule_blocks_template
    ON edushift.day_schedule_blocks (tenant_id, template_id, start_time)
    WHERE NOT deleted;

CREATE TRIGGER set_updated_at_day_schedule_blocks
    BEFORE UPDATE ON edushift.day_schedule_blocks
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON TABLE edushift.day_schedule_blocks IS
    'Non-teaching (or specialist-reserved) windows inside a day schedule template.';

-- ---------------------------------------------------------------------------
-- C) Schedule source documents (bootstrap from prior-year file)
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS edushift.schedule_source_documents (
    id                      uuid          PRIMARY KEY,
    tenant_id               uuid          NOT NULL,
    public_uuid             uuid          NOT NULL,
    created_at              timestamptz   NOT NULL,
    updated_at              timestamptz   NOT NULL,
    created_by              uuid,
    updated_by              uuid,
    deleted                 boolean       NOT NULL DEFAULT false,
    deleted_at              timestamptz,

    academic_year_id        uuid          NOT NULL,
    kind                    varchar(32)   NOT NULL,
    parse_status            varchar(32)   NOT NULL DEFAULT 'UPLOADED',
    original_filename       varchar(255)  NOT NULL,
    content_type            varchar(120),
    storage_key             varchar(500)  NOT NULL,
    file_size_bytes         bigint,
    parsed_draft_json       jsonb,
    parse_error             varchar(500),

    CONSTRAINT uk_schedule_source_documents_public_uuid UNIQUE (public_uuid),

    CONSTRAINT chk_schedule_source_documents_kind CHECK (
        kind IN ('PRIOR_YEAR_XLSX', 'PRIOR_YEAR_CSV', 'PRIOR_YEAR_PDF', 'PRIOR_YEAR_IMAGE')
    ),

    CONSTRAINT chk_schedule_source_documents_parse_status CHECK (
        parse_status IN ('UPLOADED', 'PARSED', 'COMMITTED', 'FAILED', 'REFERENCE_ONLY')
    ),

    CONSTRAINT fk_schedule_source_documents_year
        FOREIGN KEY (academic_year_id)
        REFERENCES edushift.academic_years (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_schedule_source_documents_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES edushift.tenants (id)
        ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_schedule_source_documents_year
    ON edushift.schedule_source_documents (tenant_id, academic_year_id)
    WHERE NOT deleted;

CREATE TRIGGER set_updated_at_schedule_source_documents
    BEFORE UPDATE ON edushift.schedule_source_documents
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON TABLE edushift.schedule_source_documents IS
    'Prior-year schedule files used to bootstrap day templates / slots (XLSX parse or PDF/image reference).';
