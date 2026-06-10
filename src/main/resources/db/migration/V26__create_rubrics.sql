-- =====================================================================
-- V26 - Sprint 5B / BE-5B.2 - Evaluations.Rubric
--
-- A rubric is a per-tenant scoring template with a JSONB list of
-- weighted criteria and a JSONB list of achievement levels. It's the
-- domain object that lets an evaluation of kind=RUBRIC produce a
-- matrix-style grade (criterion x student) instead of a single score.
--
-- The MINEDU seed materializes ~10 rubrics as `is_system = true` on
-- the first GET /rubrics/system of a tenant (ADR-5B.10). Tenant-owned
-- rubrics have `is_system = false` and `parent_rubric_id` may point to
-- the system rubric they were forked from (ADR-5B.3 / fork semantics).
--
-- Multi-tenant via tenant_id + Hibernate's @TenantId discriminator
-- on TenantAwareEntity. Soft-delete via `deleted` flag.
-- =====================================================================

CREATE TABLE edushift.rubrics (
    id                          uuid          PRIMARY KEY,
    tenant_id                   uuid          NOT NULL,
    public_uuid                 uuid          NOT NULL,
    created_at                  timestamptz   NOT NULL,
    updated_at                  timestamptz   NOT NULL,
    created_by                  uuid,
    updated_by                  uuid,
    deleted                     boolean       NOT NULL DEFAULT false,
    deleted_at                  timestamptz,

    -- Identidad.
    name                        varchar(160)  NOT NULL,
    description                 text,

    -- Payload pedagogico.
    -- criteria: jsonb array of objects
    --   [{ "key":"redaccion", "name":"Redaccion", "description":"...", "weight":40.0,
    --      "descriptors":[{ "level":"EN_INICIO","text":"..." }, ...] }, ...]
    -- 1..10 criterios, weight por criterio en [0, 100], suma = 100.0
    -- (validacion por el service RUB_CRITERIA_WEIGHT_SUM).
    criteria                    jsonb         NOT NULL,

    -- levels: jsonb array of objects (orden importa; orden canonico MINEDU)
    --   [{ "code":"EN_INICIO","name":"En inicio","order":1 },
    --    { "code":"EN_PROCESO","name":"En proceso","order":2 },
    --    { "code":"ESPERADO","name":"Esperado","order":3 },
    --    { "code":"SOBRESALIENTE","name":"Sobresaliente","order":4 }]
    -- 2..4 niveles. Para MVP, los 4 niveles son los canonicos MINEDU
    -- (RubricLevel enum). Tenants pueden usar 2 o 3 si lo desean.
    levels                       jsonb         NOT NULL,

    -- Semilla / fork.
    is_system                   boolean       NOT NULL DEFAULT false,
    parent_rubric_id            uuid,

    -- Estado.
    is_active                   boolean       NOT NULL DEFAULT true,

    CONSTRAINT uk_rubrics_public_uuid
        UNIQUE (public_uuid),

    -- criteria y levels deben ser JSON arrays (no objetos sueltos).
    -- jsonb_typeof devuelve 'array' para arrays; cualquier otra cosa falla.
    CONSTRAINT chk_rubrics_criteria_is_array
        CHECK (jsonb_typeof(criteria) = 'array'),
    CONSTRAINT chk_rubrics_levels_is_array
        CHECK (jsonb_typeof(levels) = 'array'),

    -- is_system=true implica que parent_rubric_id es null
    -- (el seed es la fuente; no se forka a si mismo).
    -- is_system=false PERMITE parent_rubric_id no null (fork)
    -- o null (rubrica creada desde cero por el tenant).
    CONSTRAINT chk_rubrics_system_no_parent
        CHECK (
            (is_system = true  AND parent_rubric_id IS NULL)
         OR (is_system = false)
        ),

    CONSTRAINT fk_rubrics_parent
        FOREIGN KEY (parent_rubric_id)
        REFERENCES edushift.rubrics (id)
        ON DELETE RESTRICT
);

-- Hot path: listar rubricas visibles para un tenant (sistema + propias),
-- ordenadas por is_system DESC (sistema primero), luego por nombre.
CREATE INDEX idx_rubrics_tenant_active
    ON edushift.rubrics (tenant_id, is_system DESC, name)
    WHERE NOT deleted;

-- Hot path: listar solo las rubricas del sistema (seed MINEDU).
CREATE INDEX idx_rubrics_tenant_system
    ON edushift.rubrics (tenant_id, name)
    WHERE NOT deleted AND is_system = true;

-- Reverse lookup: "que forks salieron de esta rubrica del sistema".
CREATE INDEX idx_rubrics_tenant_parent
    ON edushift.rubrics (tenant_id, parent_rubric_id)
    WHERE NOT deleted AND parent_rubric_id IS NOT NULL;

-- Case-insensitive uniqueness para (tenant, name) en rubricas no-seed
-- (las rubricas del seed son globales, pero por tenant se materializan
--  en una fila con is_system=true, asi que la unicidad sigue aplicando).
-- Implementado con indice unico sobre lower(name) para emular citext
-- sin habilitar la extension.
CREATE UNIQUE INDEX uk_rubrics_tenant_name_ci
    ON edushift.rubrics (tenant_id, lower(name))
    WHERE NOT deleted;

CREATE TRIGGER set_updated_at_rubrics
    BEFORE UPDATE ON edushift.rubrics
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON TABLE  edushift.rubrics                       IS 'Scoring template per-tenant with weighted criteria and achievement levels (Sprint 5B / BE-5B.2).';
COMMENT ON COLUMN edushift.rubrics.criteria              IS 'JSONB array of weighted criteria (1..10 items, sum of weights = 100.0). Validated by service (RUB_CRITERIA_WEIGHT_SUM).';
COMMENT ON COLUMN edushift.rubrics.levels                 IS 'JSONB array of achievement levels (2..4 items). Canonical: EN_INICIO, EN_PROCESO, ESPERADO, SOBRESALIENTE.';
COMMENT ON COLUMN edushift.rubrics.is_system              IS 'TRUE si la rubrica viene del seed MINEDU. Read-only en UI; forkable per-tenant.';
COMMENT ON COLUMN edushift.rubrics.parent_rubric_id       IS 'FK a edushift.rubrics.id cuando esta rubrica es un fork per-tenant de una rubrica del seed. NULL si fue creada desde cero o si es del seed.';
