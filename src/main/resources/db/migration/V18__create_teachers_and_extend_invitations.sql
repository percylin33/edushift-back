-- =============================================================================
-- V18__create_teachers_and_extend_invitations.sql
-- Teacher aggregate (per-tenant) + cross-module hook on user_invitations.
-- Sprint 4 / BE-4.6.
--
-- Two related changes ship together because they share a use case:
--
--   * teachers                — the new aggregate.
--   * user_invitations.metadata — small jsonb side-channel that lets the
--                                 invitation flow carry the teacherId
--                                 forward, so accepting the invitation
--                                 atomically links teacher.user_id to
--                                 the freshly-created user.
--
-- Same shape as `students` (V10): document_type+document_number unique
-- per tenant on non-deleted rows; email unique per tenant on
-- non-deleted rows when present; partial unique index on user_id so
-- the same User cannot be linked to two Teachers.
-- =============================================================================

-- ----------------------------------------------------------------------------
-- 1) Extend user_invitations with the optional `metadata` payload.
-- ----------------------------------------------------------------------------
ALTER TABLE edushift.user_invitations
    ADD COLUMN metadata jsonb NOT NULL DEFAULT '{}'::jsonb;

COMMENT ON COLUMN edushift.user_invitations.metadata
    IS 'Free-form jsonb side-channel so callers (e.g. teachers.invite) can carry domain ids forward and react on accept. Keys are convention-driven, e.g. { "teacherId": "<uuid>" }.';


-- ----------------------------------------------------------------------------
-- 2) teachers
-- ----------------------------------------------------------------------------
CREATE TABLE edushift.teachers (
    id                  uuid          PRIMARY KEY,
    tenant_id           uuid          NOT NULL,
    public_uuid         uuid          NOT NULL,

    -- Audit (inherited from AuditableEntity / BaseEntity)
    created_at          timestamptz   NOT NULL,
    updated_at          timestamptz   NOT NULL,
    created_by          uuid,
    updated_by          uuid,
    deleted             boolean       NOT NULL DEFAULT false,
    deleted_at          timestamptz,

    -- Identity / document (reuses students enums via shared CHECKs)
    document_type       varchar(20)   NOT NULL,
    document_number     varchar(20)   NOT NULL,

    -- Profile
    first_name          varchar(100)  NOT NULL,
    last_name           varchar(100)  NOT NULL,
    second_last_name    varchar(100),
    birth_date          date,
    gender              varchar(20)   NOT NULL DEFAULT 'NOT_SPECIFIED',

    -- Contact (all optional)
    email               varchar(254),
    phone               varchar(32),

    -- Teacher-specific
    title               varchar(50),
    specializations     jsonb         NOT NULL DEFAULT '[]'::jsonb,
    hire_date           date,
    employment_status   varchar(20)   NOT NULL DEFAULT 'ACTIVE',

    -- Optional link to the auth.users table (set when teacher has an account)
    user_id             uuid,

    -- Free-form extension fields
    metadata            jsonb         NOT NULL DEFAULT '{}'::jsonb,

    CONSTRAINT uk_teachers_public_uuid UNIQUE (public_uuid),

    CONSTRAINT chk_teachers_document_type CHECK (
        document_type IN ('DNI', 'CE', 'PASSPORT', 'OTHER')
    ),

    CONSTRAINT chk_teachers_gender CHECK (
        gender IN ('MALE', 'FEMALE', 'OTHER', 'NOT_SPECIFIED')
    ),

    CONSTRAINT chk_teachers_employment_status CHECK (
        employment_status IN ('ACTIVE', 'ON_LEAVE', 'RESIGNED', 'RETIRED', 'SUSPENDED')
    ),

    CONSTRAINT chk_teachers_email_format CHECK (
        email IS NULL OR (email = lower(email) AND email LIKE '%_@_%.__%')
    ),

    CONSTRAINT chk_teachers_specializations_is_array CHECK (
        jsonb_typeof(specializations) = 'array'
    ),

    CONSTRAINT chk_teachers_deleted_at_consistent CHECK (
        (deleted = false AND deleted_at IS NULL)
        OR (deleted = true  AND deleted_at IS NOT NULL)
    ),

    CONSTRAINT fk_teachers_user
        FOREIGN KEY (user_id) REFERENCES edushift.users(id) ON DELETE SET NULL
);

COMMENT ON TABLE  edushift.teachers                  IS 'Teaching staff at a tenant institution (Sprint 4 / BE-4.6).';
COMMENT ON COLUMN edushift.teachers.public_uuid      IS 'External, stable identifier exposed via REST (UUIDv4)';
COMMENT ON COLUMN edushift.teachers.tenant_id        IS 'Tenant scope; managed by Hibernate discriminator multi-tenancy';
COMMENT ON COLUMN edushift.teachers.title            IS 'Honorific / academic title (Lic., Mg., Dr., Prof., Ing., ...). Free-form.';
COMMENT ON COLUMN edushift.teachers.specializations  IS 'jsonb array of free-form strings (e.g. ["Matematica","Fisica"]).';
COMMENT ON COLUMN edushift.teachers.employment_status IS 'Lifecycle (EmploymentStatus): ACTIVE|ON_LEAVE|RESIGNED|RETIRED|SUSPENDED';
COMMENT ON COLUMN edushift.teachers.user_id          IS 'Optional FK to users.id. Unique per teacher (one user → at most one teacher).';
COMMENT ON COLUMN edushift.teachers.metadata         IS 'Free-form jsonb for institution-specific extension fields.';


-- ----------------------------------------------------------------------------
-- Indexes (partial on non-deleted rows; hot path stays small)
-- ----------------------------------------------------------------------------

-- (tenant, documentType, documentNumber) identifies one teacher
CREATE UNIQUE INDEX uk_teachers_tenant_document_active
    ON edushift.teachers (tenant_id, document_type, document_number)
    WHERE deleted = false;

-- email unique per tenant when present
CREATE UNIQUE INDEX uk_teachers_tenant_email_active
    ON edushift.teachers (tenant_id, lower(email))
    WHERE deleted = false AND email IS NOT NULL;

-- "one user → one teacher" rule. user_id is global but teachers are
-- tenant-scoped; the partial index here makes the constraint visible
-- at the SQL layer regardless of tenant scope of the writes.
CREATE UNIQUE INDEX uk_teachers_user_active
    ON edushift.teachers (user_id)
    WHERE deleted = false AND user_id IS NOT NULL;

-- List dashboards: filter by tenant + employment status
CREATE INDEX idx_teachers_tenant_employment_status
    ON edushift.teachers (tenant_id, employment_status)
    WHERE deleted = false;

-- Search index for case-insensitive name lookup
CREATE INDEX idx_teachers_tenant_lastname
    ON edushift.teachers (tenant_id, lower(last_name), lower(first_name))
    WHERE deleted = false;


-- ----------------------------------------------------------------------------
-- Triggers
-- ----------------------------------------------------------------------------
CREATE TRIGGER set_updated_at_teachers
    BEFORE UPDATE ON edushift.teachers
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();
