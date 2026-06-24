-- =============================================================================
-- V48 - Re-target audit-trail FKs from users(id) to users(public_uuid)
-- (DEBT-FK-BUGS-2 sweep).
--
-- Contexto:
--   Las migraciones V43 (Sprint 9 / BE-9.1 notifications), V44 (Sprint 9 /
--   BE-9.2 announcements) y V45 (Sprint 9 / BE-9.3 reports) crearon FKs
--   a {@code edushift.users(id)} (el PK interno UUIDv7 que NUNCA se
--   expone via API).
--
--   Sin embargo el codigo de produccion (igual que V30 para attendance y
--   V29 para grade_records) inyecta en estas columnas el
--   {@code public_uuid} del usuario (UUIDv4) desde
--   {@code CurrentUserProvider.currentUserId()}.
--
--   El error tipico, visible al correr los IT cross-tenant de
--   announcement / notification / report, es:
--
--     ERROR: insert or update on table "X" violates foreign key
--     constraint "fk_X_user"
--     Detail: Key (Y_user_id)=(<publicUuid>) is not present in table "users".
--
--   Este es EL MISMO patron que V29 (GradeRecord) y V30 (Attendance)
--   ya arreglaron. Esta migracion cierra el capitulo para los 5 FKs
--   restantes de los modulos de notificaciones + announcements + reports.
--
-- Decision (forward-only):
--   No se modifican V43/V44/V45 (regla de Flyway del proyecto: V<n> es
--   inmutable una vez aplicada). Se dropea la FK vieja y se recrea
--   apuntando a users(public_uuid), siguiendo el patron de V29.
--
-- FKs arreglados (5 total):
--   V43 / notifications.recipient_user_id         (CASCADE)
--   V43 / notification_preferences.user_id        (CASCADE)
--   V44 / announcements.author_user_id            (CASCADE)
--   V44 / announcement_recipients.user_id         (CASCADE)
--   V45 / report_jobs.requested_by_user_id        (SET NULL)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. notifications.recipient_user_id  (V43)
-- -----------------------------------------------------------------------------
ALTER TABLE edushift.notifications
    DROP CONSTRAINT fk_notifications_recipient;
ALTER TABLE edushift.notifications
    ADD CONSTRAINT fk_notifications_recipient
    FOREIGN KEY (recipient_user_id)
    REFERENCES edushift.users (public_uuid)
    ON DELETE CASCADE;

COMMENT ON COLUMN edushift.notifications.recipient_user_id IS
    'public_uuid del usuario destinatario. Inyectado desde CurrentUserProvider.currentUserId() (que retorna el publicUuid del JWT). FK -> users.public_uuid (no users.id).';

-- -----------------------------------------------------------------------------
-- 2. notification_preferences.user_id  (V43)
-- -----------------------------------------------------------------------------
ALTER TABLE edushift.notification_preferences
    DROP CONSTRAINT fk_notification_preferences_user;
ALTER TABLE edushift.notification_preferences
    ADD CONSTRAINT fk_notification_preferences_user
    FOREIGN KEY (user_id)
    REFERENCES edushift.users (public_uuid)
    ON DELETE CASCADE;

COMMENT ON COLUMN edushift.notification_preferences.user_id IS
    'public_uuid del usuario. FK -> users.public_uuid (no users.id).';

-- -----------------------------------------------------------------------------
-- 3. announcements.author_user_id  (V44)
-- -----------------------------------------------------------------------------
ALTER TABLE edushift.announcements
    DROP CONSTRAINT fk_announcements_author;
ALTER TABLE edushift.announcements
    ADD CONSTRAINT fk_announcements_author
    FOREIGN KEY (author_user_id)
    REFERENCES edushift.users (public_uuid)
    ON DELETE CASCADE;

COMMENT ON COLUMN edushift.announcements.author_user_id IS
    'public_uuid del autor del announcement. FK -> users.public_uuid (no users.id).';

-- -----------------------------------------------------------------------------
-- 4. announcement_recipients.user_id  (V44)
-- -----------------------------------------------------------------------------
ALTER TABLE edushift.announcement_recipients
    DROP CONSTRAINT fk_announcement_recipients_user;
ALTER TABLE edushift.announcement_recipients
    ADD CONSTRAINT fk_announcement_recipients_user
    FOREIGN KEY (user_id)
    REFERENCES edushift.users (public_uuid)
    ON DELETE CASCADE;

COMMENT ON COLUMN edushift.announcement_recipients.user_id IS
    'public_uuid del destinatario. FK -> users.public_uuid (no users.id).';

-- -----------------------------------------------------------------------------
-- 5. report_jobs.requested_by_user_id  (V45)
-- -----------------------------------------------------------------------------
ALTER TABLE edushift.report_jobs
    DROP CONSTRAINT fk_report_jobs_user;
ALTER TABLE edushift.report_jobs
    ADD CONSTRAINT fk_report_jobs_user
    FOREIGN KEY (requested_by_user_id)
    REFERENCES edushift.users (public_uuid)
    ON DELETE SET NULL;

COMMENT ON COLUMN edushift.report_jobs.requested_by_user_id IS
    'public_uuid del usuario que pidio el report. FK -> users.public_uuid (no users.id). ON DELETE SET NULL: si el user se borra, el report se queda como huerfano con requested_by=null.';
