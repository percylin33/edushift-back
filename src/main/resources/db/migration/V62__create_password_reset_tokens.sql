-- =============================================================================
-- V62__create_password_reset_tokens.sql
--
-- Sprint 17 / BE-17.1 — Password reset tokens for forgot/reset password flow.
--
-- Schema decisions:
-- * `jti` (UUID) is the JWT ID of the reset token. Stored separately from the
--   user/tenant PKs so that:
--     - Two reset requests for the same user coexist (only the latest is valid
--       in practice, but having rows lets us audit who requested a reset when).
--     - Rotation strategy: when a new reset is requested, we mark all previous
--       pending tokens as `superseded_at = now()` to invalidate them.
-- * `used_at` enforces idempotency: a token can be consumed at most once.
-- * `expires_at` is the absolute expiration timestamp; default TTL 1h from issue.
-- * Tenant scope: every token is scoped to a tenant via tenant_id (NOT NULL +
--   FK to tenants). This enables the tenant-isolation IT (`ResetPasswordTenantIsolationIT`).
-- * No cascade: deleting a user does NOT delete reset tokens (kept for audit
--   up to N days after expiration; cleaner delete is a future cron).
-- =============================================================================

CREATE TABLE IF NOT EXISTS edushift.password_reset_tokens (
    id              uuid         PRIMARY KEY,
    public_uuid     uuid         NOT NULL,
    jti             uuid         NOT NULL,
    user_id         uuid         NOT NULL,
    tenant_id       uuid         NOT NULL,
    expires_at      timestamptz  NOT NULL,
    used_at         timestamptz,
    superseded_at   timestamptz,
    request_ip      varchar(64),
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uk_password_reset_tokens_jti UNIQUE (jti),
    CONSTRAINT uk_password_reset_tokens_public_uuid UNIQUE (public_uuid),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES edushift.users (id),
    CONSTRAINT fk_password_reset_tokens_tenant FOREIGN KEY (tenant_id) REFERENCES edushift.tenants (id)
);

-- Hot path index: when validating a reset, we look up by jti first.
-- The unique constraint already creates an index on jti, but we make it
-- explicit + add a secondary index on (user_id, created_at DESC) for the
-- "list latest reset for user" admin view (future).
CREATE INDEX IF NOT EXISTS ix_password_reset_tokens_user_created_at
    ON edushift.password_reset_tokens (user_id, created_at DESC);

-- Audit columns are added/managed by JpaAuditing. We still stamp them
-- explicitly here for the case when rows are inserted via raw SQL (the
-- NotificationTemplate seed path, future admin tooling).