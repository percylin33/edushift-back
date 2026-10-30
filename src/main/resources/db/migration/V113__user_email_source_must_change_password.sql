-- V113: email_source + must_change_password on users (CRED-7 mass provision)
-- email_source: REAL = deliver mail; SYNTHETIC = skip transactional email until user replaces it.
-- must_change_password: force password change on first login after temp-password provision.

ALTER TABLE edushift.users
	ADD COLUMN IF NOT EXISTS email_source VARCHAR(20) NOT NULL DEFAULT 'REAL',
	ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE edushift.users
	DROP CONSTRAINT IF EXISTS chk_users_email_source;

ALTER TABLE edushift.users
	ADD CONSTRAINT chk_users_email_source
	CHECK (email_source IN ('REAL', 'SYNTHETIC'));

COMMENT ON COLUMN edushift.users.email_source IS
	'REAL = user-provided/verified mailbox; SYNTHETIC = generated for mass provision (mailer must skip).';
COMMENT ON COLUMN edushift.users.must_change_password IS
	'When true, FE/BE block app use until the user sets a new password.';
