-- =============================================================================
-- V74 — seed STUDENT login + reconcile keola-networks tenant.
--
-- WHY
--   * BE-BUG-1 from docs/qa/rbac-audit.md: TEACHER/STAFF/PARENT logins
--     failed because AuthServiceImpl.persistRefreshToken collided on
--     token_hash. Fixed in AuthServiceImpl.java (pre-check) — see the
--     sibling commit. This migration does NOT change auth code; it
--     seeds the missing STUDENT account and reconciles the keola-networks
--     tenant that V5/V38 left incomplete.
--
--   * The STUDENT user is needed for the LMS RBAC matrix
--     (LMS_TASK_SUBMIT, LMS_QUIZ_SUBMIT, etc.) and for the /auth/login
--     flow exercised by e2e/tests/students/* and e2e/tests/lms/*. Without
--     a STUDENT login we cannot E2E-test the student-side RBAC paths.
--
--   * keola-networks already has one user (p3r.valderrama@gmail.com) but
--     no matching tenants row — V5 only seeded the demo tenant. The
--     INSERT below is idempotent (ON CONFLICT DO NOTHING) so re-running
--     this migration is safe on CI.
--
--   * The STUDENT user uses the sentinel prefix SEED_RESET_REQUIRED_v1_
--     which DevDataInitializer.resetSeededUserPasswords() (BE startup)
--     rewrites to a BCrypt of EduShift2026! (default seed password,
--     overridable via DEV_SEED_PASSWORD env).
--
-- IDEMPOTENCY
--   * Both inserts use ON CONFLICT DO NOTHING or an existence-check guard.
--   * Safe to re-run on a DB that already has V74 applied.
-- =============================================================================

-- 1. Reconcile keola-networks tenant (V5 only seeded `demo`).
--    V4 creates a PARTIAL unique index `uk_tenants_slug_lower` on
--    `lower(slug) WHERE deleted = false` — not on the raw `slug` column.
--    ON CONFLICT (slug) cannot match that index, so we guard with an
--    explicit existence check below instead.
DO $$
DECLARE
    v_existing_id uuid;
BEGIN
    SELECT id INTO v_existing_id
    FROM   edushift.tenants
    WHERE  lower(slug) = lower('keola-networks')
      AND  deleted = false;

    IF v_existing_id IS NULL THEN
        INSERT INTO edushift.tenants (
            id, public_uuid, slug, name, status, plan_id, created_at, updated_at, deleted
        ) VALUES (
            '019f53dd-8772-73fa-a964-bb904ecf869e',
            gen_random_uuid(),
            'keola-networks',
            'Keola Networks',
            'ACTIVE',
            (SELECT id FROM edushift.platform_plans WHERE code = 'ENTERPRISE' LIMIT 1),
            NOW(),
            NOW(),
            false
        );
        RAISE NOTICE '[V74] inserted tenant keola-networks';
    ELSE
        RAISE NOTICE '[V74] tenant keola-networks already exists — skipping';
    END IF;
END $$;

-- 2. Seed a STUDENT user in tecnosur.
--    Lucia Estudiante Demo — single canonical student for E2E.
DO $$
DECLARE
    v_tenant_id     uuid;
    v_existing_id   uuid;
BEGIN
    SELECT id INTO v_tenant_id
    FROM   edushift.tenants
    WHERE  slug = 'tecnosur' AND deleted = false;

    IF v_tenant_id IS NULL THEN
        RAISE EXCEPTION '[V74] tecnosur tenant not found — V39 must run before V74';
    END IF;

    SELECT id INTO v_existing_id
    FROM   edushift.users
    WHERE  email = 'lucia.student@tecnosur.edushift.pe'
      AND  deleted = false
      AND  tenant_id = v_tenant_id;

    IF v_existing_id IS NULL THEN
        INSERT INTO edushift.users (
            id,
            tenant_id,
            public_uuid,
            email,
            password_hash,
            first_name,
            last_name,
            status,
            email_verified,
            mfa_enabled,
            roles,
            created_at,
            updated_at,
            deleted,
            created_by,
            updated_by
        ) VALUES (
            gen_random_uuid(),
            v_tenant_id,
            gen_random_uuid(),
            'lucia.student@tecnosur.edushift.pe',
            'SEED_RESET_REQUIRED_v1_EduShift2026!',
            'Lucia',
            'Estudiante Demo',
            'ACTIVE',
            true,
            false,
            ARRAY['STUDENT']::varchar[],
            NOW(),
            NOW(),
            false,
            NULL,
            NULL
        );
        RAISE NOTICE '[V74] inserted STUDENT user lucia.student@tecnosur.edushift.pe';
    ELSE
        RAISE NOTICE '[V74] STUDENT user lucia.student@tecnosur.edushift.pe already exists — skipping';
    END IF;
END $$;