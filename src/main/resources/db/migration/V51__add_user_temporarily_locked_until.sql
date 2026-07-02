-- =============================================================================
-- V51__add_user_temporarily_locked_until.sql
--
-- Sprint 14 (MVP Closure) / DEBT-AUTH-7: failed-login lockout temporal.
--
-- When a user accumulates N=5 failed login attempts within M=15 minutes,
-- the account is temporarily locked for 15 minutes. The flag is stored as
-- a TIMESTAMP (NOT a boolean) so the lock can expire automatically without
-- a background sweeper.
--
-- Schema (additive, safe with existing data):
--   users.temporarily_locked_until TIMESTAMP NULL
--     • NULL      = no active lock (default).
--     • Future    = lock active, unlocks automatically at the timestamp.
--     • Past      = lock expired; cleared on next successful read.
--
-- The CHECK constraint prevents accidentally recording "past" locks that
-- would be already invalid by the time the row is written.
-- =============================================================================

ALTER TABLE edushift.users
    ADD COLUMN IF NOT EXISTS temporarily_locked_until TIMESTAMP NULL;

-- Defense-in-depth: never store a lock that is already in the past.
-- If the application logic ever computes a stale timestamp, the DB rejects it.
ALTER TABLE edushift.users
    ADD CONSTRAINT chk_users_locked_until_future
        CHECK (temporarily_locked_until IS NULL OR temporarily_locked_until > NOW() - INTERVAL '1 day');

-- Partial index: only rows with an active lock. Tiny (NULL values are not indexed).
CREATE INDEX IF NOT EXISTS idx_users_temporarily_locked_until
    ON edushift.users (temporarily_locked_until)
    WHERE temporarily_locked_until IS NOT NULL;

COMMENT ON COLUMN edushift.users.temporarily_locked_until IS
    'DEBT-AUTH-7: when a user accumulates too many failed login attempts, '
    'their account is temporarily locked until this timestamp. NULL means '
    'no active lock. The column is set by LoginAttemptService and cleared '
    'implicitly on the next successful authentication read (or via an '
    'admin reset).';
