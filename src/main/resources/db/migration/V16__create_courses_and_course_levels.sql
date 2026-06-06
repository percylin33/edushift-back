-- =============================================================================
-- V16__create_courses_and_course_levels.sql
-- Course catalog (per-tenant) + M:N pivot to academic levels.
-- Owned by the `academic.course` sub-module (Sprint 4 / BE-4.4).
--
-- Key design decisions:
--   * `courses`              — tenant-aware catalog. `code` is the human
--                              identifier (e.g. "MAT", "COMU"); unique
--                              per-tenant case-insensitive on non-deleted
--                              rows. Courses can be deactivated
--                              (`is_active=false`) without deleting them
--                              so existing teacher assignments / grade
--                              reports keep referencing them.
--   * `course_levels`        — explicit entity (NOT @ManyToMany pivot)
--                              because every aggregate in this codebase
--                              extends TenantAwareEntity for audit /
--                              soft-delete uniformity. Each row is
--                              tenant-scoped via Hibernate's @TenantId.
--   * Invariant              — A course must be linked to >= 1 level at
--                              all times (enforced at service layer with
--                              code COURSE_NEEDS_AT_LEAST_ONE_LEVEL).
--                              The DB does not enforce this by itself
--                              (no DEFERRED constraint complexity), but
--                              the unique partial index below guarantees
--                              no duplicates inside the set.
-- =============================================================================

-- ----------------------------------------------------------------------------
-- courses
-- ----------------------------------------------------------------------------
CREATE TABLE edushift.courses (
    id                uuid          PRIMARY KEY,
    tenant_id         uuid          NOT NULL,
    public_uuid       uuid          NOT NULL,

    -- Audit
    created_at        timestamptz   NOT NULL,
    updated_at        timestamptz   NOT NULL,
    created_by        uuid,
    updated_by        uuid,
    deleted           boolean       NOT NULL DEFAULT false,
    deleted_at        timestamptz,

    -- Identity
    code              varchar(30)   NOT NULL,
    name              varchar(200)  NOT NULL,
    description       text,
    credits           int,
    hours_per_week    int,
    is_active         boolean       NOT NULL DEFAULT true,

    CONSTRAINT uk_courses_public_uuid UNIQUE (public_uuid),

    CONSTRAINT chk_courses_code_format CHECK (
        code = upper(code)
        AND code ~ '^[A-Z][A-Z0-9_]*$'
    ),

    CONSTRAINT chk_courses_credits_non_negative CHECK (
        credits IS NULL OR credits >= 0
    ),

    CONSTRAINT chk_courses_hours_non_negative CHECK (
        hours_per_week IS NULL OR hours_per_week >= 0
    ),

    CONSTRAINT chk_courses_deleted_at_consistent CHECK (
        (deleted = false AND deleted_at IS NULL)
        OR (deleted = true  AND deleted_at IS NOT NULL)
    )
);

COMMENT ON TABLE  edushift.courses                IS 'Course catalog per tenant (M:N with academic_levels via course_levels).';
COMMENT ON COLUMN edushift.courses.tenant_id      IS 'Tenant scope; managed by Hibernate discriminator multi-tenancy';
COMMENT ON COLUMN edushift.courses.code           IS 'Stable upper-case identifier; unique per-tenant case-insensitive (e.g. "MAT", "COMU").';
COMMENT ON COLUMN edushift.courses.is_active      IS 'When false, the course is hidden from FE dropdowns but kept for historical reads.';

-- Code unique per tenant (case-insensitive on non-deleted rows)
CREATE UNIQUE INDEX uk_courses_tenant_code_active
    ON edushift.courses (tenant_id, lower(code))
    WHERE deleted = false;

-- Hot path: list active courses sorted by name
CREATE INDEX idx_courses_tenant_active_name
    ON edushift.courses (tenant_id, is_active, lower(name))
    WHERE deleted = false;


-- ----------------------------------------------------------------------------
-- course_levels (explicit pivot entity)
-- ----------------------------------------------------------------------------
CREATE TABLE edushift.course_levels (
    id            uuid          PRIMARY KEY,
    tenant_id     uuid          NOT NULL,
    public_uuid   uuid          NOT NULL,

    -- Audit
    created_at    timestamptz   NOT NULL,
    updated_at    timestamptz   NOT NULL,
    created_by    uuid,
    updated_by    uuid,
    deleted       boolean       NOT NULL DEFAULT false,
    deleted_at    timestamptz,

    -- Relationship
    course_id     uuid          NOT NULL,
    level_id      uuid          NOT NULL,

    CONSTRAINT uk_course_levels_public_uuid UNIQUE (public_uuid),

    CONSTRAINT chk_course_levels_deleted_at_consistent CHECK (
        (deleted = false AND deleted_at IS NULL)
        OR (deleted = true  AND deleted_at IS NOT NULL)
    ),

    -- Both FKs RESTRICT for the same reason as sections: deletion
    -- order is enforced at the service layer with clean 409 codes
    -- (COURSE_NEEDS_AT_LEAST_ONE_LEVEL / LEVEL_IN_USE_BY_COURSES /
    -- COURSE_IN_USE_BY_ASSIGNMENTS).
    CONSTRAINT fk_course_levels_course
        FOREIGN KEY (course_id) REFERENCES edushift.courses(id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_course_levels_level
        FOREIGN KEY (level_id) REFERENCES edushift.academic_levels(id)
        ON DELETE RESTRICT
);

COMMENT ON TABLE  edushift.course_levels             IS 'M:N pivot between courses and academic_levels (per-tenant; explicit entity).';
COMMENT ON COLUMN edushift.course_levels.tenant_id   IS 'Tenant scope; managed by Hibernate discriminator multi-tenancy';
COMMENT ON COLUMN edushift.course_levels.course_id   IS 'FK to courses.id (within the same tenant). RESTRICT delete.';
COMMENT ON COLUMN edushift.course_levels.level_id    IS 'FK to academic_levels.id (within the same tenant). RESTRICT delete.';

-- Unique (course, level) on non-deleted rows
CREATE UNIQUE INDEX uk_course_levels_course_level_active
    ON edushift.course_levels (course_id, level_id)
    WHERE deleted = false;

-- Hot path: list courses linked to a level (filter by levelId)
CREATE INDEX idx_course_levels_tenant_level
    ON edushift.course_levels (tenant_id, level_id)
    WHERE deleted = false;

-- Hot path: list levels linked to a course (course detail view)
CREATE INDEX idx_course_levels_course
    ON edushift.course_levels (course_id)
    WHERE deleted = false;


-- ----------------------------------------------------------------------------
-- Triggers
-- ----------------------------------------------------------------------------
CREATE TRIGGER set_updated_at_courses
    BEFORE UPDATE ON edushift.courses
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

CREATE TRIGGER set_updated_at_course_levels
    BEFORE UPDATE ON edushift.course_levels
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();
