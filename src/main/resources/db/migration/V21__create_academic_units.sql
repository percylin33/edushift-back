-- =============================================================================
-- V21__create_academic_units.sql
-- Pedagogical units that hang off a course (per-tenant). Sprint 5A / BE-5A.1.
--
-- Key design decisions:
--   * `academic_units`     -- tenant-aware. A unit lives inside a course
--                          (course.id FK, RESTRICT). Each unit carries
--                          a stable display_order so the FE can render
--                          a sortable list.
--   * `name` unique        -- per (course_id, lower(name)) on non-deleted
--                          rows. Allows reusing the same name in
--                          different courses (e.g. "Unidad I" in
--                          Comunicación and Matemática).
--   * `display_order`      -- mutated atomically by a service-side
--                          two-pass reorder (mirrors GradeReorderRequest
--                          from BE-4.2): write to a tmp negative ordinal
--                          first, then bump to the target value to avoid
--                          tripping the partial unique index.
--   * `start_date/end_date`-- optional pedagogical hint (units can span
--                          multiple academic periods). NOT validated
--                          against periods at the DB level — see the
--                          service rule UNIT_DATE_INVERTED.
--   * Lifecycle            -- units are deactivated (`is_active=false`)
--                          rather than deleted whenever a learning
--                          session already references them. Hard
--                          soft-delete is reserved for true mistakes;
--                          the service emits UNIT_HAS_SESSIONS (409)
--                          if a non-empty unit is targeted.
-- =============================================================================

CREATE TABLE edushift.academic_units (
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

    -- Relationship
    course_id         uuid          NOT NULL,

    -- Identity
    name              varchar(200)  NOT NULL,
    description       text,
    display_order     int           NOT NULL,
    start_date        date,
    end_date          date,
    is_active         boolean       NOT NULL DEFAULT true,

    CONSTRAINT uk_academic_units_public_uuid UNIQUE (public_uuid),

    CONSTRAINT chk_academic_units_display_order_positive CHECK (
        display_order >= 1
    ),

    CONSTRAINT chk_academic_units_dates_consistent CHECK (
        start_date IS NULL
        OR end_date IS NULL
        OR end_date >= start_date
    ),

    CONSTRAINT chk_academic_units_deleted_at_consistent CHECK (
        (deleted = false AND deleted_at IS NULL)
        OR (deleted = true  AND deleted_at IS NOT NULL)
    ),

    CONSTRAINT fk_academic_units_course
        FOREIGN KEY (course_id) REFERENCES edushift.courses(id)
        ON DELETE RESTRICT
);

COMMENT ON TABLE  edushift.academic_units                 IS 'Pedagogical units inside a course (per-tenant). Sessions hang off a unit.';
COMMENT ON COLUMN edushift.academic_units.tenant_id       IS 'Tenant scope; managed by Hibernate discriminator multi-tenancy.';
COMMENT ON COLUMN edushift.academic_units.course_id       IS 'FK to courses.id (within the same tenant). RESTRICT delete.';
COMMENT ON COLUMN edushift.academic_units.display_order   IS 'Stable 1-based order; mutated via service-side two-pass reorder.';
COMMENT ON COLUMN edushift.academic_units.is_active       IS 'When false, the unit is hidden from FE dropdowns but kept for historical reads.';

-- Name unique per course (case-insensitive on non-deleted rows)
CREATE UNIQUE INDEX uk_academic_units_course_name_active
    ON edushift.academic_units (course_id, lower(name))
    WHERE deleted = false;

-- display_order unique per course on non-deleted rows
CREATE UNIQUE INDEX uk_academic_units_course_order_active
    ON edushift.academic_units (course_id, display_order)
    WHERE deleted = false;

-- Hot path: list active units of a course in display order
CREATE INDEX idx_academic_units_tenant_course_active
    ON edushift.academic_units (tenant_id, course_id, is_active, display_order)
    WHERE deleted = false;


-- ----------------------------------------------------------------------------
-- Trigger
-- ----------------------------------------------------------------------------
CREATE TRIGGER set_updated_at_academic_units
    BEFORE UPDATE ON edushift.academic_units
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();
