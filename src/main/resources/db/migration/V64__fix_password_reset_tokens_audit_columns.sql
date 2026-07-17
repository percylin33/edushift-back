-- =============================================================================
-- V63__fix_password_reset_tokens_audit_columns.sql
--
-- Sprint 17 / BE-17.1 — backfill de columnas audit + soft-delete en
-- password_reset_tokens.
--
-- Problema:
-- V62 (create_password_reset_tokens.sql) omitió las columnas heredadas de
-- BaseEntity (deleted) y AuditableEntity (created_by, updated_by). Hibernate
-- falla al validar el schema al arrancar:
--
--   SchemaManagementException: Schema-validation: missing column [deleted] in
--   table [edushift.password_reset_tokens]
--
-- Fix:
-- Añadir las 3 columnas que la entidad PasswordResetToken (que extiende
-- TenantAwareEntity → AuditableEntity → BaseEntity) espera:
--   * deleted     — soft-delete flag, NOT NULL DEFAULT false
--   * created_by  — UUID del usuario que pidió el reset (nullable: tokens
--                   generados por jobs del sistema pueden ser null)
--   * updated_by  — UUID del usuario que actualizó el row
--
-- Idempotente: usa IF NOT EXISTS para poder re-ejecutarse en cualquier
-- entorno sin error.
-- =============================================================================

ALTER TABLE edushift.password_reset_tokens
    ADD COLUMN IF NOT EXISTS deleted    boolean      NOT NULL DEFAULT false;

ALTER TABLE edushift.password_reset_tokens
    ADD COLUMN IF NOT EXISTS created_by uuid;

ALTER TABLE edushift.password_reset_tokens
    ADD COLUMN IF NOT EXISTS updated_by uuid;

COMMENT ON COLUMN edushift.password_reset_tokens.deleted IS
    'Soft delete flag — BaseEntity. @SQLDelete en PasswordResetToken
     setea deleted=true al eliminar.';
COMMENT ON COLUMN edushift.password_reset_tokens.created_by IS
    'AuditableEntity — UUID del usuario que pidió el reset. Nullable
     para tokens generados por jobs del sistema.';
COMMENT ON COLUMN edushift.password_reset_tokens.updated_by IS
    'AuditableEntity — UUID del usuario que actualizó el row.';