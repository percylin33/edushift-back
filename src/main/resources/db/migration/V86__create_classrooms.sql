-- =============================================================================
-- V86__create_classrooms.sql
--
-- Cierre-C / B4 -- Schedule v2: physical classrooms catalog.
--
-- Tabla `classrooms`: aulas fisicas configurables por tenant. Reemplaza
-- gradualmente el campo libre `time_slots.classroom` (V86 + V87 permiten
-- que coexistan: el campo legacy sigue siendo la fuente de verdad para
-- los slots pre-B4, mientras `classroom_id` es la FK para los nuevos).
--
-- Decisiones:
--   * ADR-Cierre-C.4 -- append-only con soft-delete (`deleted_at`).
--   * ADR-Cierre-C.4 -- `code` unico por tenant (no global). Permite
--     que dos tenants tengan "A1" sin colisionar.
--   * ADR-Cierre-C.4 -- `type` enum constrained (CLASSROOM|LAB|GYM|...)
--     para que el FE pueda filtrar / diferenciar sin texto libre.
--   * ADR-Cierre-C.4 -- `capacity` int >= 0 (null = capacidad desconocida,
--     se usa cuando el admin no la lleno todavia).
--   * ADR-Cierre-C.4 -- multi-tenant via columna `tenant_id` +
--     Hibernate `@TenantId` discriminator.
-- =============================================================================

CREATE TABLE IF NOT EXISTS edushift.classrooms (
    id                      uuid          PRIMARY KEY,
    tenant_id               uuid          NOT NULL,
    public_uuid             uuid          NOT NULL,

    -- Audit (BaseEntity contract)
    created_at              timestamptz   NOT NULL,
    updated_at              timestamptz   NOT NULL,
    created_by              uuid,
    updated_by              uuid,
    deleted                 boolean       NOT NULL DEFAULT false,
    deleted_at              timestamptz,

    -- Catalog
    code                    varchar(40)   NOT NULL,
    name                    varchar(120)  NOT NULL,
    type                    varchar(40)   NOT NULL,
    capacity                integer,
    location                varchar(160),
    description             varchar(500),

    CONSTRAINT uk_classrooms_public_uuid UNIQUE (public_uuid),

    CONSTRAINT chk_classrooms_type CHECK (
        type IN ('CLASSROOM', 'LAB', 'GYM', 'LIBRARY', 'AUDITORIUM', 'OUTDOOR', 'OTHER')
    ),

    CONSTRAINT chk_classrooms_capacity_non_negative CHECK (
        capacity IS NULL OR capacity >= 0
    ),

    CONSTRAINT chk_classrooms_deleted_at_consistent CHECK (
        (deleted = false AND deleted_at IS NULL)
     OR (deleted = true  AND deleted_at IS NOT NULL)
    ),

    CONSTRAINT fk_classrooms_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES edushift.tenants (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_classrooms_tenant_active
    ON edushift.classrooms (tenant_id, type)
    WHERE deleted = false;

-- Per-tenant unique on `code` ignoring soft-deleted rows. PostgreSQL does
-- NOT accept `UNIQUE (cols) WHERE <cond>` as a CONSTRAINT (same trap as
-- V82 — see ADR-Cierre-A.5). The correct construct is a PARTIAL UNIQUE
-- INDEX. ADR-Cierre-C.4.
CREATE UNIQUE INDEX uk_classrooms_tenant_code
    ON edushift.classrooms (tenant_id, code)
    WHERE deleted = false;

CREATE TRIGGER set_updated_at_classrooms
    BEFORE UPDATE ON edushift.classrooms
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON TABLE  edushift.classrooms              IS 'Tenant-defined physical classrooms (Sprint cierre-C / B4).';
COMMENT ON COLUMN edushift.classrooms.code         IS 'Stable short code per tenant (e.g. A1, LAB-FIS). Unique within tenant when not deleted.';
COMMENT ON COLUMN edushift.classrooms.type         IS 'CLASSROOM | LAB | GYM | LIBRARY | AUDITORIUM | OUTDOOR | OTHER.';
COMMENT ON COLUMN edushift.classrooms.capacity     IS 'Integer >= 0; NULL = capacidad desconocida.';
COMMENT ON COLUMN edushift.classrooms.location      IS 'Free-text location hint (e.g. "Piso 2, ala norte").';