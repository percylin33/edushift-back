-- =============================================================================
-- V99 - ADR-CRED-3: re-target guardians.user_id FK from users(id) to
-- users(public_uuid).
--
-- Contexto:
--   V11 creó `fk_guardians_user` apuntando a `users(id)` (UUIDv7 PK).
--   V76/V77 ya migraron students y teachers a `users.public_uuid`
--   (lo que lleva el JWT `sub`). Family lista hijos comparando
--   `guardians.user_id` con el public uuid del padre autenticado.
--   Mientras esta columna siga en el PK interno, un PARENT seed o
--   recién aceptado ve `/family` vacío.
--
-- Orden (forward-only; V11 inmutable):
--   1. DROP CONSTRAINT fk_guardians_user.
--   2. Traducir user_id = users.id → users.public_uuid.
--      Filas que YA almacenan public_uuid (si alguien backfilleo a
--      mano) no matchean `u.id = g.user_id` y se dejan intactas.
--   3. Huérfanas (ni id ni public_uuid válidos) → NULL.
--   4. ADD CONSTRAINT fk_guardians_user → users(public_uuid)
--      ON DELETE SET NULL.
--   5. UNIQUE parcial: un User PARENT como máximo un Guardian.
-- =============================================================================

ALTER TABLE edushift.guardians
    DROP CONSTRAINT fk_guardians_user;

UPDATE edushift.guardians g
SET    user_id = u.public_uuid
FROM   edushift.users u
WHERE  u.id = g.user_id
  AND  g.user_id IS NOT NULL;

UPDATE edushift.guardians
SET    user_id = NULL
WHERE  user_id IS NOT NULL
  AND  NOT EXISTS (
        SELECT 1 FROM edushift.users u
        WHERE  u.public_uuid = edushift.guardians.user_id
      );

ALTER TABLE edushift.guardians
    ADD CONSTRAINT fk_guardians_user
    FOREIGN KEY (user_id)
    REFERENCES edushift.users (public_uuid)
    ON DELETE SET NULL;

CREATE UNIQUE INDEX uk_guardians_user_active
    ON edushift.guardians (user_id)
    WHERE deleted = false AND user_id IS NOT NULL;

COMMENT ON COLUMN edushift.guardians.user_id IS
    'public_uuid del usuario PARENT asociado al tutor (cuando el tutor tiene portal). FK -> users.public_uuid (no users.id). Nullable: tutores sin cuenta quedan con user_id NULL.';

-- =============================================================================
-- FIN V99
-- =============================================================================
