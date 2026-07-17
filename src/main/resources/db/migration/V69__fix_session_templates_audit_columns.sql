-- =============================================================================
-- V69__fix_session_templates_audit_columns.sql
--
-- Sprint 18 / BE-18.3 — backfill de columnas audit en session_templates.
--
-- Problema:
-- V66 (create_session_templates.sql) omitió las columnas heredadas de
-- AuditableEntity (created_by, updated_by). Hibernate falla al validar el
-- schema al arrancar:
--
--   SchemaManagementException: Schema-validation: missing column [created_by]
--   in table [edushift.session_templates]
--
-- Fix:
-- Añadir las 2 columnas que la entidad SessionTemplate (que extiende
-- TenantAwareEntity → AuditableEntity → BaseEntity) espera:
--   * created_by  — UUID del usuario que creó el template (nullable: los
--                   templates del sistema seedeados por migración no tienen
--                   un usuario asociado)
--   * updated_by  — UUID del usuario que actualizó el row
--
-- Idempotente: usa IF NOT EXISTS para poder re-ejecutarse en cualquier
-- entorno sin error.
-- =============================================================================

ALTER TABLE edushift.session_templates
    ADD COLUMN IF NOT EXISTS created_by uuid;

ALTER TABLE edushift.session_templates
    ADD COLUMN IF NOT EXISTS updated_by uuid;

COMMENT ON COLUMN edushift.session_templates.created_by IS
    'AuditableEntity — UUID del usuario que creó el template. Nullable
     para templates del sistema seedeados por la migración V66.';
COMMENT ON COLUMN edushift.session_templates.updated_by IS
    'AuditableEntity — UUID del usuario que actualizó el row.';
