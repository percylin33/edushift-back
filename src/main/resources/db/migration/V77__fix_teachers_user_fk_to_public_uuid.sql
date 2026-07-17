-- =============================================================================
-- V77 - DEBT-FK-BUGS-3 closure (2/2): re-target teachers.user_id FK from
-- users(id) to users(public_uuid).
--
-- Contexto:
--   Idéntico al documentado en V76 (la contraparte para `teachers`).
--   V18 (Sprint 4 / BE-4.6) creó `fk_teachers_user` apuntando a
--   `users(id)`. V29/V30/V48/V75 migraron progresivamente todos los
--   demás FKs a `users.public_uuid` — esta migración cierra el
--   capítulo.
--
-- Por qué urge:
--   `teacher.user_id` se consume en paths que necesitan public_uuid:
--
--   1. TeacherMapper.toResponseDetail() (L118-119) lee teacher.user_id
--      y hace `userRepository.findById(teacher.getUserId())`. Después
--      de V77 la columna almacena public_uuid, así que el lookup debe
--      cambiar a `findByPublicUuid` — eso lo cubre la migración
--      complementaria en `TeacherMapper.java` (no en SQL).
--
--   2. QuizRubricServiceImpl L187+L266 hace el dance
--      `publicUuid → user.id → teacher.user_id` en dos pasos por la
--      convención legacy de V18. Con V77 + V76 ese dance se
--      simplifica a un solo paso (work futuro en el commit paralelo
--      de QuizRubricServiceImpl).
--
--   3. AnnouncementAudienceResolver caso COURSE une teachers.user_id
--      a users.id para llegar a public_uuid. Con V77 el JOIN
--      desaparece (al igual que en students con V76).
--
-- Decision (forward-only, mismo patron que V76):
--   No se modifica V18 (regla Flyway: V<n> inmutable). Se dropea la
--   FK, se traducen los datos, se recrea apuntando a
--   `users(public_uuid)`.
--
-- Orden de operaciones:
--   1. DROP CONSTRAINT fk_teachers_user (el FK viejo a users.id).
--      Sin la FK activa, el UPDATE del paso 2 puede escribir
--      public_uuid sin violar constraints (los public_uuid NO
--      existen en users.id, sólo en users.public_uuid).
--   2. UPDATE traduce cada user_id (UUIDv7 PK) al public_uuid.
--   3. UPDATE setea user_id = NULL para filas huérfanas (apuntan a
--      un user_id que ya no existe en users) — preserva la
--      semántica del ON DELETE SET NULL del FK viejo.
--   4. ADD CONSTRAINT fk_teachers_user apuntando a users(public_uuid).
--   5. COMMENT de la columna.
--
-- Idempotencia: mismo análisis que V76.
-- =============================================================================

-- 1. Drop FK legacy.
ALTER TABLE edushift.teachers
    DROP CONSTRAINT fk_teachers_user;

-- 2. Traducir user_id (UUIDv7 PK) → public_uuid (UUIDv4).
UPDATE edushift.teachers t
SET    user_id = u.public_uuid
FROM   edushift.users u
WHERE  u.id = t.user_id
  AND  t.user_id IS NOT NULL;

-- 3. Filas cuyo user_id no matchea ningún user → NULL.
UPDATE edushift.teachers
SET    user_id = NULL
WHERE  user_id IS NOT NULL
  AND  NOT EXISTS (
        SELECT 1 FROM edushift.users u WHERE u.id = edushift.teachers.user_id
      );

-- 4. Re-target a users(public_uuid).
ALTER TABLE edushift.teachers
    ADD CONSTRAINT fk_teachers_user
    FOREIGN KEY (user_id)
    REFERENCES edushift.users (public_uuid)
    ON DELETE SET NULL;

-- 5. COMMENT documenta la nueva convención.
COMMENT ON COLUMN edushift.teachers.user_id IS
    'public_uuid del usuario asociado al docente (cuando el docente también tiene cuenta). FK -> users.public_uuid (no users.id). Nullable: docentes sin cuenta quedan con user_id NULL.';

-- =============================================================================
-- FIN V77
-- =============================================================================