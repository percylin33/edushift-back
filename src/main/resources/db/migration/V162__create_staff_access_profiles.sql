-- =============================================================================
-- V162__create_staff_access_profiles.sql
-- Per-user staff profile assignments and explicit permission exceptions.
-- Profiles are built-in bundles; every row remains tenant-scoped.
-- =============================================================================

CREATE TABLE edushift.staff_user_profile_assignments (
    id              uuid         PRIMARY KEY,
    tenant_id       uuid         NOT NULL,
    user_id         uuid         NOT NULL,
    profile_code    varchar(40)  NOT NULL,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,
    deleted         boolean      NOT NULL DEFAULT false,
    deleted_at      timestamptz,

    CONSTRAINT fk_staff_profile_assignments_tenant FOREIGN KEY (tenant_id)
        REFERENCES edushift.tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_staff_profile_assignments_user FOREIGN KEY (user_id)
        REFERENCES edushift.users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_staff_profile_assignments_code CHECK (profile_code IN (
        'SECRETARIA','ADMISIONES','ASISTENCIA','FINANZAS','COORDINACION',
        'SOPORTE','INFORMACION_ANUAL','LEGACY_OPERATIONS'
    )),
    CONSTRAINT ck_staff_profile_assignments_deleted_at CHECK (
        (deleted = false AND deleted_at IS NULL)
        OR (deleted = true AND deleted_at IS NOT NULL)
    )
);

COMMENT ON TABLE edushift.staff_user_profile_assignments IS
    'Built-in, composable operational permission profiles assigned to individual STAFF users.';
COMMENT ON COLUMN edushift.staff_user_profile_assignments.tenant_id IS
    'School tenant that owns this profile assignment; never accepted from the request body.';
COMMENT ON COLUMN edushift.staff_user_profile_assignments.user_id IS
    'Internal user id; the service verifies that the user belongs to this tenant and has STAFF.';
COMMENT ON COLUMN edushift.staff_user_profile_assignments.profile_code IS
    'Built-in profile code resolved by StaffAccessCatalog; profiles combine by union.';

CREATE INDEX ix_staff_profile_assignments_tenant
    ON edushift.staff_user_profile_assignments (tenant_id)
    WHERE deleted = false;
CREATE INDEX ix_staff_profile_assignments_tenant_user
    ON edushift.staff_user_profile_assignments (tenant_id, user_id)
    WHERE deleted = false;
CREATE UNIQUE INDEX uk_staff_profile_assignments_active
    ON edushift.staff_user_profile_assignments (tenant_id, user_id, profile_code)
    WHERE deleted = false;
CREATE TRIGGER set_updated_at_staff_profile_assignments
    BEFORE UPDATE ON edushift.staff_user_profile_assignments
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

CREATE TABLE edushift.staff_user_permission_overrides (
    id              uuid         PRIMARY KEY,
    tenant_id       uuid         NOT NULL,
    user_id         uuid         NOT NULL,
    authority       varchar(64)  NOT NULL,
    granted         boolean      NOT NULL,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,
    deleted         boolean      NOT NULL DEFAULT false,
    deleted_at      timestamptz,

    CONSTRAINT fk_staff_user_permissions_tenant FOREIGN KEY (tenant_id)
        REFERENCES edushift.tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_staff_user_permissions_user FOREIGN KEY (user_id)
        REFERENCES edushift.users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_staff_user_permissions_authority CHECK (authority IN (
        'STUDENTS:READ','STUDENTS:CREATE','STUDENTS:UPDATE','USERS:PROVISION_STUDENT',
        'GUARDIANS:READ','GUARDIANS:MANAGE','USERS:PROVISION_GUARDIAN',
        'ENROLLMENTS:READ','ENROLLMENTS:MANAGE',
        'ATTENDANCE:READ','ATTENDANCE:REGISTER','ATTENDANCE:CORRECT',
        'PAYMENTS:READ','PAYMENTS:RECEIVE','PAYMENTS:RECONCILE','PAYMENTS:REFUND','PAYMENTS:EXPORT',
        'ACADEMIC:READ','REPORTS:READ','LMS_ADMISSION_MANAGE',
        'USERS_PROVISION_SECTION','LMS_TASK_READ','LMS_MATERIAL_READ',
        'LMS_QUIZ_READ','LMS_DISCLOSURE_READ','LMS_DISCLOSURE_MANAGE',
        'LMS_DISCLOSURE_SUBMIT','LMS_PAYMENT_ADMIN'
    )),
    CONSTRAINT ck_staff_user_permissions_deleted_at CHECK (
        (deleted = false AND deleted_at IS NULL)
        OR (deleted = true AND deleted_at IS NOT NULL)
    )
);

COMMENT ON TABLE edushift.staff_user_permission_overrides IS
    'Per-user grants and revocations layered over a STAFF user’s assigned profiles.';
COMMENT ON COLUMN edushift.staff_user_permission_overrides.tenant_id IS
    'School tenant that owns this permission override; never accepted from the request body.';
COMMENT ON COLUMN edushift.staff_user_permission_overrides.authority IS
    'Stable permission key from StaffAccessCatalog; one action per key.';
COMMENT ON COLUMN edushift.staff_user_permission_overrides.granted IS
    'True adds this permission; false revokes it even when an assigned profile grants it.';

CREATE INDEX ix_staff_user_permissions_tenant
    ON edushift.staff_user_permission_overrides (tenant_id)
    WHERE deleted = false;
CREATE INDEX ix_staff_user_permissions_tenant_user
    ON edushift.staff_user_permission_overrides (tenant_id, user_id)
    WHERE deleted = false;
CREATE UNIQUE INDEX uk_staff_user_permissions_active
    ON edushift.staff_user_permission_overrides (tenant_id, user_id, authority)
    WHERE deleted = false;
CREATE TRIGGER set_updated_at_staff_user_permissions
    BEFORE UPDATE ON edushift.staff_user_permission_overrides
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

-- Preserve the current access of existing STAFF accounts during rollout.
-- Administrators can remove this legacy bundle and assign narrower profiles
-- from each user's detail page. New STAFF users start with read-only defaults.
INSERT INTO edushift.staff_user_profile_assignments (
    id, tenant_id, user_id, profile_code, created_at, updated_at, deleted
)
SELECT gen_random_uuid(), u.tenant_id, u.id, 'LEGACY_OPERATIONS', now(), now(), false
FROM edushift.users u
WHERE u.deleted = false
  AND 'STAFF' = ANY(u.roles)
  AND NOT ('SUPER_ADMIN' = ANY(u.roles));
