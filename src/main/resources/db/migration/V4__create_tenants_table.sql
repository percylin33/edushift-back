-- =============================================================================
-- V4__create_tenants_table.sql
-- Root tenant table (one row = one school / institution).
--
-- This table is intentionally GLOBAL (NOT tenant-aware): it IS the catalog of
-- tenants. Almost every other table in the system carries a `tenant_id` that
-- foreign-keys back to this table.
--
-- Design notes:
--   * `id`           internal UUIDv7 (time-ordered; never exposed via API)
--   * `public_uuid`  external UUIDv4 surfaced to clients
--   * `slug`         human-friendly subdomain identifier (lowercase, kebab-case)
--                    enforced unique on non-deleted rows so that re-creating a
--                    tenant after a soft-delete does not get blocked
--   * `status`       lifecycle gate; only ACTIVE tenants can authenticate
--   * `settings`     free-form JSON for institutional preferences (locale,
--                    timezone, academic conventions). Extended by Sprint 2.
--
-- Sprint 2 will ALTER this table to add: plan, trial_ends_at, branding_json,
-- feature_flags_json, max_students, max_teachers.
-- =============================================================================

CREATE TABLE edushift.tenants (
    id              uuid          PRIMARY KEY,
    public_uuid     uuid          NOT NULL,

    -- Audit (inherited from AuditableEntity / BaseEntity)
    created_at      timestamptz   NOT NULL,
    updated_at      timestamptz   NOT NULL,
    created_by      uuid,
    updated_by      uuid,
    deleted         boolean       NOT NULL DEFAULT false,
    deleted_at      timestamptz,

    -- Identity
    name            varchar(200)  NOT NULL,
    slug            varchar(80)   NOT NULL,
    custom_domain   varchar(200),

    -- Lifecycle
    status          varchar(30)   NOT NULL DEFAULT 'PENDING',

    -- Free-form institutional settings (locale, timezone, conventions, ...)
    settings        jsonb         NOT NULL DEFAULT '{}'::jsonb,

    CONSTRAINT uk_tenants_public_uuid UNIQUE (public_uuid),

    CONSTRAINT chk_tenants_status CHECK (
        status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'INACTIVE')
    ),

    -- Slug grammar: lowercase alphanumerics + dashes; cannot start/end with dash
    CONSTRAINT chk_tenants_slug_format CHECK (
        slug ~ '^[a-z0-9]([a-z0-9-]{0,78}[a-z0-9])?$'
        AND char_length(slug) BETWEEN 2 AND 80
    ),

    -- Custom domain: optional, must look like a hostname
    CONSTRAINT chk_tenants_custom_domain_format CHECK (
        custom_domain IS NULL
        OR custom_domain ~ '^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$'
    ),

    CONSTRAINT chk_tenants_deleted_at_consistent CHECK (
        (deleted = false AND deleted_at IS NULL)
        OR (deleted = true  AND deleted_at IS NOT NULL)
    )
);

COMMENT ON TABLE  edushift.tenants               IS 'Root catalog of tenants (schools / institutions). Globally scoped.';
COMMENT ON COLUMN edushift.tenants.id            IS 'Internal UUIDv7 PK; never exposed via API';
COMMENT ON COLUMN edushift.tenants.public_uuid   IS 'External, stable identifier exposed via REST (UUIDv4)';
COMMENT ON COLUMN edushift.tenants.slug          IS 'URL-friendly identifier used as subdomain (e.g. school.edushift.pe)';
COMMENT ON COLUMN edushift.tenants.custom_domain IS 'Optional vanity domain for white-label deployments';
COMMENT ON COLUMN edushift.tenants.status        IS 'Lifecycle (TenantStatus): PENDING|ACTIVE|SUSPENDED|INACTIVE';
COMMENT ON COLUMN edushift.tenants.settings      IS 'Free-form institutional settings (jsonb)';

-- ----------------------------------------------------------------------------
-- Indexes (partial: only non-deleted rows participate)
-- ----------------------------------------------------------------------------

-- Slug uniqueness is GLOBAL (not per-tenant) and case-insensitive.
-- Enforced only on non-deleted rows so a tenant recreated after soft-delete
-- can reclaim the same slug.
CREATE UNIQUE INDEX uk_tenants_slug_lower
    ON edushift.tenants (lower(slug))
    WHERE deleted = false;

-- Same logic for custom_domain when present.
CREATE UNIQUE INDEX uk_tenants_custom_domain_lower
    ON edushift.tenants (lower(custom_domain))
    WHERE deleted = false AND custom_domain IS NOT NULL;

-- Hot path: status filter (admin dashboards: active tenants, etc.)
CREATE INDEX idx_tenants_status
    ON edushift.tenants (status)
    WHERE deleted = false;

-- Public UUID lookup
CREATE INDEX idx_tenants_public_uuid
    ON edushift.tenants (public_uuid)
    WHERE deleted = false;

-- ----------------------------------------------------------------------------
-- Triggers
-- ----------------------------------------------------------------------------
CREATE TRIGGER set_updated_at_tenants
    BEFORE UPDATE ON edushift.tenants
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();
