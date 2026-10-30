-- Announcements: optional guardian fan-out, and a scoped teacher permission
-- that tenants can grant or revoke without opening school-wide publishing.

ALTER TABLE edushift.announcements
    ADD COLUMN include_guardians boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN edushift.announcements.include_guardians IS
    'When true, GRADE/SECTION/COURSE resolution also includes guardians who have a portal user.';

ALTER TABLE edushift.role_permission_overrides
    DROP CONSTRAINT ck_role_permission_overrides_authority;

ALTER TABLE edushift.role_permission_overrides
    ADD CONSTRAINT ck_role_permission_overrides_authority CHECK (authority IN (
        'LMS_TASK_READ','LMS_TASK_CREATE','LMS_TASK_GRADE','LMS_TASK_SUBMIT',
        'LMS_MATERIAL_READ','LMS_MATERIAL_WRITE','LMS_MATERIAL_DELETE',
        'LMS_QUIZ_READ','LMS_QUIZ_CREATE','LMS_QUIZ_GRADE','LMS_QUIZ_SUBMIT',
        'LMS_AI_GENERATE','LMS_AI_USAGE','LMS_PAYMENT_ADMIN',
        'LMS_ANNOUNCEMENTS_CREATE','LMS_ANNOUNCEMENTS_CREATE_OWN',
        'LMS_NOTIFICATIONS_MANAGE'
    ));
