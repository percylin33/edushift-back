-- =============================================================================
-- V63__add_mfa_to_users.sql
--
-- Sprint 17 / BE-17.2 — Multi-factor authentication (TOTP) columns.
--
-- Schema decisions:
-- * `mfa_secret_hash` stores the BCrypt-hashed TOTP secret. Even though TOTP
--   secrets are base32 strings, we never store them in plaintext: a leaked
--   DB dump would otherwise let an attacker forge codes for the lifetime
--   of the secret (typically forever, until the user re-enrolls).
--   The hash is asymmetric, so a fresh code is generated on every
--   enrollment; only the user knows the plaintext during the QR scan.
-- * `mfa_recovery_codes_hash` (jsonb array) holds BCrypt hashes of the
--   recovery codes generated at enrollment time. Storing as JSONB (not
--   a join table) keeps the recovery flow self-contained on the User
--   aggregate without a second table. We accept a max of 10 codes.
-- * `mfa_enrolled_at` records when MFA was first enabled — used by
--   audit logs and to detect re-enrollments.
-- * `mfa_enabled` already exists (V51 sprint 14 / DEBT-AUTH-7) and is
--   preserved as-is. We only ADD the missing payload columns.
-- =============================================================================

ALTER TABLE edushift.users
    ADD COLUMN IF NOT EXISTS mfa_secret_hash           varchar(255),
    ADD COLUMN IF NOT EXISTS mfa_recovery_codes_hash  jsonb,
    ADD COLUMN IF NOT EXISTS mfa_enrolled_at           timestamptz;