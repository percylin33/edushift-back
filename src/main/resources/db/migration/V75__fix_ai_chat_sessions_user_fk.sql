-- =============================================================================
-- V75 - DEBT-FK-BUGS-2 follow-up: re-target ai_chat_sessions.user_id FK from
-- users(id) to users(public_uuid).
--
-- Contexto:
--   V42 (Sprint 8 / BE-8.3) creó `fk_chat_sessions_user` apuntando a
--   `edushift.users(id)` (el PK interno UUIDv7 que NUNCA se expone por API).
--
--   Sin embargo el código de runtime (igual que V29 para attendance,
--   V30 para grade_records, V48 para notifications/announcements/reports)
--   inyecta en esta columna el `public_uuid` del usuario (UUIDv4) desde
--   `CurrentUserProvider.currentUserId()`.
--
--   Ver `JwtAuthenticatedPrincipal.java:10-11`:
--     "The id carried here is the user's publicUuid, not the internal
--      User.id."
--
--   El error típico, visible al correr los e2e de
--   `edushift-front/e2e/tests/ai/` (Phase 2.9) contra el dev DB, es:
--
--     ERROR: insert or update on table "ai_chat_sessions" violates foreign key
--     constraint "fk_chat_sessions_user"
--     Detail: Key (user_id)=(<publicUuid>) is not present in table "users".
--
--   Los specs aceptan 200/201/400/404/409 (no 5xx) precisamente por este
--   bug. Después de este fix, los specs deben poder assert 201.
--
-- Por qué V48 no lo cerró:
--   V48 (`V48__fix_user_fks_to_use_public_uuid.sql`) cerró la deuda
--   DEBT-FK-BUGS-2 con 5 FKs del módulo notifications + announcements +
--   reports. `ai_chat_sessions` no estaba en el alcance original (la
--   tabla es BE-8.3, posterior a las que motivaron DEBT-FK-BUGS-2). Este
--   fix lo agrega.
--
-- Decision (forward-only, mismo patron que V29/V48):
--   No se modifica V42 (regla de Flyway: V<n> es inmutable). Se dropea
--   la FK vieja y se recrea apuntando a `users(public_uuid)`, siguiendo
--   el patrón V29.
--
-- Side concern (NO incluido en este fix):
--   `students.user_id` (V10) y `teachers.user_id` (V18) también apuntan
--   a `users.id` y no se tocan aquí — son legacy pre-convención y
--   cambiarlas tiene blast radius mayor (resolver de announcements +
--   queries de guardian links). Registrado como DEBT-FK-BUGS-3.
-- =============================================================================

ALTER TABLE edushift.ai_chat_sessions
    DROP CONSTRAINT fk_chat_sessions_user;

ALTER TABLE edushift.ai_chat_sessions
    ADD CONSTRAINT fk_chat_sessions_user
    FOREIGN KEY (user_id)
    REFERENCES edushift.users (public_uuid)
    ON DELETE CASCADE;

COMMENT ON COLUMN edushift.ai_chat_sessions.user_id IS
    'public_uuid del usuario dueño de la sesión. Inyectado desde CurrentUserProvider.currentUserId() (que retorna el publicUuid del JWT). FK -> users.public_uuid (no users.id).';

-- =============================================================================
-- FIN V75
-- =============================================================================