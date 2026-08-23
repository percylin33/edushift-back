-- =============================================================================
-- V96__seed_tecnosur_login_bg.sql
-- Adds an example loginBgUrl to the tecnosur tenant branding (dev seed).
-- Forward-only: does NOT edit V39/V93. Idempotent merge via jsonb ||.
-- =============================================================================

DO $$
BEGIN
    IF current_database() IN ('edushift_prod', 'edushift_production') THEN
        RAISE NOTICE 'V96 seed skipped on production database %', current_database();
        RETURN;
    END IF;

    UPDATE edushift.tenants
    SET
        branding = COALESCE(branding, '{}'::jsonb) || jsonb_build_object(
            'loginBgUrl',
            'https://images.unsplash.com/photo-1562774053-701939374585?auto=format&fit=crop&w=1920&q=80'
        ),
        updated_at = NOW()
    WHERE lower(slug) = 'tecnosur'
      AND deleted = false
      AND (
          branding IS NULL
          OR branding->>'loginBgUrl' IS NULL
          OR branding->>'loginBgUrl' = ''
      );

    IF FOUND THEN
        RAISE NOTICE 'V96: tecnosur loginBgUrl applied';
    ELSE
        RAISE NOTICE 'V96: tecnosur missing or already has loginBgUrl — no-op';
    END IF;
END;
$$;
