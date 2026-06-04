-- =============================================================================
-- V8__add_users_roles_column.sql
-- Per-user role assignments for Sprint 2 (BE-2.4).
--
-- Sprint 2 introduces self-signup: anyone hitting POST /v1/tenants/register
-- creates their own tenant and immediately needs the TENANT_ADMIN role to
-- finish onboarding (PATCH /tenants/me). The seed admin in `dev` also needs
-- it so the existing dev environment keeps working with @PreAuthorize gates.
--
-- Why the simple shape (varchar[] in users) instead of a proper roles +
-- role_permissions schema:
--   * Sprint 2's reach is limited — TENANT_ADMIN is the only role we need
--     to enforce in the API right now. Roll-out of TEACHER / STUDENT /
--     PARENT / STAFF lands in Sprint 3 alongside the user-management UX.
--   * Carrying a Postgres TEXT array on `users` keeps the JWT claim trivial
--     (just a JSON array of strings the front already understands).
--   * When Sprint 3 promotes roles to a relational catalog with permission
--     bundles, the migration is purely additive: introduce the new tables,
--     backfill from this column, then deprecate the column. The JWT shape
--     does NOT need to change — it stays a flat array of role names.
--
-- Backfill:
--   * The seed admin (`admin@demo.edushift.pe` in tenant `demo`) gets
--     ARRAY['TENANT_ADMIN'] so the existing dev login keeps working with
--     the role gate active.
--   * Every other existing user (none yet outside the seed) gets the
--     default empty array, which means "no role-gated endpoint accessible".
-- =============================================================================

ALTER TABLE edushift.users
    ADD COLUMN roles varchar(40)[] NOT NULL DEFAULT '{}';

COMMENT ON COLUMN edushift.users.roles IS
    'Role names assigned to this user. Mirrors UserRole enum: TENANT_ADMIN, '
    'TEACHER, STUDENT, PARENT, STAFF. Surfaced as the `roles` JWT claim '
    'and consumed by Spring Security via SimpleGrantedAuthority("ROLE_*").';

-- ----------------------------------------------------------------------------
-- Backfill: seed admin needs TENANT_ADMIN so dev environment still works
-- ----------------------------------------------------------------------------
UPDATE edushift.users
SET    roles = ARRAY['TENANT_ADMIN']::varchar[]
WHERE  email = 'admin@demo.edushift.pe'
  AND  cardinality(roles) = 0;

-- ----------------------------------------------------------------------------
-- Index: GIN supports `'TENANT_ADMIN' = ANY(roles)` lookups efficiently.
-- We don't need it for /me-style lookups but a future "list all admins"
-- endpoint will hit this. Cheap to add now while the table is small.
-- ----------------------------------------------------------------------------
CREATE INDEX idx_users_roles_gin
    ON edushift.users USING GIN (roles)
    WHERE deleted = false;
