-- =============================================================================
-- V22__create_competencies_capacities.sql
-- MINEDU-style competencies and capacities per course (per-tenant editable
-- with optional on-demand seed). Sprint 5A / BE-5A.2.
--
-- Key design decisions:
--   * `competencies`        -- tenant-aware. Hangs off a `course` (FK
--                            RESTRICT). `code` is the human identifier
--                            (e.g. "COMU_C1", "MAT_C2"); unique per
--                            course case-insensitive on non-deleted rows.
--                            `display_order` ordered set scoped to the
--                            course (mirrors `academic_units` from V21).
--   * `capacities`          -- tenant-aware. Hangs off a `competency`
--                            (FK RESTRICT). Same code/order invariants
--                            scoped to the competency.
--   * Per-tenant editable   -- both tables carry `tenant_id` and are
--                            fully CRUD-able by TENANT_ADMIN. The
--                            optional MINEDU seed is on-demand per
--                            course (BE-5A.2 / `seedDefaults`) instead
--                            of running in self-signup, because the
--                            catalog depends on courses existing first.
--   * Lifecycle             -- both tables prefer deactivation
--                            (`is_active=false`) over hard soft-delete
--                            once they are referenced by a learning
--                            session (BE-5A.4). The service emits
--                            `COMPETENCY_IN_USE_BY_SESSIONS` and
--                            `CAPACITY_IN_USE_BY_SESSIONS` when
--                            applicable (placeholders until BE-5A.4
--                            wires up).
-- =============================================================================

-- ----------------------------------------------------------------------------
-- competencies
-- ----------------------------------------------------------------------------
CREATE TABLE edushift.competencies (
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
    code              varchar(40)   NOT NULL,
    name              varchar(300)  NOT NULL,
    description       text,
    display_order     int           NOT NULL,
    is_active         boolean       NOT NULL DEFAULT true,

    CONSTRAINT uk_competencies_public_uuid UNIQUE (public_uuid),

    CONSTRAINT chk_competencies_code_format CHECK (
        code = upper(code)
        AND code ~ '^[A-Z][A-Z0-9_]*$'
    ),

    CONSTRAINT chk_competencies_display_order_positive CHECK (
        display_order >= 1
    ),

    CONSTRAINT chk_competencies_deleted_at_consistent CHECK (
        (deleted = false AND deleted_at IS NULL)
        OR (deleted = true  AND deleted_at IS NOT NULL)
    ),

    CONSTRAINT fk_competencies_course
        FOREIGN KEY (course_id) REFERENCES edushift.courses(id)
        ON DELETE RESTRICT
);

COMMENT ON TABLE  edushift.competencies                IS 'MINEDU-style competencies per course (per-tenant editable). Sprint 5A.';
COMMENT ON COLUMN edushift.competencies.tenant_id      IS 'Tenant scope; managed by Hibernate discriminator multi-tenancy.';
COMMENT ON COLUMN edushift.competencies.course_id      IS 'FK to courses.id (within the same tenant). RESTRICT delete.';
COMMENT ON COLUMN edushift.competencies.code           IS 'Stable upper-case identifier; unique per course case-insensitive (e.g. "COMU_C1").';
COMMENT ON COLUMN edushift.competencies.display_order  IS 'Stable 1-based order; mutated via service-side two-pass reorder (mirrors grades / units).';
COMMENT ON COLUMN edushift.competencies.is_active      IS 'When false, the competency is hidden from FE dropdowns but kept for historical reads.';

-- Code unique per course (case-insensitive on non-deleted rows)
CREATE UNIQUE INDEX uk_competencies_course_code_active
    ON edushift.competencies (course_id, lower(code))
    WHERE deleted = false;

-- display_order unique per course on non-deleted rows
CREATE UNIQUE INDEX uk_competencies_course_order_active
    ON edushift.competencies (course_id, display_order)
    WHERE deleted = false;

-- Hot path: list active competencies of a course in display order
CREATE INDEX idx_competencies_tenant_course_active
    ON edushift.competencies (tenant_id, course_id, is_active, display_order)
    WHERE deleted = false;


-- ----------------------------------------------------------------------------
-- capacities
-- ----------------------------------------------------------------------------
CREATE TABLE edushift.capacities (
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
    competency_id     uuid          NOT NULL,

    -- Identity
    code              varchar(40)   NOT NULL,
    name              varchar(300)  NOT NULL,
    description       text,
    display_order     int           NOT NULL,
    is_active         boolean       NOT NULL DEFAULT true,

    CONSTRAINT uk_capacities_public_uuid UNIQUE (public_uuid),

    CONSTRAINT chk_capacities_code_format CHECK (
        code = upper(code)
        AND code ~ '^[A-Z][A-Z0-9_]*$'
    ),

    CONSTRAINT chk_capacities_display_order_positive CHECK (
        display_order >= 1
    ),

    CONSTRAINT chk_capacities_deleted_at_consistent CHECK (
        (deleted = false AND deleted_at IS NULL)
        OR (deleted = true  AND deleted_at IS NOT NULL)
    ),

    CONSTRAINT fk_capacities_competency
        FOREIGN KEY (competency_id) REFERENCES edushift.competencies(id)
        ON DELETE RESTRICT
);

COMMENT ON TABLE  edushift.capacities                  IS 'Capacities under a competency (per-tenant editable). Sprint 5A.';
COMMENT ON COLUMN edushift.capacities.tenant_id        IS 'Tenant scope; managed by Hibernate discriminator multi-tenancy.';
COMMENT ON COLUMN edushift.capacities.competency_id    IS 'FK to competencies.id (within the same tenant). RESTRICT delete.';
COMMENT ON COLUMN edushift.capacities.code             IS 'Stable upper-case identifier; unique per competency case-insensitive.';
COMMENT ON COLUMN edushift.capacities.display_order    IS 'Stable 1-based order; mutated via service-side two-pass reorder.';
COMMENT ON COLUMN edushift.capacities.is_active        IS 'When false, the capacity is hidden from FE dropdowns but kept for historical reads.';

-- Code unique per competency (case-insensitive on non-deleted rows)
CREATE UNIQUE INDEX uk_capacities_competency_code_active
    ON edushift.capacities (competency_id, lower(code))
    WHERE deleted = false;

-- display_order unique per competency on non-deleted rows
CREATE UNIQUE INDEX uk_capacities_competency_order_active
    ON edushift.capacities (competency_id, display_order)
    WHERE deleted = false;

-- Hot path: list active capacities of a competency in display order
CREATE INDEX idx_capacities_tenant_competency_active
    ON edushift.capacities (tenant_id, competency_id, is_active, display_order)
    WHERE deleted = false;


-- ----------------------------------------------------------------------------
-- Triggers
-- ----------------------------------------------------------------------------
CREATE TRIGGER set_updated_at_competencies
    BEFORE UPDATE ON edushift.competencies
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

CREATE TRIGGER set_updated_at_capacities
    BEFORE UPDATE ON edushift.capacities
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();
