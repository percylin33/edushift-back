-- =============================================================================
-- V6__create_refresh_tokens_table.sql
-- Persistent storage for refresh tokens (auth module).
--
-- Design decisions:
--   * Tenant-aware: refresh tokens belong to a user inside a tenant. Hibernate's
--     @TenantId discriminator auto-filters queries; the column is immutable.
--
--   * We store a SHA-256 hex digest (64 chars), NOT the raw token. If the DB
--     leaks, attackers cannot replay tokens. SHA-256 is deliberate (vs BCrypt):
--     refresh tokens are random 256-bit secrets, not user-chosen passwords —
--     fast hashing is fine and fast lookup is required.
--
--   * Rotation lineage via parent_token_id (self FK). On every /refresh we
--     mint a new token, mark the old as revoked, and link new.parent = old.
--     This forms a chain that supports theft detection: if a revoked token
--     is presented again, the whole chain is compromised and we revoke it.
--
--   * Soft delete is inherited from BaseEntity. Hard delete is reserved for
--     cleanup jobs that purge tokens past their expiration window.
--
-- Sprint 2 will add: a scheduled job to sweep expires_at < now() AND
-- revoked_at IS NOT NULL OLDER THAN N days; rate limiting on /refresh.
-- =============================================================================

CREATE TABLE edushift.refresh_tokens (
    id               uuid          PRIMARY KEY,
    tenant_id        uuid          NOT NULL,

    -- Audit (BaseEntity / AuditableEntity)
    created_at       timestamptz   NOT NULL,
    updated_at       timestamptz   NOT NULL,
    created_by       uuid,
    updated_by       uuid,
    deleted          boolean       NOT NULL DEFAULT false,
    deleted_at       timestamptz,

    -- Token data
    token_hash       varchar(64)   NOT NULL,
    user_id          uuid          NOT NULL,
    expires_at       timestamptz   NOT NULL,

    -- Revocation
    revoked_at       timestamptz,
    revoked_reason   varchar(50),

    -- Rotation lineage (self FK; nullable for the FIRST token of a chain)
    parent_token_id  uuid,

    -- Foreign keys
    CONSTRAINT fk_refresh_tokens_tenant
        FOREIGN KEY (tenant_id) REFERENCES edushift.tenants(id),

    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES edushift.users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_refresh_tokens_parent
        FOREIGN KEY (parent_token_id) REFERENCES edushift.refresh_tokens(id)
        ON DELETE SET NULL,

    -- token_hash format: 64 lowercase hex chars (SHA-256 hex digest)
    CONSTRAINT chk_refresh_tokens_token_hash_format CHECK (
        token_hash ~ '^[0-9a-f]{64}$'
    ),

    -- revoked_at and revoked_reason must be consistent
    CONSTRAINT chk_refresh_tokens_revoked_consistent CHECK (
        (revoked_at IS NULL AND revoked_reason IS NULL)
        OR (revoked_at IS NOT NULL)
    ),

    -- revoked_reason is a small enum-like string
    CONSTRAINT chk_refresh_tokens_revoked_reason CHECK (
        revoked_reason IS NULL
        OR revoked_reason IN ('ROTATED', 'LOGOUT', 'COMPROMISED', 'EXPIRED', 'ADMIN_REVOKE')
    ),

    -- soft-delete consistency (mirrors users / tenants)
    CONSTRAINT chk_refresh_tokens_deleted_at_consistent CHECK (
        (deleted = false AND deleted_at IS NULL)
        OR (deleted = true  AND deleted_at IS NOT NULL)
    )
);

COMMENT ON TABLE  edushift.refresh_tokens                IS 'Issued refresh tokens with rotation lineage and revocation tracking.';
COMMENT ON COLUMN edushift.refresh_tokens.token_hash     IS 'SHA-256 hex digest of the raw token (64 lowercase hex chars).';
COMMENT ON COLUMN edushift.refresh_tokens.parent_token_id IS 'Self FK to the previous token in the rotation chain. NULL for the first.';
COMMENT ON COLUMN edushift.refresh_tokens.revoked_reason IS 'ROTATED|LOGOUT|COMPROMISED|EXPIRED|ADMIN_REVOKE';

-- ----------------------------------------------------------------------------
-- Indexes
-- ----------------------------------------------------------------------------

-- Hot path: /refresh looks up by token_hash. Globally unique on non-deleted rows.
CREATE UNIQUE INDEX uk_refresh_tokens_token_hash
    ON edushift.refresh_tokens (token_hash)
    WHERE deleted = false;

-- Active tokens by user (admin views, "log out everywhere", per-user limits).
CREATE INDEX idx_refresh_tokens_user_active
    ON edushift.refresh_tokens (user_id, tenant_id)
    WHERE deleted = false AND revoked_at IS NULL;

-- Sweep expired tokens (background job, Sprint 2).
CREATE INDEX idx_refresh_tokens_expires_at
    ON edushift.refresh_tokens (expires_at)
    WHERE deleted = false AND revoked_at IS NULL;

-- Tenant-scoped browsing (admin dashboards).
CREATE INDEX idx_refresh_tokens_tenant
    ON edushift.refresh_tokens (tenant_id)
    WHERE deleted = false;

-- ----------------------------------------------------------------------------
-- Triggers (updated_at maintenance)
-- ----------------------------------------------------------------------------

CREATE TRIGGER set_updated_at_refresh_tokens
    BEFORE UPDATE ON edushift.refresh_tokens
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();
