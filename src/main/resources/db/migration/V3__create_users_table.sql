-- =============================================================================
-- V3__create_users_table.sql
-- Application users (per-tenant). Owned by the `auth` module.
--
-- Key design decisions:
--   * `id`            internal UUIDv7 (time-ordered; never exposed via API)
--   * `public_uuid`   external UUIDv4 surfaced to clients
--   * `tenant_id`     mandatory; managed by Hibernate discriminator
--                     multi-tenancy (@TenantId on TenantAwareEntity)
--   * email uniqueness is enforced PER TENANT and only over non-deleted rows
--     so a soft-deleted account does not block re-registration
--   * soft delete is the global default (BaseEntity); `deleted_at` captures
--     the forensic timestamp
-- =============================================================================

CREATE TABLE edushift.users (
    id              uuid          PRIMARY KEY,
    tenant_id       uuid          NOT NULL,
    public_uuid     uuid          NOT NULL,

    -- Audit (inherited from AuditableEntity / BaseEntity)
    created_at      timestamptz   NOT NULL,
    updated_at      timestamptz   NOT NULL,
    created_by      uuid,
    updated_by      uuid,
    deleted         boolean       NOT NULL DEFAULT false,
    deleted_at      timestamptz,

    -- Profile
    first_name      varchar(100)  NOT NULL,
    last_name       varchar(100)  NOT NULL,
    email           varchar(254)  NOT NULL,
    password_hash   varchar(255)  NOT NULL,
    phone           varchar(32),
    avatar_url      varchar(512),

    -- Lifecycle / security
    status          varchar(30)   NOT NULL DEFAULT 'PENDING_VERIFICATION',
    email_verified  boolean       NOT NULL DEFAULT false,
    mfa_enabled     boolean       NOT NULL DEFAULT false,
    last_login_at   timestamptz,

    CONSTRAINT uk_users_public_uuid UNIQUE (public_uuid),

    CONSTRAINT chk_users_status CHECK (
        status IN ('PENDING_VERIFICATION', 'ACTIVE', 'INACTIVE', 'SUSPENDED', 'LOCKED')
    ),

    CONSTRAINT chk_users_email_format CHECK (
        email = lower(email) AND email LIKE '%_@_%.__%'
    ),

    CONSTRAINT chk_users_deleted_at_consistent CHECK (
        (deleted = false AND deleted_at IS NULL)
        OR (deleted = true  AND deleted_at IS NOT NULL)
    )
);

COMMENT ON TABLE  edushift.users               IS 'Application users (per-tenant). Owned by auth module.';
COMMENT ON COLUMN edushift.users.id            IS 'Internal UUIDv7 primary key; never exposed via API';
COMMENT ON COLUMN edushift.users.public_uuid   IS 'External, stable identifier exposed via REST (UUIDv4)';
COMMENT ON COLUMN edushift.users.tenant_id     IS 'Tenant scope; managed by Hibernate discriminator multi-tenancy';
COMMENT ON COLUMN edushift.users.email         IS 'Lowercased; uniqueness enforced per tenant on non-deleted rows';
COMMENT ON COLUMN edushift.users.password_hash IS 'BCrypt/Argon2 digest; never expose in API responses or logs';
COMMENT ON COLUMN edushift.users.status        IS 'Lifecycle (UserStatus): PENDING_VERIFICATION|ACTIVE|INACTIVE|SUSPENDED|LOCKED';
COMMENT ON COLUMN edushift.users.mfa_enabled   IS 'True when the user has a second-factor enrolled';
COMMENT ON COLUMN edushift.users.deleted_at    IS 'Soft-delete timestamp; populated by @SQLDelete on the entity';

-- ----------------------------------------------------------------------------
-- Indexes (partial: only non-deleted rows participate, hot path stays small)
-- ----------------------------------------------------------------------------

-- Uniqueness of email per tenant (case-insensitive, ignoring soft-deleted rows).
CREATE UNIQUE INDEX uk_users_tenant_email_active
    ON edushift.users (tenant_id, lower(email))
    WHERE deleted = false;

-- Tenant + status (admin dashboards: active users, pending verification, etc.)
CREATE INDEX idx_users_tenant_status
    ON edushift.users (tenant_id, status)
    WHERE deleted = false;

-- Last login (activity reports / inactivity sweeps)
CREATE INDEX idx_users_last_login
    ON edushift.users (tenant_id, last_login_at DESC)
    WHERE deleted = false AND last_login_at IS NOT NULL;

-- Global lookup by external uuid (e.g. embedded JWT subject -> user resolution)
CREATE INDEX idx_users_public_uuid
    ON edushift.users (public_uuid)
    WHERE deleted = false;

-- ----------------------------------------------------------------------------
-- Triggers
-- ----------------------------------------------------------------------------
CREATE TRIGGER set_updated_at_users
    BEFORE UPDATE ON edushift.users
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();
