-- =============================================================================
-- V92 - Backfill additive de `tenants.plan_id` para los tenants de seed.
--
-- Contexto
-- --------
-- V38 (Sprint 13) y V39 (Sprint 14) insertan tenants con `plan = '<CODE>'`
-- (columna varchar deprecada, ver V54_1). V54_1 ya backfilea `plan_id` desde
-- `plan` siempre que el valor exista en `platform_plans.code`. En BD limpia
-- funciona en orden. PERO, si los seeds se re-ejecutan en un reset manual
-- (wipe-and-reset-db.ps1) o si por algun motivo V54_1 no mapeo un tenant
-- (e.g. `plan = NULL` en demo, ver V5), el `plan_id` puede quedar NULL y
-- la columna pasaria a fallar la constraint NOT NULL posterior.
--
-- Esta migracion es ADITIVA y SAFE-FOR-REPLAY:
--   * NO modifica los INSERTs de V38/V39/V74 (regla Flyway: V<n> inmutable).
--   * Solo se asegura de que `plan_id` este populado en los 3 tenants de
--     seed (`demo`, `tecnosur`, `keola-networks`) por si V54_1 dejo algo
--     con NULL.
--   * Es idempotente: `WHERE plan_id IS NULL`.
--
-- Mapeo explicito (no usamos el `plan` varchar para no depender de el):
--   * demo            -> BASIC       (V5 no asigna plan; cae al default)
--   * tecnosur        -> PRO         (V39 lo declara)
--   * keola-networks  -> ENTERPRISE  (V74 lo declara)
--
-- Autor: plan "Actualizar seeds dev + script wipe-and-reset + seed extendido"
-- 2026-07-29
-- =============================================================================

DO $$
DECLARE
    v_basic_id      uuid;
    v_pro_id        uuid;
    v_enterprise_id uuid;
    v_updated       int := 0;
BEGIN
    -- Resolver los IDs de los planes sembrados por V54. Si V54 no corrio
    -- (improbable pero posible), abortamos con un mensaje claro.
    SELECT id INTO v_basic_id      FROM edushift.platform_plans WHERE code = 'BASIC'      AND deleted = false LIMIT 1;
    SELECT id INTO v_pro_id        FROM edushift.platform_plans WHERE code = 'PRO'        AND deleted = false LIMIT 1;
    SELECT id INTO v_enterprise_id FROM edushift.platform_plans WHERE code = 'ENTERPRISE' AND deleted = false LIMIT 1;

    IF v_basic_id IS NULL OR v_pro_id IS NULL OR v_enterprise_id IS NULL THEN
        RAISE EXCEPTION 'V92: platform_plans missing one of BASIC/PRO/ENTERPRISE. Did V54 run?';
    END IF;

    -- demo -> BASIC
    UPDATE edushift.tenants
    SET    plan_id = v_basic_id
    WHERE  slug    = 'demo'
      AND  deleted = false
      AND  plan_id IS NULL;
    GET DIAGNOSTICS v_updated = ROW_COUNT;
    RAISE NOTICE 'V92: demo -> BASIC (rows updated: %)', v_updated;

    -- tecnosur -> PRO
    v_updated := 0;
    UPDATE edushift.tenants
    SET    plan_id = v_pro_id
    WHERE  slug    = 'tecnosur'
      AND  deleted = false
      AND  plan_id IS NULL;
    GET DIAGNOSTICS v_updated = ROW_COUNT;
    RAISE NOTICE 'V92: tecnosur -> PRO (rows updated: %)', v_updated;

    -- keola-networks -> ENTERPRISE
    v_updated := 0;
    UPDATE edushift.tenants
    SET    plan_id = v_enterprise_id
    WHERE  slug    = 'keola-networks'
      AND  deleted = false
      AND  plan_id IS NULL;
    GET DIAGNOSTICS v_updated = ROW_COUNT;
    RAISE NOTICE 'V92: keola-networks -> ENTERPRISE (rows updated: %)', v_updated;
END$$;

-- Verificacion final: en una BD sana, los 3 tenants deben tener plan_id
-- no-NULL. Si por algun motivo sigue habiendo NULL, levantamos un WARNING
-- (no abortamos, porque V54_1 ya hace el NOT NULL final).
DO $$
DECLARE
    v_null_count int;
BEGIN
    SELECT COUNT(*) INTO v_null_count
    FROM edushift.tenants
    WHERE slug IN ('demo', 'tecnosur', 'keola-networks')
      AND deleted = false
      AND plan_id IS NULL;

    IF v_null_count > 0 THEN
        RAISE WARNING 'V92: % seed tenants still have NULL plan_id after backfill. Investigate.', v_null_count;
    ELSE
        RAISE NOTICE 'V92: all 3 seed tenants have plan_id populated.';
    END IF;
END$$;

-- =============================================================================
-- FIN V92
-- =============================================================================
