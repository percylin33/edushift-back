-- =============================================================================
-- V70__fix_teacher_attendance_records_audit_columns.sql
--
-- Sprint 18 / BE-18.4 — backfill de columnas audit en teacher_attendance_records.
--
-- Problema:
-- V67 (create_teacher_attendance_records.sql) omitió las columnas heredadas de
-- AuditableEntity (created_by, updated_by). Hibernate falla al validar el
-- schema al arrancar:
--
--   SchemaManagementException: Schema-validation: missing column [created_by]
--   in table [edushift.teacher_attendance_records]
--
-- Fix:
-- Añadir las 2 columnas que la entidad TeacherAttendanceRecord (que extiende
-- TenantAwareEntity → AuditableEntity → BaseEntity) espera:
--   * created_by  — UUID del usuario que creó el row (nullable: registros
--                   generados por jobs del sistema pueden no tener usuario)
--   * updated_by  — UUID del usuario que actualizó el row
--
-- Idempotente: usa IF NOT EXISTS para poder re-ejecutarse en cualquier
-- entorno sin error.
-- =============================================================================

ALTER TABLE edushift.teacher_attendance_records
    ADD COLUMN IF NOT EXISTS created_by uuid;

ALTER TABLE edushift.teacher_attendance_records
    ADD COLUMN IF NOT EXISTS updated_by uuid;

COMMENT ON COLUMN edushift.teacher_attendance_records.created_by IS
    'AuditableEntity — UUID del usuario que creó el row. Nullable
     para registros generados por jobs del sistema.';
COMMENT ON COLUMN edushift.teacher_attendance_records.updated_by IS
    'AuditableEntity — UUID del usuario que actualizó el row.';
