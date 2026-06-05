-- =============================================================================
-- V9__create_user_invitations_table.sql
-- Pending invitations for new users (per-tenant). Owned by the `users` module.
--
-- Key design decisions:
--   * `id`            internal UUIDv7 (time-ordered; never exposed via API)
--   * `public_uuid`   external UUIDv4 surfaced to TENANT_ADMIN tooling
--                     (admins use it to cancel pending invites)
--   * `tenant_id`     mandatory; managed by Hibernate discriminator
--                     multi-tenancy (@TenantId on TenantAwareEntity)
--   * `token`         GLOBALLY UNIQUE: the accept endpoint is public and
--                     resolves the tenant via the token alone. Per-tenant
--                     uniqueness would not be enough — the public endpoint
--                     has no tenant context to disambiguate collisions.
--   * Lifecycle is implicit (no `status` column): the (acceptedAt,
--     cancelledAt, expiresAt) trio is the source of truth and the service
--     layer derives the {PENDING|ACCEPTED|CANCELLED|EXPIRED} state on
--     read. This keeps the DB shape simple and the truth in one place.
--   * `roles`         varchar[] mirroring users.roles — the invitation
--                     captures the role set the new account will inherit
--                     when it accepts.
-- =============================================================================

CREATE TABLE edushift.user_invitations (
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

    -- Recipient profile (snapshotted at invite time so the public preflight
    -- endpoint can render "Welcome, {firstName}" without resolving anything else)
    email           varchar(254)  NOT NULL,
    first_name      varchar(100)  NOT NULL,
    last_name       varchar(100)  NOT NULL,

    -- Roles to grant on accept (matches users.roles convention)
    roles           varchar(40)[] NOT NULL DEFAULT '{}'::varchar(40)[],

    -- Token: opaque high-entropy string (>= 32 base64-url chars) generated
    -- by the service. Globally unique because the accept flow is public
    -- and tenant-less.
    token           varchar(64)   NOT NULL,

    -- Lifecycle timestamps
    expires_at      timestamptz   NOT NULL,
    accepted_at     timestamptz,
    cancelled_at    timestamptz,

    CONSTRAINT uk_user_invitations_public_uuid UNIQUE (public_uuid),

    -- Token uniqueness is global (not per-tenant) to support the public
    -- accept flow. Partial: soft-deleted rows do not occupy the namespace.
    CONSTRAINT chk_user_invitations_deleted_at_consistent CHECK (
        (deleted = false AND deleted_at IS NULL)
        OR (deleted = true AND deleted_at IS NOT NULL)
    ),

    -- Mutually-exclusive terminal flags: an invitation can be accepted OR
    -- cancelled, but not both. We don't constrain "expired" because that
    -- one is computed from `expires_at`, not stored as a flag.
    CONSTRAINT chk_user_invitations_terminal CHECK (
        accepted_at IS NULL OR cancelled_at IS NULL
    )
);

CREATE UNIQUE INDEX uk_user_invitations_token_active
    ON edushift.user_invitations (token)
    WHERE deleted = false;

-- A given email should have at most one PENDING invitation per tenant at
-- a time. Re-inviting after acceptance/cancellation/expiry is allowed.
CREATE UNIQUE INDEX uk_user_invitations_tenant_email_pending
    ON edushift.user_invitations (tenant_id, lower(email))
    WHERE deleted = false
      AND accepted_at IS NULL
      AND cancelled_at IS NULL;

CREATE INDEX idx_user_invitations_tenant_pending
    ON edushift.user_invitations (tenant_id, expires_at)
    WHERE deleted = false
      AND accepted_at IS NULL
      AND cancelled_at IS NULL;

COMMENT ON TABLE  edushift.user_invitations               IS 'Pending invitations to join a tenant (per-tenant). Owned by users module.';
COMMENT ON COLUMN edushift.user_invitations.token         IS 'Globally unique opaque secret consumed by the public accept endpoint';
COMMENT ON COLUMN edushift.user_invitations.expires_at    IS 'Hard deadline; service layer rejects accept attempts after this';
COMMENT ON COLUMN edushift.user_invitations.accepted_at   IS 'When the recipient redeemed the token; null until accepted';
COMMENT ON COLUMN edushift.user_invitations.cancelled_at  IS 'When an admin cancelled the invitation; null while pending';

CREATE TRIGGER set_updated_at_user_invitations
    BEFORE UPDATE ON edushift.user_invitations
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();
