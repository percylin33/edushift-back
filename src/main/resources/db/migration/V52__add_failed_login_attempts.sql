-- =============================================================================
-- V52__add_failed_login_attempts.sql
--
-- Sprint 14 (MVP Closure) / DEBT-AUTH-7: failed-login tracking.
--
-- Adds a dedicated counter table for failed login attempts.
-- Earlier debt attempted Redis-only (login_attempts:{email}), but Redis
-- counts were lost on flush and could be bypassed. This table is the
-- source-of-truth at the SQL boundary. Redis is still used as a fast
-- cache for the hot login path, but the table is the durable record
-- that survives restarts.
--
-- The counter resets on:
--   • successful authentication (recordSuccess),
--   • admin manual reset (reset),
--   • row older than 24 hours (no auto-cleanup; rows linger and a
--     nightly job can prune — a separate debt DEBT-AUTH-7-CLEANUP).
--
-- Schema notes:
--   • Composite PK (email, tenant_slug, window_started_at) so the same
--     email in different tenants is independent (defense-in-depth).
--   • status enum reflects WHY the row exists: ACTIVE counter,
--     LOCKED after threshold, EXPIRED after 24h.
-- =============================================================================

CREATE TABLE edushift.failed_login_attempts (
    id                uuid          PRIMARY KEY,
    tenant_id         uuid          NOT NULL,
    tenant_slug       varchar(64)   NOT NULL,
    email             varchar(254)  NOT NULL,
    attempt_count     int           NOT NULL DEFAULT 1,
    first_attempt_at  timestamptz   NOT NULL,
    last_attempt_at   timestamptz   NOT NULL,
    locked_until      timestamptz,
    status            varchar(16)   NOT NULL DEFAULT 'ACTIVE',
    deleted           boolean       NOT NULL DEFAULT false,
    deleted_at        timestamptz,

    -- Audit (BaseEntity / AuditableEntity)
    created_at        timestamptz   NOT NULL,
    updated_at        timestamptz   NOT NULL,
    created_by        uuid,
    updated_by        uuid,

    CONSTRAINT fk_failed_login_attempts_tenant
        FOREIGN KEY (tenant_id) REFERENCES edushift.tenants(id)
        ON DELETE CASCADE,

    -- One row per (tenant, email, attempt-window-start). We allow multiple
    -- windows because the first window may expire and the next attempt starts
    -- a new window (recorded as a new row).
    CONSTRAINT uk_failed_login_attempts_window
        UNIQUE (tenant_id, email, first_attempt_at),

    -- Counts must be sane.
    CONSTRAINT chk_failed_login_attempts_count_positive
        CHECK (attempt_count >= 1),

    -- Status enum-like.
    CONSTRAINT chk_failed_login_attempts_status
        CHECK (status IN ('ACTIVE', 'LOCKED', 'EXPIRED', 'CLEARED'))
);

COMMENT ON TABLE edushift.failed_login_attempts IS
    'DEBT-AUTH-7: durable counter for failed login attempts per (tenant, email). '
    'Used in conjunction with Redis cache for the hot login path.';

COMMENT ON COLUMN edushift.failed_login_attempts.locked_until IS
    'When the user was locked out. NULL means not currently locked.';
COMMENT ON COLUMN edushift.failed_login_attempts.status IS
    'ACTIVE: counter running. LOCKED: threshold reached. '
    'EXPIRED: window ended without lock. CLEARED: success reset the counter.';

-- Hot path: lookup latest window by (tenant, email).
CREATE INDEX idx_failed_login_attempts_lookup
    ON edushift.failed_login_attempts (tenant_id, email, last_attempt_at DESC)
    WHERE deleted = false;

-- Tenant-wide dashboard of recent lockouts.
CREATE INDEX idx_failed_login_attempts_tenant_locked
    ON edushift.failed_login_attempts (tenant_id, locked_until DESC)
    WHERE deleted = false AND locked_until IS NOT NULL;

-- updated_at maintenance
CREATE TRIGGER set_updated_at_failed_login_attempts
    BEFORE UPDATE ON edushift.failed_login_attempts
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();
