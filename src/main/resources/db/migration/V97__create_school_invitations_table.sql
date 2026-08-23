-- =============================================================================
-- V97__create_school_invitations_table.sql
-- Super-Admin invitations to create a new school. Owned by the `admin` module.
--
-- Key design decisions:
--   * Platform-level table (NO tenant_id). The school does not exist yet,
--     so this cannot extend TenantAwareEntity / Hibernate @TenantId.
--   * Mirrors user_invitations lifecycle (pending / accepted / cancelled /
--     expired derived from timestamps) with a globally unique opaque token.
--   * created_tenant_id is filled on accept for audit; ON DELETE SET NULL
--     so a later tenant wipe does not block invitation history.
-- =============================================================================

CREATE TABLE edushift.school_invitations (
    id                  uuid          PRIMARY KEY,
    public_uuid         uuid          NOT NULL,

    created_at          timestamptz   NOT NULL,
    updated_at          timestamptz   NOT NULL,
    created_by          uuid,
    updated_by          uuid,
    deleted             boolean       NOT NULL DEFAULT false,
    deleted_at          timestamptz,

    email               varchar(254)  NOT NULL,
    token               varchar(64)   NOT NULL,
    expires_at          timestamptz   NOT NULL,
    accepted_at         timestamptz,
    cancelled_at        timestamptz,
    created_tenant_id   uuid,

    CONSTRAINT uk_school_invitations_public_uuid UNIQUE (public_uuid),
    CONSTRAINT chk_school_invitations_deleted_at_consistent CHECK (
        (deleted = false AND deleted_at IS NULL)
        OR (deleted = true AND deleted_at IS NOT NULL)
    ),
    CONSTRAINT chk_school_invitations_terminal CHECK (
        accepted_at IS NULL OR cancelled_at IS NULL
    ),
    CONSTRAINT fk_school_invitations_created_tenant
        FOREIGN KEY (created_tenant_id) REFERENCES edushift.tenants (id)
        ON DELETE SET NULL
);

CREATE UNIQUE INDEX uk_school_invitations_token_active
    ON edushift.school_invitations (token)
    WHERE deleted = false;

CREATE UNIQUE INDEX uk_school_invitations_email_pending
    ON edushift.school_invitations (lower(email))
    WHERE deleted = false
      AND accepted_at IS NULL
      AND cancelled_at IS NULL;

CREATE INDEX idx_school_invitations_pending
    ON edushift.school_invitations (expires_at)
    WHERE deleted = false
      AND accepted_at IS NULL
      AND cancelled_at IS NULL;

COMMENT ON TABLE  edushift.school_invitations IS
    'Super-Admin invitations to provision a new school (platform-level, no tenant_id).';
COMMENT ON COLUMN edushift.school_invitations.token IS
    'Globally unique opaque secret mailed to the founder; consumed by POST /tenants/register.';
COMMENT ON COLUMN edushift.school_invitations.created_tenant_id IS
    'Tenant created when the invitation was accepted; null while pending.';

CREATE TRIGGER set_updated_at_school_invitations
    BEFORE UPDATE ON edushift.school_invitations
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();
