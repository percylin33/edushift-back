-- =============================================================================
-- V89__add_user_device_tokens_base_columns.sql
--
-- Cierre-C / B8 -- schema-fixup for V88.
--
-- V88 created `edushift.user_device_tokens` but missed the
-- BaseEntity contract columns (`deleted`, `deleted_at`, `created_by`,
-- `updated_by`) that Hibernate's schema-validation
-- (`hbm2ddl.validate`) requires because UserDeviceToken extends
-- TenantAwareEntity which extends BaseEntity.
--
-- Adding these columns in a separate migration so that existing
-- tenants that already ran V88 don't need a manual repair; the ALTER
-- is a metadata-only change and runs in milliseconds.
--
-- Decisiones:
--   * ADR-Cierre-C.8.b -- `deleted` semantics: row-level tombstone for
--     audit (independent from `active` which means "FCM opt-out /
--     token rotation"). Both transitions keep the row visible to
--     the audit UI; FcmSender only picks `active=true AND deleted=false`.
--   * ADR-Cierre-C.8.b -- chk_user_device_tokens_deleted_at_consistent
--     enforces (deleted=false => deleted_at IS NULL) and the inverse.
--   * ADR-Cierre-C.8.b -- new index `idx_user_device_tokens_active_filtered`
--     covers the FCM dispatch hot path (active=true AND deleted=false).
-- =============================================================================

ALTER TABLE edushift.user_device_tokens
    ADD COLUMN IF NOT EXISTS created_by  uuid;

ALTER TABLE edushift.user_device_tokens
    ADD COLUMN IF NOT EXISTS updated_by  uuid;

ALTER TABLE edushift.user_device_tokens
    ADD COLUMN IF NOT EXISTS deleted     boolean NOT NULL DEFAULT false;

ALTER TABLE edushift.user_device_tokens
    ADD COLUMN IF NOT EXISTS deleted_at  timestamptz;

-- Backfill: any pre-fix row that's currently marked active=false is
-- by definition a user opt-out / token rotation, NOT a hard delete.
-- So `deleted` stays false for those rows. The chk constraint will
-- tolerate them because `deleted=false AND deleted_at IS NULL` is the
-- default new-row state.

-- Constraints: same chk_user_device_tokens_deleted_at_consistent we
-- added to V88 retroactively; safe because the backfill above ensures
-- `deleted=false` and `deleted_at IS NULL` for every existing row.
ALTER TABLE edushift.user_device_tokens
    ADD CONSTRAINT chk_user_device_tokens_deleted_at_consistent CHECK (
        (deleted = false AND deleted_at IS NULL)
     OR (deleted = true  AND deleted_at IS NOT NULL)
    );

-- Index for the FCM dispatch hot path: tenant + active + not-deleted.
CREATE INDEX IF NOT EXISTS idx_user_device_tokens_active_filtered
    ON edushift.user_device_tokens (tenant_id, user_public_uuid, last_seen_at DESC)
    WHERE active = true AND deleted = false;

COMMENT ON COLUMN edushift.user_device_tokens.deleted
    IS 'Sprint cierre-C / B8 -- BaseEntity soft-delete tombstone. Independent from `active` (FCM opt-out / token rotation): `active=false` means user opt-out; `deleted=true` means row removed by the app.';
COMMENT ON COLUMN edushift.user_device_tokens.deleted_at
    IS 'Sprint cierre-C / B8 -- set when the row transitions deleted=true. Preserved for audit.';
COMMENT ON COLUMN edushift.user_device_tokens.created_by
    IS 'Sprint cierre-C / B8 -- BaseEntity audit column (actor who first registered the token).';
COMMENT ON COLUMN edushift.user_device_tokens.updated_by
    IS 'Sprint cierre-C / B8 -- BaseEntity audit column (last actor who touched the row).';