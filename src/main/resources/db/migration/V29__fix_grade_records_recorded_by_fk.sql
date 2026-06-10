-- =====================================================================
-- V29 - Sprint 5B / BE-5B.3 (hotfix QA-5B.1)
--
-- Fixes the foreign-key target of `grade_records.recorded_by_user_id`.
--
-- Background
-- ----------
-- V27 created `fk_grade_records_recorded_by` pointing at
-- `edushift.users.id` (the *internal* UUIDv7 PK that is intentionally
-- never surfaced via the API).
--
-- However the runtime contract — documented inline in V27 itself
-- ("Inyectado desde CurrentUserProvider.currentUserId()") and in
-- {@link com.edushift.modules.auth.security.JwtAuthenticatedPrincipal}
-- ("The id carried here is the user's publicUuid, not the internal
-- User.id") — feeds the column with the user's `public_uuid`.
--
-- The mismatch surfaced as a `DataIntegrityViolationException` during
-- QA-5B.1 (PASO-12) the first time we tried to record a grade
-- end-to-end:
--
--     ERROR: insert or update on table "grade_records" violates foreign
--     key constraint "fk_grade_records_recorded_by"
--     Detail: Key (recorded_by_user_id)=(<publicUuid>) is not present
--     in table "users".
--
-- Fix
-- ---
-- Re-target the FK to `edushift.users(public_uuid)`. This keeps
-- referential integrity (the `public_uuid` column is also UNIQUE — see
-- `uk_users_public_uuid` in V3) and aligns with the rest of the audit
-- chain: `created_by` / `updated_by` (BaseEntity) already store the
-- author's `public_uuid` and are deliberately FK-less because the same
-- semantic mismatch would have appeared there too.
--
-- This migration is idempotent against the V27 baseline: it drops the
-- old FK and re-creates it against the correct target column.
-- =====================================================================

ALTER TABLE edushift.grade_records
    DROP CONSTRAINT fk_grade_records_recorded_by;

ALTER TABLE edushift.grade_records
    ADD CONSTRAINT fk_grade_records_recorded_by
    FOREIGN KEY (recorded_by_user_id)
    REFERENCES edushift.users (public_uuid)
    ON DELETE RESTRICT;

COMMENT ON COLUMN edushift.grade_records.recorded_by_user_id IS
    'public_uuid del usuario autor del último write. Inyectado desde CurrentUserProvider.currentUserId() (que retorna el publicUuid del JWT). FK -> users.public_uuid (no users.id).';
