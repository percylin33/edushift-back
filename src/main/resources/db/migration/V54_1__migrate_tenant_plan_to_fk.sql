-- =============================================================================
-- V54_1__migrate_tenant_plan_to_fk.sql
--
-- Sprint 15 (SUPER_ADMIN module) / BE-15.3 — Migrar tenants.plan (varchar) a
-- tenants.plan_id (FK → platform_plans).
--
-- Cambia el modelo:
--   • Antes: tenants.plan = 'TRIAL' | 'BASIC' | 'PRO' | 'ENTERPRISE' (varchar)
--   • Ahora: tenants.plan_id = uuid (FK → platform_plans)
--
-- El valor textual `tenants.plan` se mantiene por compatibilidad (JPA entity
-- existente puede seguir leyéndolo) pero ya NO se usa. La columna eventually
-- se eliminará en una migración futura (DEBT-SUPERADMIN-CLEANUP-1).
--
-- Reglas de migración de datos:
--   1. Si tenant.plan existe en platform_plans.code → FK al plan correspondiente.
--   2. Si tenant.plan es NULL o no mappable → asignar BASIC por defecto.
--   3. Tenants con plan='TRIAL' también migran (TRIAL no es plan SaaS actual;
--      el sentinel lo recibe al crear el tenant).
--
-- Idempotencia: usa IF NOT EXISTS en el ALTER COLUMN. Si la migración se re-ejecuta,
-- no rompe.
-- =============================================================================

-- 1. Agregar columna plan_id como nullable inicialmente
ALTER TABLE edushift.tenants
    ADD COLUMN IF NOT EXISTS plan_id uuid
    REFERENCES edushift.platform_plans(id) ON DELETE SET NULL;

-- 2. Backfill: mapear tenants.plan (varchar) → tenants.plan_id (FK)
DO $$
DECLARE
    v_default_plan_id uuid;
BEGIN
    -- Seleccionar el plan por defecto (BASIC) si no se puede mapear
    SELECT id INTO v_default_plan_id
    FROM edushift.platform_plans
    WHERE code = 'BASIC' AND deleted = false
    LIMIT 1;

    IF v_default_plan_id IS NULL THEN
        RAISE EXCEPTION 'V54_1: BASIC plan not found in platform_plans. Did V54 run?';
    END IF;

    -- Mapear los planes de los tenants existentes
    UPDATE edushift.tenants t
    SET plan_id = p.id
    FROM edushift.platform_plans p
    WHERE t.plan_id IS NULL
      AND t.deleted = false
      AND p.code = t.plan
      AND p.deleted = false;

    -- Asignar BASIC a los tenants que no pudieron mapearse
    UPDATE edushift.tenants
    SET plan_id = v_default_plan_id
    WHERE plan_id IS NULL
      AND deleted = false;

    -- El tenant sentinel siempre va a ENTERPRISE (aunque normalmente ya está bien)
    UPDATE edushift.tenants
    SET plan_id = (SELECT id FROM edushift.platform_plans WHERE code = 'ENTERPRISE' LIMIT 1)
    WHERE id = '00000000-0000-0000-0000-000000000001';
END$$;

-- 3. Hacer NOT NULL (post-backfill, todos deben tener plan_id)
DO $$
BEGIN
    -- Verificar que todos los tenants activos tengan plan_id
    IF EXISTS (
        SELECT 1 FROM edushift.tenants
        WHERE plan_id IS NULL AND deleted = false
    ) THEN
        RAISE WARNING 'V54_1: Some tenants still have null plan_id. Check data.';
    END IF;

    -- Hacer la columna NOT NULL
    ALTER TABLE edushift.tenants
        ALTER COLUMN plan_id SET NOT NULL;
END$$;

-- 4. Indice para dashboards (lookup tenant por plan)
CREATE INDEX IF NOT EXISTS idx_tenants_plan_id
    ON edushift.tenants (plan_id)
    WHERE deleted = false;

COMMENT ON COLUMN edushift.tenants.plan_id IS
    'FK al plan actual del tenant (Sprint 15). FK a platform_plans.id. '
    'Reemplaza el enum varchar tenants.plan; esa columna se mantiene por '
    'compatibilidad pero esta es la fuente de verdad.';
COMMENT ON COLUMN edushift.tenants.plan IS
    'LEGACY: enum varchar con el codigo del plan (BASIC, PRO, ENTERPRISE, TRIAL). '
    'DEPRECADO en Sprint 15 — usar plan_id. Se mantiene por compatibilidad. '
    'Pendiente eliminar: DEBT-SUPERADMIN-CLEANUP-1.';
