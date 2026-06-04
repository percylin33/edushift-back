-- =============================================================================
-- V7__extend_tenants_table.sql
-- Round-2 columns for the tenants catalog (Sprint 2).
--
-- Sprint 1 introduced the table with the bare minimum needed for login
-- (id, public_uuid, name, slug, status, settings, custom_domain). Sprint 2
-- turns it into a real SaaS resource: every tenant carries a billing plan,
-- visual branding, feature flags, and soft caps on enrollment so the
-- product team can iterate on plan-based limits without touching the
-- schema again.
--
-- Design notes:
--   * `plan` defaults to TRIAL with a 14-day window. The window is
--     persisted explicitly in `trial_ends_at` (not derived) so future
--     plan changes (extension, manual upgrade) can edit it without
--     ambiguity.
--   * `branding` and `feature_flags` are jsonb on purpose — both are
--     long-tail key/value bags that we expect to mutate independently
--     from the rest of the schema (UI tweaks, A/B flags). Keeping them
--     unstructured here avoids a schema migration every time the front
--     wants to surface a new branding knob (e.g. accent color, hero
--     image). The backend exposes a typed DTO (`BrandingDto`) so the
--     contract with the front stays explicit.
--   * `max_students` / `max_teachers` are nullable: NULL means
--     "unlimited within the plan limits". Hard plan-level caps live in
--     the backend; per-tenant overrides land here.
--   * Backfill: only the seed `demo` tenant exists at this point. We
--     preserve its TRIAL default and seed a 14-day trial window so the
--     dev environment doesn't immediately look "expired".
--
-- All columns are added with non-null DEFAULTs to make the ALTER
-- non-blocking on existing rows; PostgreSQL avoids the table rewrite
-- when the default is a compile-time literal (which all of ours are).
-- =============================================================================

ALTER TABLE edushift.tenants
    ADD COLUMN plan          varchar(30)  NOT NULL DEFAULT 'TRIAL',
    ADD COLUMN trial_ends_at timestamptz,
    ADD COLUMN branding      jsonb        NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN feature_flags jsonb        NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN max_students  int,
    ADD COLUMN max_teachers  int;

ALTER TABLE edushift.tenants
    ADD CONSTRAINT chk_tenants_plan CHECK (
        plan IN ('TRIAL', 'BASIC', 'PRO', 'ENTERPRISE')
    );

ALTER TABLE edushift.tenants
    ADD CONSTRAINT chk_tenants_max_students CHECK (
        max_students IS NULL OR max_students > 0
    );

ALTER TABLE edushift.tenants
    ADD CONSTRAINT chk_tenants_max_teachers CHECK (
        max_teachers IS NULL OR max_teachers > 0
    );

COMMENT ON COLUMN edushift.tenants.plan          IS 'Billing tier (TenantPlan): TRIAL|BASIC|PRO|ENTERPRISE';
COMMENT ON COLUMN edushift.tenants.trial_ends_at IS 'When the trial window closes (only meaningful while plan = TRIAL)';
COMMENT ON COLUMN edushift.tenants.branding      IS 'Free-form jsonb: primaryColor, logoUrl, faviconUrl, loginBgUrl, ...';
COMMENT ON COLUMN edushift.tenants.feature_flags IS 'Free-form jsonb: per-tenant feature toggles {flag: bool}';
COMMENT ON COLUMN edushift.tenants.max_students  IS 'Optional hard cap on student count (NULL = use plan default)';
COMMENT ON COLUMN edushift.tenants.max_teachers  IS 'Optional hard cap on teacher count (NULL = use plan default)';

-- ----------------------------------------------------------------------------
-- Backfill: seed the demo tenant with a real trial window so dev env
-- doesn't show "expired trial" surprises after the migration runs.
-- ----------------------------------------------------------------------------
UPDATE edushift.tenants
SET    trial_ends_at = NOW() + INTERVAL '14 days'
WHERE  slug          = 'demo'
  AND  trial_ends_at IS NULL;
