-- =============================================================================
-- V90 - Re-target ai_generations.request_user_id FK from users(id) to
-- users(public_uuid) (DEBT-FK-BUGS-2 follow-up, same pattern as V75).
--
-- Runtime code injects CurrentUserProvider.currentUserId() (JWT publicUuid)
-- into request_user_id. V36 originally referenced users(id), causing
-- DATA_INTEGRITY_VIOLATION on insert when the schema expects public_uuid.
--
-- ORDERING MATTERS: the existing FK points at users(id). If we ran the
-- UPDATE first, PostgreSQL would reject every row whose request_user_id
-- stops being a valid users(id) (the new value would be a public_uuid, not
-- an internal id). Drop the FK first, do the backfill, then re-create the
-- FK pointing at public_uuid. Same pattern V75 uses for the LMS tables.
-- =============================================================================

ALTER TABLE edushift.ai_generations
    DROP CONSTRAINT IF EXISTS fk_ai_generations_user;

-- Backfill rows that still store the internal users.id (legacy dev data).
UPDATE edushift.ai_generations g
   SET request_user_id = u.public_uuid
  FROM edushift.users u
 WHERE g.request_user_id = u.id
   AND g.request_user_id IS NOT NULL;

ALTER TABLE edushift.ai_generations
    ADD CONSTRAINT fk_ai_generations_user
    FOREIGN KEY (request_user_id)
    REFERENCES edushift.users (public_uuid)
    ON DELETE SET NULL;

COMMENT ON COLUMN edushift.ai_generations.request_user_id IS
    'public_uuid of the requesting user. Injected from CurrentUserProvider.currentUserId(). FK -> users.public_uuid (not users.id).';
