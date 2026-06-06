-- =============================================================================
-- V15__create_sections_table.sql
-- Section aggregate (per-tenant). A section is a physical class group:
--   (academic_year_id, grade_id, name) e.g. (2026, "1ro Primaria", "A").
-- Owned by the `academic.section` sub-module (Sprint 4 / BE-4.3).
--
-- Key design decisions:
--   * tenant_id            mandatory; managed by Hibernate @TenantId
--                          discriminator multi-tenancy.
--   * academic_year_id     FK to edushift.academic_years (V13)
--                          ON DELETE RESTRICT — sections must be deleted
--                          before their parent year (preserves history).
--   * grade_id             FK to edushift.grades (V14)
--                          ON DELETE RESTRICT — same reasoning; the
--                          GRADE_HAS_SECTIONS error contract is enforced
--                          at the service layer for a clean 409 instead
--                          of a 500.
--   * name                 short label like "A", "B", "Aula Roja";
--                          unique per (year, grade) on non-deleted rows
--                          (case-insensitive).
--   * capacity             optional cap on enrollments (BE-4.8 will
--                          enforce). Stored nullable.
--   * display_order        manual UI ordering (1, 2, 3...). When ties
--                          exist, name asc breaks the tie.
-- =============================================================================

CREATE TABLE edushift.sections (
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

    -- Relationships (within the same tenant)
    academic_year_id      uuid          NOT NULL,
    grade_id              uuid          NOT NULL,

    -- Identity / config
    name                  varchar(40)   NOT NULL,
    capacity              int,
    display_order         int           NOT NULL DEFAULT 1,

    CONSTRAINT uk_sections_public_uuid UNIQUE (public_uuid),

    CONSTRAINT chk_sections_capacity_positive CHECK (
        capacity IS NULL OR capacity >= 1
    ),

    CONSTRAINT chk_sections_display_order_positive CHECK (
        display_order >= 1
    ),

    CONSTRAINT chk_sections_deleted_at_consistent CHECK (
        (deleted = false AND deleted_at IS NULL)
        OR (deleted = true  AND deleted_at IS NOT NULL)
    ),

    CONSTRAINT fk_sections_academic_year
        FOREIGN KEY (academic_year_id) REFERENCES edushift.academic_years(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_sections_grade
        FOREIGN KEY (grade_id) REFERENCES edushift.grades(id)
        ON DELETE RESTRICT
);

COMMENT ON TABLE  edushift.sections                  IS 'Physical class group within a (year, grade) of a tenant.';
COMMENT ON COLUMN edushift.sections.tenant_id        IS 'Tenant scope; managed by Hibernate discriminator multi-tenancy';
COMMENT ON COLUMN edushift.sections.academic_year_id IS 'FK to academic_years.id (within the same tenant). RESTRICT delete.';
COMMENT ON COLUMN edushift.sections.grade_id         IS 'FK to grades.id (within the same tenant). RESTRICT delete.';
COMMENT ON COLUMN edushift.sections.name             IS 'Short label like "A", "B", "Aula Roja"; unique per (year, grade) case-insensitive.';
COMMENT ON COLUMN edushift.sections.capacity         IS 'Optional cap on enrollments; enforced by BE-4.8 enrollment service.';
COMMENT ON COLUMN edushift.sections.display_order    IS 'Manual UI ordering inside the same (year, grade); ties broken by name asc.';

-- ----------------------------------------------------------------------------
-- Indexes (partial: only non-deleted rows participate; hot path stays small)
-- ----------------------------------------------------------------------------

-- Unique: (year, grade, name) on non-deleted rows (case-insensitive)
CREATE UNIQUE INDEX uk_sections_year_grade_name_active
    ON edushift.sections (academic_year_id, grade_id, lower(name))
    WHERE deleted = false;

-- Hot path: list all sections of a year (default filter when none supplied)
CREATE INDEX idx_sections_tenant_year
    ON edushift.sections (tenant_id, academic_year_id, display_order, name)
    WHERE deleted = false;

-- Hot path: list all sections of a (year, grade)
CREATE INDEX idx_sections_year_grade_order
    ON edushift.sections (academic_year_id, grade_id, display_order, name)
    WHERE deleted = false;

-- Hot path: filter by grade across years (rare but used by reports)
CREATE INDEX idx_sections_tenant_grade
    ON edushift.sections (tenant_id, grade_id)
    WHERE deleted = false;

-- ----------------------------------------------------------------------------
-- Triggers
-- ----------------------------------------------------------------------------
CREATE TRIGGER set_updated_at_sections
    BEFORE UPDATE ON edushift.sections
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();
