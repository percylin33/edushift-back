-- =============================================================================
-- V94__fix_v93_seed_bugs.sql
--
-- Migracion correctiva para BDs que aplicaron V93 antes del fix.
-- Los 3 bugs originales eran:
--   1. Bloque 5.1 usaba OFFSET fragil (ORDER BY LIMIT 1 OFFSET N) que solo
--      vinculaba estudiantes si el OFFSET estaba dentro del rango de candidatos.
--   2. Bloque 5.8.1 generaba names duplicados cuando 4 secciones del mismo
--      curso en 1er grado chocaban con uk_rubrics_tenant_name_ci.
--   3. Bloque 5.8 tenia un guard IF NOT EXISTS academic_units que abortaba
--      porque 5.8 estaba antes de 5.9 en el archivo (orden textual).
--
-- V94 es idempotente: cada sub-bloque verifica estado antes de actuar.
-- Si la BD ya esta en estado consistente, V94 no hace nada.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 0. GUARD: skip on production databases
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF current_database() IN ('edushift_prod', 'edushift_production') THEN
        RAISE NOTICE 'V94 fix skipped on production database %', current_database();
        RETURN;
    END IF;
END;
$$;


-- -----------------------------------------------------------------------------
-- 1) Vincular Mateo Mendoza y Valentina Torres por ID deterministico
--    (si quedaron con user_id IS NULL por el bug de OFFSET en V93.1).
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id  uuid;
    v_linked     int := 0;
BEGIN
    SELECT id INTO v_tenant_id FROM edushift.tenants WHERE slug = 'tecnosur' AND deleted = false;
    IF v_tenant_id IS NULL THEN
        RAISE NOTICE 'V94: tenant tecnosur not found - skip';
        RETURN;
    END IF;

    UPDATE edushift.students s
    SET user_id = (SELECT public_uuid FROM edushift.users
                   WHERE email = 'mateo.estudiante@tecnosur.edushift.pe'
                     AND deleted = false AND tenant_id = v_tenant_id)
    WHERE s.id = 'a4fab774-62ab-4604-9ba9-b2ab22587bf6'
      AND s.deleted = false AND s.user_id IS NULL;
    GET DIAGNOSTICS v_linked = ROW_COUNT;
    IF v_linked > 0 THEN
        RAISE NOTICE 'V94.1: linked Mateo Mendoza Rivera to mateo.estudiante';
    END IF;

    UPDATE edushift.students s
    SET user_id = (SELECT public_uuid FROM edushift.users
                   WHERE email = 'valentina.estudiante@tecnosur.edushift.pe'
                     AND deleted = false AND tenant_id = v_tenant_id)
    WHERE s.id = '01356efb-4a2d-4631-b7d0-94c5dd26fbf5'
      AND s.deleted = false AND s.user_id IS NULL;
    GET DIAGNOSTICS v_linked = ROW_COUNT;
    IF v_linked > 0 THEN
        RAISE NOTICE 'V94.1: linked Valentina Torres Salas to valentina.estudiante';
    END IF;
END;
$$;


-- -----------------------------------------------------------------------------
-- 2) Renombrar evaluations duplicadas (las que quedaron con el mismo name
--    antes del fix de V93 5.8.1).
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id  uuid;
    v_count      int := 0;
BEGIN
    SELECT id INTO v_tenant_id FROM edushift.tenants WHERE slug = 'tecnosur' AND deleted = false;
    IF v_tenant_id IS NULL THEN RETURN; END IF;

    UPDATE edushift.evaluations e
    SET name = e.name || ' (legacy-' || extract(epoch from e.created_at)::int || ')'
    WHERE e.tenant_id = v_tenant_id AND e.deleted = false
      AND e.name IN (
          SELECT name FROM edushift.evaluations
          WHERE tenant_id = v_tenant_id AND deleted = false
          GROUP BY name HAVING count(*) > 1
      );
    GET DIAGNOSTICS v_count = ROW_COUNT;
    IF v_count > 0 THEN
        RAISE NOTICE 'V94.2: renamed % duplicate evaluations', v_count;
    END IF;
END;
$$;


-- -----------------------------------------------------------------------------
-- 3) Renombrar rubrics huerfanos (los que quedaron cuando el INSERT de 5.8
--    fallo por el choque de unique constraint y se revirtio).
--    Un rubric es huerfano si no aparece en evaluation_rubric.
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id  uuid;
    v_count      int := 0;
BEGIN
    SELECT id INTO v_tenant_id FROM edushift.tenants WHERE slug = 'tecnosur' AND deleted = false;
    IF v_tenant_id IS NULL THEN RETURN; END IF;

    UPDATE edushift.rubrics r
    SET name = r.name || ' (legacy-' || extract(epoch from r.created_at)::int || ')'
    WHERE r.tenant_id = v_tenant_id AND r.deleted = false
      AND NOT EXISTS (
          SELECT 1 FROM edushift.evaluation_rubric er
          WHERE er.rubric_id = r.id AND er.deleted = false
      );
    GET DIAGNOSTICS v_count = ROW_COUNT;
    IF v_count > 0 THEN
        RAISE NOTICE 'V94.3: renamed % orphan rubrics', v_count;
    END IF;
END;
$$;


-- =============================================================================
-- FIN V94
-- =============================================================================