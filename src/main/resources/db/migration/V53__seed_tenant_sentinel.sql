-- =============================================================================
-- V53__seed_tenant_sentinel.sql
--
-- Sprint 15 (SUPER_ADMIN module) / BE-15.1 — Tenant sentinel para SUPER_ADMIN.
--
-- Define un tenant "raíz" del sistema (UUID conocido y fijo) que sirve como
-- anclaje cross-tenant para el rol SUPER_ADMIN. Esto evita tener que modificar
-- `TenantAwareEntity` para hacer `tenant_id` nullable.
--
-- El sentinel:
--   * uuid fijo:           00000000-0000-0000-0000-000000000001 (todo ceros + 1)
--   * slug reservado:      edushift-system  (cumple chk_tenants_slug_format:
--                                          lowercase alphanumerics + dashes)
--   * status:              ACTIVE (nunca se suspende; si esto pasa, romper la plataforma)
--   * plan:                ENTERPRISE (se setea en V54_1 después de insertar platform_plans)
--   * tenant_id nullability: este tenant EXISTE en la tabla `tenants` con un id real,
--                           por lo tanto cumple con la constraint tenant_id NOT NULL
--                           de TenantAwareEntity.
--
-- `TenantIdResolver.isRoot(tenantId)` reconoce este UUID como root: Hibernate
-- NO añade el filtro `WHERE tenant_id = ?` cuando el contexto actual es este tenant.
-- `JwtAuthenticationFilter` y `TenantInterceptor` detectan `isSuperAdmin=true` para
-- bypassear la validación de tenant.
--
-- Este tenant nunca se expone en listados user-facing de TENANT_ADMIN. Solo
-- SUPER_ADMIN puede verlo, y aparece con un card distintivo "Sistema EduShift".
--
-- Idempotencia: usa ON CONFLICT DO NOTHING para evitar error si se re-ejecuta.
-- Pre-requisito: V4__create_tenants_table.sql + V7__extend_tenants_table.sql.
-- =============================================================================

INSERT INTO edushift.tenants (
    id,
    public_uuid,
    name,
    slug,
    status,
    plan,
    max_students,
    max_teachers,
    settings,
    branding,
    trial_ends_at,
    created_at,
    updated_at,
    deleted
) VALUES (
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'EduShift System',
    'edushift-system',
    'ACTIVE',
    'ENTERPRISE',
    NULL,
    NULL,
    '{}',
    '{}',
    NULL,
    now(),
    now(),
    false
)
ON CONFLICT (id) DO NOTHING;

COMMENT ON TABLE edushift.tenants IS
    'Tenants. Cada fila representa un colegio o el sentinel edushift-system (Sprint 15). '
    'El sentinel es la raíz cross-tenant para el rol SUPER_ADMIN.';

-- Backfill de public_uuid por seguridad (si public_uuid != id en producción)
UPDATE edushift.tenants
SET public_uuid = id
WHERE id = '00000000-0000-0000-0000-000000000001'
  AND public_uuid IS DISTINCT FROM id;

-- Constraint extra: el slug edushift-system debe ser único en el sistema (defensa)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_tenants_sentinel_slug'
    ) THEN
        ALTER TABLE edushift.tenants
            ADD CONSTRAINT uk_tenants_sentinel_slug
            CHECK (slug != 'edushift-system' OR id = '00000000-0000-0000-0000-000000000001');
    END IF;
END$$;
