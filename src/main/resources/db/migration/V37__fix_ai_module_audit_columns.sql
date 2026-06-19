-- =============================================================================
-- V37 - Sprint 7c / fix - Audit columns faltantes en el modulo AI (V36).
--
-- Contexto:
--   V36 creo las tablas tenant_ai_settings, tenant_ai_usage y ai_generations,
--   pero olvido las columnas que las entidades JPA heredan de BaseEntity
--   ({@code id, created_at, updated_at, deleted}) y AuditableEntity
--   ({@code created_by, updated_by}). Concretamente:
--
--     * ai_generations      -> faltaba updated_at y deleted (extends BaseEntity)
--     * tenant_ai_usage     -> faltaba deleted (extends BaseEntity)
--     * tenant_ai_settings  -> faltaba deleted (extends AuditableEntity)
--
--   Al levantar el back con spring.jpa.hibernate.ddl-auto=validate, Hibernate
--   fallaba en arranque con:
--     Schema-validation: missing column [deleted] in table [edushift.ai_generations]
--
-- Decision (forward-only):
--   No se modifica V36 (regla de Flyway del proyecto: V<n> es inmutable una
--   vez aplicada). Se anaden las columnas con ALTER TABLE + defaults seguros,
--   y se crean los triggers set_updated_at_* que tambien faltaban como
--   defensa-en-profundidad para UPDATEs hechos por SQL directo / DBA.
--
-- Idempotencia: usa IF NOT EXISTS para que sea seguro re-correr en entornos
-- donde alguien haya arreglado parcialmente a mano antes de esta migracion.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. ai_generations: add updated_at + deleted + trigger
-- -----------------------------------------------------------------------------
ALTER TABLE edushift.ai_generations
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();

ALTER TABLE edushift.ai_generations
    ADD COLUMN IF NOT EXISTS deleted boolean NOT NULL DEFAULT false;

DROP TRIGGER IF EXISTS set_updated_at_ai_generations ON edushift.ai_generations;
CREATE TRIGGER set_updated_at_ai_generations
    BEFORE UPDATE ON edushift.ai_generations
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON COLUMN edushift.ai_generations.updated_at IS 'Last mutation timestamp. Auto-updated by trigger set_updated_at_ai_generations (defense-in-depth on top of Spring Data Auditing).';
COMMENT ON COLUMN edushift.ai_generations.deleted    IS 'Soft-delete flag inherited from BaseEntity. Default false. Audit rows are never hard-deleted; old rows age out via the TTL job (DEBT-BE-7C-1).';

-- -----------------------------------------------------------------------------
-- 2. tenant_ai_usage: add deleted + trigger
-- -----------------------------------------------------------------------------
ALTER TABLE edushift.tenant_ai_usage
    ADD COLUMN IF NOT EXISTS deleted boolean NOT NULL DEFAULT false;

DROP TRIGGER IF EXISTS set_updated_at_tenant_ai_usage ON edushift.tenant_ai_usage;
CREATE TRIGGER set_updated_at_tenant_ai_usage
    BEFORE UPDATE ON edushift.tenant_ai_usage
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON COLUMN edushift.tenant_ai_usage.deleted IS 'Soft-delete flag inherited from BaseEntity. Daily counters are never hard-deleted; aged out via TTL job (DEBT-BE-7C-1).';

-- -----------------------------------------------------------------------------
-- 3. tenant_ai_settings: add deleted + trigger
-- -----------------------------------------------------------------------------
ALTER TABLE edushift.tenant_ai_settings
    ADD COLUMN IF NOT EXISTS deleted boolean NOT NULL DEFAULT false;

DROP TRIGGER IF EXISTS set_updated_at_tenant_ai_settings ON edushift.tenant_ai_settings;
CREATE TRIGGER set_updated_at_tenant_ai_settings
    BEFORE UPDATE ON edushift.tenant_ai_settings
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON COLUMN edushift.tenant_ai_settings.deleted IS 'Soft-delete flag inherited from BaseEntity. Disabling AI for a tenant uses ai_enabled=false; this column exists only because every entity inherits it.';
