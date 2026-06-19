-- =====================================================================
-- V40: add AuditableEntity columns to lms_submission_revisions
-- =====================================================================
-- Motivation:
--   SubmissionRevision was extended from BaseEntity to TenantAwareEntity
--   (which transitively extends AuditableEntity) so Hibernate's
--   @TenantId discriminator auto-populates tenant_id on insert.
--   AuditableEntity requires `created_by` and `updated_by` columns.
--   The original table only had `created_by_user_id` (a soft FK to
--   users.public_uuid, used as the revision's "snapshot author").
--
-- Decision:
--   * Add `created_by` and `updated_by` as nullable uuid (the entity
--     columns are nullable; see AuditableEntity Javadoc).
--   * Keep `created_by_user_id` as the domain-meaningful "snapshot
--     author" column. We do NOT collapse them: their semantics differ
--     (created_by = JPA auditing from AuditorAware, created_by_user_id
--     = explicit snapshot author passed by the service).
--   * Append-only semantics are preserved: trigger still updates
--     updated_at on every UPDATE (only Hibernate refreshes).
-- =====================================================================

ALTER TABLE edushift.lms_submission_revisions
    ADD COLUMN IF NOT EXISTS created_by uuid,
    ADD COLUMN IF NOT EXISTS updated_by uuid;

COMMENT ON COLUMN edushift.lms_submission_revisions.created_by
    IS 'JPA AuditableEntity @CreatedBy (populated from AuditorAware). Nullable for system-generated rows.';
COMMENT ON COLUMN edushift.lms_submission_revisions.updated_by
    IS 'JPA AuditableEntity @LastModifiedBy (populated from AuditorAware). Nullable for system-generated rows.';
