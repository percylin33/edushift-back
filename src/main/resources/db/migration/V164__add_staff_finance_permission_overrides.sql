-- Keep persisted per-user STAFF overrides aligned with the finance permissions
-- exposed by StaffAccessCatalog. These codes were accepted by the API but
-- rejected by the V162 database CHECK constraint.
ALTER TABLE edushift.staff_user_permission_overrides
    DROP CONSTRAINT ck_staff_user_permissions_authority;

ALTER TABLE edushift.staff_user_permission_overrides
    ADD CONSTRAINT ck_staff_user_permissions_authority CHECK (authority IN (
        'STUDENTS:READ','STUDENTS:CREATE','STUDENTS:UPDATE','USERS:PROVISION_STUDENT',
        'GUARDIANS:READ','GUARDIANS:MANAGE','USERS:PROVISION_GUARDIAN',
        'ENROLLMENTS:READ','ENROLLMENTS:MANAGE',
        'ATTENDANCE:READ','ATTENDANCE:REGISTER','ATTENDANCE:CORRECT',
        'PAYMENTS:READ','PAYMENTS:RECEIVE','PAYMENTS:RECONCILE','PAYMENTS:ISSUE',
        'PAYMENTS:REFUND','PAYMENTS:EXPORT','PAYMENT_CONCEPTS:MANAGE',
        'ACADEMIC:READ','REPORTS:READ','LMS_ADMISSION_MANAGE',
        'USERS_PROVISION_SECTION','LMS_TASK_READ','LMS_MATERIAL_READ',
        'LMS_QUIZ_READ','LMS_DISCLOSURE_READ','LMS_DISCLOSURE_MANAGE',
        'LMS_DISCLOSURE_SUBMIT','LMS_PAYMENT_ADMIN'
    ));

COMMENT ON CONSTRAINT ck_staff_user_permissions_authority
    ON edushift.staff_user_permission_overrides IS
    'Allowed STAFF permission keys; kept aligned with StaffAccessCatalog.';
