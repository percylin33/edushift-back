-- =============================================================================
-- V72__fix_role_permission_overrides_audit_columns.sql
--
-- Backfill de columnas audit en role_permission_overrides.
--
-- Problema:
-- V71 (create_role_permission_overrides.sql) omitió las columnas heredadas de
-- AuditableEntity (created_by, updated_by). Hibernate falla al validar el
-- schema al arrancar:
--
--   SchemaManagementException: Schema-validation: missing column [created_by]
--   in table [edushift.role_permission_overrides]
--
-- Fix:
-- Añadir las 2 columnas que la entidad RolePermissionOverride (que extiende
-- TenantAwareEntity → AuditableEntity → BaseEntity) espera:
--   * created_by  — UUID del usuario que creó el override
--   * updated_by  — UUID del usuario que actualizó el row
--
-- Idempotente: usa IF NOT EXISTS para poder re-ejecutarse en cualquier
-- entorno sin error.
-- =============================================================================

ALTER TABLE edushift.role_permission_overrides
    ADD COLUMN IF NOT EXISTS created_by uuid;

ALTER TABLE edushift.role_permission_overrides
    ADD COLUMN IF NOT EXISTS updated_by uuid;

COMMENT ON COLUMN edushift.role_permission_overrides.created_by IS
    'AuditableEntity — UUID del usuario que creó el override.';
COMMENT ON COLUMN edushift.role_permission_overrides.updated_by IS
    'AuditableEntity — UUID del usuario que actualizó el row.';