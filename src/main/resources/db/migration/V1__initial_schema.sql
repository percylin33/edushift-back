-- =============================================================================
-- V1__initial_schema.sql
-- EduShift - esquema inicial (modular monolith)
--
-- Convención Flyway: V{version}__{description}.sql
-- Próximas migraciones: V2__create_users_table.sql, etc.
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS edushift;

COMMENT ON SCHEMA edushift IS 'EduShift application schema - modular monolith bounded contexts';

-- Extensiones en public (requiere privilegio en el rol de migración)
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- UTC: aplicado por HikariCP (connection-init-sql) y Hibernate (jdbc.time_zone)

-- Función reutilizable para columnas updated_at (módulos la referenciarán en V2+)
CREATE OR REPLACE FUNCTION edushift.set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = NOW() AT TIME ZONE 'UTC';
    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION edushift.set_updated_at() IS 'Trigger function: maintains updated_at in UTC';
