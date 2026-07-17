-- =============================================================================
-- V76 - DEBT-FK-BUGS-3 closure (1/2): re-target students.user_id FK from
-- users(id) to users(public_uuid).
--
-- Contexto:
--   V10 (Sprint 3) cre\u00f3 `fk_students_user` apuntando a `users(id)` (UUIDv7
--   PK interno). V18 hizo lo mismo para `teachers`. En ese momento la
--   convenci\u00f3n del codebase a\u00fan no estaba unificada.
--
--   V29 (Sprint 5B), V30 (Sprint 6), V48 (Sprint 13) y V75 (Sprint 2.15)
--   migraron progresivamente los FKs a `users.public_uuid` (UUIDv4
--   externo, lo que lleva el JWT `sub` claim). Estudiantes y profesores
--   quedaron como las \u00fanicas dos excepciones \u2014 documentado en
--   `docs/product/tech-debt.md` (DEBT-FK-BUGS-3).
--
-- Por qu\u00e9 urge (no solo consistencia):
--   `student.user_id` se consume en dos paths que necesitan
--   `users.public_uuid` (no `users.id`):
--
--   1. AttendanceServiceImpl L236+L846 publica
--      `NotificationEvent.recipients(Recipient(student.getUserId(), null))`
--      con `student.getUserId()` (= `users.id` UUIDv7). El
--      NotificationService persiste una fila `notifications`
--      con `recipient_user_id` apuntando a `users.public_uuid`
--      (FK post-V48). El listener traga la excepci\u00f3n FK violation.
--      \u2192 Notificaciones de asistencia a estudiantes fallan
--      silenciosamente en producci\u00f3n desde que V48 se aplic\u00f3.
--
--   2. EvaluationServiceImpl L287 \u2014 mismo pat\u00e1n para
--      `GRADE_PUBLISHED` a estudiantes matriculados.
--
--   3. AnnouncementAudienceResolver (Sprint 2.15 / V75 follow-up)
--      tiene que hacer JOIN expl\u00edcito `students.user_id \u2192 users.id \u2192
--      users.public_uuid` para los casos GRADE/SECTION/COURSE.
--      Con esta migraci\u00f3n el JOIN extra desaparece.
--
-- Decision (forward-only, mismo patron que V29/V30/V48/V75):
--   No se modifica V10 (regla Flyway: V<n> inmutable). Se dropea la
--   FK vieja, se migran los datos, se recrea apuntando a
--   `users(public_uuid)`.
--
-- Orden de operaciones dentro de la migration:
--   1. DROP CONSTRAINT fk_students_user (el FK viejo a users.id).
--      Sin la FK activa, el UPDATE del paso 2 puede escribir
--      public_uuid en user_id sin violar constraints (los valores
--      public_uuid NO existen en users.id, sólo en users.public_uuid).
--   2. UPDATE traduce cada user_id existente (UUIDv7 PK) al
--      public_uuid correspondiente. Filas con user_id apuntando a
--      un user borrado lógicamente (deleted=true) las seteamos a
--      NULL (el JOIN no encuentra match) — preserva la semántica del
--      ON DELETE SET NULL del FK viejo.
--   3. ADD CONSTRAINT fk_students_user apuntando a users(public_uuid).
--   4. Refresca el COMMENT de la columna.

-- 1. Drop FK legacy.
ALTER TABLE edushift.students
    DROP CONSTRAINT fk_students_user;

-- 2. Traducir user_id (UUIDv7 PK) → public_uuid (UUIDv4).
-- Filas huérfanas (user_id apunta a un user hard-deleted, así que
-- el JOIN no encuentra match) quedan con NULL — equivalente al
-- ON DELETE SET NULL del FK viejo.
UPDATE edushift.students s
SET    user_id = u.public_uuid
FROM   edushift.users u
WHERE  u.id = s.user_id
  AND  s.user_id IS NOT NULL;

-- Filas cuyo user_id no matchea ningún user → NULL.
UPDATE edushift.students
SET    user_id = NULL
WHERE  user_id IS NOT NULL
  AND  NOT EXISTS (
        SELECT 1 FROM edushift.users u WHERE u.id = edushift.students.user_id
      );

-- 3. Re-target a users(public_uuid).
ALTER TABLE edushift.students
    ADD CONSTRAINT fk_students_user
    FOREIGN KEY (user_id)
    REFERENCES edushift.users (public_uuid)
    ON DELETE SET NULL;

-- 4. COMMENT documenta la nueva convención.
COMMENT ON COLUMN edushift.students.user_id IS
    'public_uuid del usuario asociado al estudiante (cuando el estudiante también tiene cuenta). FK -> users.public_uuid (no users.id). Nullable: estudiantes sin cuenta quedan con user_id NULL.';
-- =============================================================================
-- FIN V76
-- =============================================================================