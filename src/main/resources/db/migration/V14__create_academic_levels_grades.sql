-- =============================================================================
-- V14__create_academic_levels_grades.sql
-- Academic catalog: levels + grades (per-tenant, both seeded on signup).
-- Owned by the `academic.levelgrade` sub-module (Sprint 4 / BE-4.2).
--
-- Key design decisions:
--   * `academic_levels`  — coarse education stage (INICIAL/PRIMARIA/SECUNDARIA
--                          by default, but extensible per tenant: a Cambridge
--                          school may add IGCSE/IB_DIPLOMA without code changes).
--   * `grades`           — fine-grained progression inside a level.
--                          (level_id, ordinal) is unique per tenant on
--                          non-deleted rows.
--   * Seed strategy      — `AcademicSeedService.seedDefaults(tenantId)` is
--                          invoked from `TenantServiceImpl.register` right
--                          after the admin user is persisted, inside the
--                          same `TenantContext.runAs` transaction.
--   * No FK to tenants   — same project convention as students/users:
--                          Hibernate's @TenantId discriminator is the guardian.
-- =============================================================================

-- ----------------------------------------------------------------------------
-- academic_levels
-- ----------------------------------------------------------------------------
CREATE TABLE edushift.academic_levels (
    id              uuid          PRIMARY KEY,
    tenant_id       uuid          NOT NULL,
    public_uuid     uuid          NOT NULL,

    -- Audit
    created_at      timestamptz   NOT NULL,
    updated_at      timestamptz   NOT NULL,
    created_by      uuid,
    updated_by      uuid,
    deleted         boolean       NOT NULL DEFAULT false,
    deleted_at      timestamptz,

    -- Identity
    code            varchar(40)   NOT NULL,
    name            varchar(100)  NOT NULL,
    ordinal         int           NOT NULL,

    CONSTRAINT uk_academic_levels_public_uuid UNIQUE (public_uuid),

    CONSTRAINT chk_academic_levels_code_format CHECK (
        code = upper(code)
        AND code ~ '^[A-Z][A-Z0-9_]*$'
    ),

    CONSTRAINT chk_academic_levels_ordinal_positive CHECK (ordinal >= 1),

    CONSTRAINT chk_academic_levels_deleted_at_consistent CHECK (
        (deleted = false AND deleted_at IS NULL)
        OR (deleted = true  AND deleted_at IS NOT NULL)
    )
);

COMMENT ON TABLE  edushift.academic_levels             IS 'Education stages per tenant (INICIAL/PRIMARIA/SECUNDARIA + custom).';
COMMENT ON COLUMN edushift.academic_levels.code        IS 'Stable upper-case identifier; unique per tenant case-insensitive (used by API consumers and seeding).';
COMMENT ON COLUMN edushift.academic_levels.ordinal     IS 'Display / sort order (lower = earlier in the curriculum).';
COMMENT ON COLUMN edushift.academic_levels.tenant_id   IS 'Tenant scope; managed by Hibernate discriminator multi-tenancy';

-- Code is unique per tenant (case-insensitive on non-deleted rows)
CREATE UNIQUE INDEX uk_academic_levels_tenant_code_active
    ON edushift.academic_levels (tenant_id, lower(code))
    WHERE deleted = false;

-- Hot path: render the catalog sorted by ordinal
CREATE INDEX idx_academic_levels_tenant_ordinal
    ON edushift.academic_levels (tenant_id, ordinal)
    WHERE deleted = false;

CREATE TRIGGER set_updated_at_academic_levels
    BEFORE UPDATE ON edushift.academic_levels
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();


-- ----------------------------------------------------------------------------
-- grades
-- ----------------------------------------------------------------------------
CREATE TABLE edushift.grades (
    id              uuid          PRIMARY KEY,
    tenant_id       uuid          NOT NULL,
    public_uuid     uuid          NOT NULL,

    -- Audit
    created_at      timestamptz   NOT NULL,
    updated_at      timestamptz   NOT NULL,
    created_by      uuid,
    updated_by      uuid,
    deleted         boolean       NOT NULL DEFAULT false,
    deleted_at      timestamptz,

    -- Relationship
    level_id        uuid          NOT NULL,

    -- Identity
    name            varchar(100)  NOT NULL,
    ordinal         int           NOT NULL,

    CONSTRAINT uk_grades_public_uuid UNIQUE (public_uuid),

    CONSTRAINT chk_grades_ordinal_positive CHECK (ordinal >= 1),

    CONSTRAINT chk_grades_deleted_at_consistent CHECK (
        (deleted = false AND deleted_at IS NULL)
        OR (deleted = true  AND deleted_at IS NOT NULL)
    ),

    -- We DO want a FK to academic_levels because both live in the same
    -- tenant; the FK enforces referential integrity inside the row's
    -- tenant. RESTRICT on delete forces "delete the grade first" to
    -- match the LEVEL_HAS_GRADES error contract.
    CONSTRAINT fk_grades_level
        FOREIGN KEY (level_id) REFERENCES edushift.academic_levels(id)
        ON DELETE RESTRICT
);

COMMENT ON TABLE  edushift.grades              IS 'Grade progression per academic level (per-tenant).';
COMMENT ON COLUMN edushift.grades.tenant_id    IS 'Tenant scope; managed by Hibernate discriminator multi-tenancy';
COMMENT ON COLUMN edushift.grades.level_id     IS 'FK to academic_levels.id (within the same tenant). RESTRICT delete.';
COMMENT ON COLUMN edushift.grades.ordinal      IS 'Order inside the parent level (1ro = 1, 2do = 2, ...).';

-- (level, ordinal) is unique per tenant on non-deleted rows
CREATE UNIQUE INDEX uk_grades_level_ordinal_active
    ON edushift.grades (tenant_id, level_id, ordinal)
    WHERE deleted = false;

-- Hot path: list all grades of a level sorted by ordinal
CREATE INDEX idx_grades_tenant_level_ordinal
    ON edushift.grades (tenant_id, level_id, ordinal)
    WHERE deleted = false;

CREATE TRIGGER set_updated_at_grades
    BEFORE UPDATE ON edushift.grades
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();
