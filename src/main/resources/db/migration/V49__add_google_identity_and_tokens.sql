-- =============================================================================
-- V49__add_google_identity_and_tokens.sql
-- Google OAuth identity linking + encrypted refresh-token storage.
--
-- Background (Sprint 11 hardening):
--   The auth module currently supports email + password only. The roadmap
--   (`docs/modules/auth.md` §14.3) lists Google Login as the next external
--   identity provider. This migration adds the schema pieces required by
--   POST /api/v1/auth/google:
--
--     1. `users.google_subject` — Google's stable `sub` claim, links a
--        user account to one Google identity. Nullable so the column can
--        be back-filled on existing rows without touching them.
--
--     2. `user_google_tokens`  — encrypted Google OAuth refresh tokens +
--        the scopes the user consented to. Persisted so EduShift can call
--        Gmail API on behalf of the user without re-prompting consent on
--        every send. One active row per user (partial unique index).
--
-- Decisions:
--   * Per-tenant uniqueness on `google_subject` (mirror of email uniqueness):
--     the same Google identity can theoretically exist in two tenants in
--     different worlds (private consumer Google + Workspace school); the
--     conflict is resolved at the API layer by `X-Tenant-Slug` + the link
--     table. The partial unique index ensures the link can't be inserted
--     twice for the same tenant.
--   * `encrypted_refresh_token bytea` is opaque ciphertext — we never want
--     a DBA reading tokens in plain. The application-side encryption key
--     lives in `app.integrations.gmail.encryption-key` (env-driven).
--   * `scopes varchar[]` records the scopes the user consented to so a
--     later code-review can warn when an endpoint needs a scope the user
--     never granted.
--   * Soft-delete + audit cols inherited from BaseEntity (see V3 / V6).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. users.google_subject
-- -----------------------------------------------------------------------------
ALTER TABLE edushift.users
    ADD COLUMN google_subject varchar(128);

COMMENT ON COLUMN edushift.users.google_subject IS
    'Google stable `sub` claim from the verified id_token. Nullable so legacy email/password accounts are untouched. Uniqueness is enforced PER TENANT on non-deleted rows.';

-- Uniqueness of google_subject per tenant (case-sensitive: Google subjects
-- are lowercase but opaque to us, so we don't normalize).
CREATE UNIQUE INDEX uk_users_tenant_google_subject
    ON edushift.users (tenant_id, google_subject)
    WHERE google_subject IS NOT NULL AND deleted = false;

-- -----------------------------------------------------------------------------
-- 2. user_google_tokens
-- -----------------------------------------------------------------------------
CREATE TABLE edushift.user_google_tokens (
    id                       uuid          PRIMARY KEY,

    -- Tenant scoping (managed by Hibernate @TenantId via TenantAwareEntity)
    tenant_id                uuid          NOT NULL,

    -- Audit (BaseEntity / AuditableEntity)
    created_at               timestamptz   NOT NULL,
    updated_at               timestamptz   NOT NULL,
    created_by               uuid,
    updated_by               uuid,
    deleted                  boolean       NOT NULL DEFAULT false,
    deleted_at               timestamptz,

    -- Ownership
    user_id                  uuid          NOT NULL,

    -- Encrypted refresh token (AES-GCM ciphertext; never plain text in DB)
    encrypted_refresh_token  bytea         NOT NULL,

    -- Scopes the user granted to EduShift at consent time
    scopes                   varchar(64)[] NOT NULL DEFAULT '{}',

    -- Lifecycle
    expires_at               timestamptz,
    revoked_at               timestamptz,
    revoked_reason           varchar(50),

    -- Foreign keys
    CONSTRAINT fk_user_google_tokens_tenant
        FOREIGN KEY (tenant_id) REFERENCES edushift.tenants(id),

    CONSTRAINT fk_user_google_tokens_user
        FOREIGN KEY (user_id) REFERENCES edushift.users(id)
        ON DELETE CASCADE,

    -- revoked_reason is a small enum-like string
    CONSTRAINT chk_user_google_tokens_revoked_reason CHECK (
        revoked_reason IS NULL
        OR revoked_reason IN ('USER_REVOKED', 'SCOPE_CHANGED', 'COMPROMISED', 'ADMIN_REVOKE', 'PROVIDER_DISABLED')
    ),

    -- revoked_at / revoked_reason consistency
    CONSTRAINT chk_user_google_tokens_revoked_consistent CHECK (
        (revoked_at IS NULL AND revoked_reason IS NULL)
        OR (revoked_at IS NOT NULL)
    ),

    -- scopes array must be non-null (Postgres allows NULL arrays which we
    -- want to forbid; use empty array for "no scopes granted")
    CONSTRAINT chk_user_google_tokens_scopes_non_null CHECK (
        scopes IS NOT NULL
    ),

    -- encrypted_refresh_token must be non-empty (AES-GCM output is at least
    -- 12-byte IV + 16-byte tag + 1 byte payload = 29 bytes)
    CONSTRAINT chk_user_google_tokens_encrypted_min_length CHECK (
        octet_length(encrypted_refresh_token) >= 29
    ),

    -- soft-delete consistency (mirrors users / refresh_tokens)
    CONSTRAINT chk_user_google_tokens_deleted_at_consistent CHECK (
        (deleted = false AND deleted_at IS NULL)
        OR (deleted = true  AND deleted_at IS NOT NULL)
    )
);

COMMENT ON TABLE  edushift.user_google_tokens                          IS 'Encrypted Google OAuth refresh tokens + consented scopes. One active row per user.';
COMMENT ON COLUMN edushift.user_google_tokens.encrypted_refresh_token  IS 'AES-GCM ciphertext of the Google refresh token. NEVER log, NEVER expose via API.';
COMMENT ON COLUMN edushift.user_google_tokens.scopes                   IS 'Scopes the user consented to at grant time. Used to gate Gmail API calls without re-prompting.';
COMMENT ON COLUMN edushift.user_google_tokens.expires_at               IS 'When the Google access token would expire (Google refresh tokens typically do not, but we track the access-token expiry to know when to refresh).';
COMMENT ON COLUMN edushift.user_google_tokens.revoked_reason           IS 'USER_REVOKED|SCOPE_CHANGED|COMPROMISED|ADMIN_REVOKE|PROVIDER_DISABLED';

-- -----------------------------------------------------------------------------
-- Indexes
-- -----------------------------------------------------------------------------

-- One ACTIVE token row per user (the partial unique index mirrors the
-- single-active-chain invariant of refresh_tokens).
CREATE UNIQUE INDEX uk_user_google_tokens_user_active
    ON edushift.user_google_tokens (user_id)
    WHERE deleted = false AND revoked_at IS NULL;

-- Tenant-scoped browsing (admin dashboards, "revoke Google connection").
CREATE INDEX idx_user_google_tokens_tenant
    ON edushift.user_google_tokens (tenant_id)
    WHERE deleted = false;

-- Audit / "when was this token issued" reports.
CREATE INDEX idx_user_google_tokens_user_created
    ON edushift.user_google_tokens (user_id, created_at DESC)
    WHERE deleted = false;

-- -----------------------------------------------------------------------------
-- Triggers (updated_at maintenance — shared helper from V1)
-- -----------------------------------------------------------------------------
CREATE TRIGGER set_updated_at_user_google_tokens
    BEFORE UPDATE ON edushift.user_google_tokens
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();