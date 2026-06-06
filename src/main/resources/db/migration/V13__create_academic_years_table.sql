-- =============================================================================
-- V13__create_academic_years_table.sql
-- Academic year aggregate (per-tenant). Owned by the `academic.year` module.
--
-- Key design decisions (Sprint 4 — see docs/product/sprints/sprint-04-teachers-academic.md):
--   * `id`           internal UUIDv7 (time-ordered, never exposed via API)
--   * `public_uuid`  external UUIDv4 surfaced to clients
--   * `tenant_id`    mandatory; managed by Hibernate discriminator
--                    multi-tenancy (@TenantId on TenantAwareEntity)
--   * `status`       lifecycle PLANNING -> ACTIVE -> CLOSED.
--                    A unique partial index enforces "only one ACTIVE per
--                    tenant" at the DB level (defense in depth — see ADR-04.4).
--   * `name`         human label like "2026" or "Ciclo 2026-I". Unique
--                    per-tenant (case-insensitive) on non-deleted rows.
--   * `start_date` / `end_date` define the academic calendar window. Must
--                    obey start < end. Periods (V17, BE-4.5) and sections
--                    (V15, BE-4.3) anchor here.
-- =============================================================================

CREATE TABLE edushift.academic_years (
    id              uuid          PRIMARY KEY,
    tenant_id       uuid          NOT NULL,
    public_uuid     uuid          NOT NULL,

    -- Audit (inherited from AuditableEntity / BaseEntity)
    created_at      timestamptz   NOT NULL,
    updated_at      timestamptz   NOT NULL,
    created_by      uuid,
    updated_by      uuid,
    deleted         boolean       NOT NULL DEFAULT false,
    deleted_at      timestamptz,

    -- Identity / lifecycle
    name            varchar(50)   NOT NULL,
    status          varchar(20)   NOT NULL DEFAULT 'PLANNING',

    -- Calendar window
    start_date      date          NOT NULL,
    end_date        date          NOT NULL,

    CONSTRAINT uk_academic_years_public_uuid UNIQUE (public_uuid),

    CONSTRAINT chk_academic_years_status CHECK (
        status IN ('PLANNING', 'ACTIVE', 'CLOSED')
    ),

    CONSTRAINT chk_academic_years_date_order CHECK (
        start_date < end_date
    ),

    CONSTRAINT chk_academic_years_deleted_at_consistent CHECK (
        (deleted = false AND deleted_at IS NULL)
        OR (deleted = true  AND deleted_at IS NOT NULL)
    )
);

COMMENT ON TABLE  edushift.academic_years              IS 'Academic year per tenant (calendar window + lifecycle).';
COMMENT ON COLUMN edushift.academic_years.public_uuid  IS 'External, stable identifier exposed via REST (UUIDv4)';
COMMENT ON COLUMN edushift.academic_years.tenant_id    IS 'Tenant scope; managed by Hibernate discriminator multi-tenancy';
COMMENT ON COLUMN edushift.academic_years.status       IS 'Lifecycle (AcademicYearStatus): PLANNING|ACTIVE|CLOSED';
COMMENT ON COLUMN edushift.academic_years.name         IS 'Human label (e.g. "2026", "Ciclo 2026-I"); unique per tenant case-insensitive';

-- ----------------------------------------------------------------------------
-- Indexes (partial: only non-deleted rows participate, hot path stays small)
-- ----------------------------------------------------------------------------

-- A given (tenant, name) identifies one academic year (case-insensitive)
CREATE UNIQUE INDEX uk_academic_years_tenant_name_active
    ON edushift.academic_years (tenant_id, lower(name))
    WHERE deleted = false;

-- Defense in depth: only one ACTIVE academic year per tenant. Activating
-- a new year requires CLOSE-ing the current ACTIVE in the same tx
-- (handled by AcademicYearServiceImpl.activate). The unique partial
-- index is the last line of defense against concurrent activate calls.
CREATE UNIQUE INDEX uk_academic_years_tenant_active
    ON edushift.academic_years (tenant_id)
    WHERE status = 'ACTIVE' AND deleted = false;

-- Hot path: list with status filter (e.g. show planning + active)
CREATE INDEX idx_academic_years_tenant_status
    ON edushift.academic_years (tenant_id, status)
    WHERE deleted = false;

-- Hot path: pivot the academic calendar by date (used by sessions in Sprint 5)
CREATE INDEX idx_academic_years_tenant_dates
    ON edushift.academic_years (tenant_id, start_date, end_date)
    WHERE deleted = false;

-- ----------------------------------------------------------------------------
-- Triggers
-- ----------------------------------------------------------------------------
CREATE TRIGGER set_updated_at_academic_years
    BEFORE UPDATE ON edushift.academic_years
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();
