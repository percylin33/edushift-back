-- =============================================================================
-- V10__create_students_table.sql
-- Student aggregate (per-tenant). Owned by the `students` module.
--
-- Key design decisions:
--   * `id`              internal UUIDv7 (time-ordered; never exposed via API)
--   * `public_uuid`     external UUIDv4 surfaced to clients
--   * `tenant_id`       mandatory; managed by Hibernate discriminator
--                       multi-tenancy (@TenantId on TenantAwareEntity)
--   * `document_type` + `document_number` together identify a student
--                       within a tenant. The combination is unique per
--                       tenant on non-deleted rows.
--   * `email` is optional but, when present, also unique per tenant on
--                       non-deleted rows (case-insensitive).
--   * `metadata`         free-form jsonb for institution-specific extra
--                       fields (blood type, allergies, ...). Always '{}'
--                       by default to keep queries trivial.
--   * `user_id`          optional FK to users — set when the student also
--                       has an account in the system (older students,
--                       families that opt into a portal). Sprint 3 lets
--                       it stay null for everyone; Sprint 4+ wires it.
--   * `enrollment_*`     lifecycle of the student INSIDE the institution.
--                       Distinct from `deleted` (admin removal) and from
--                       any future "school-year" cycle.
-- =============================================================================

CREATE TABLE edushift.students (
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

    -- Identity / document
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
    address             varchar(500),

    -- Enrollment lifecycle (inside the institution)
    enrollment_status   varchar(20)   NOT NULL DEFAULT 'PENDING',
    enrollment_date     date,

    -- Optional link to the auth.users table
    user_id             uuid,

    -- Free-form extension fields (allergies, blood type, custom flags, ...)
    metadata            jsonb         NOT NULL DEFAULT '{}'::jsonb,

    CONSTRAINT uk_students_public_uuid UNIQUE (public_uuid),

    CONSTRAINT chk_students_document_type CHECK (
        document_type IN ('DNI', 'CE', 'PASSPORT', 'OTHER')
    ),

    CONSTRAINT chk_students_gender CHECK (
        gender IN ('MALE', 'FEMALE', 'OTHER', 'NOT_SPECIFIED')
    ),

    CONSTRAINT chk_students_enrollment_status CHECK (
        enrollment_status IN ('PENDING', 'ENROLLED', 'GRADUATED', 'TRANSFERRED', 'WITHDRAWN')
    ),

    CONSTRAINT chk_students_email_format CHECK (
        email IS NULL OR (email = lower(email) AND email LIKE '%_@_%.__%')
    ),

    CONSTRAINT chk_students_deleted_at_consistent CHECK (
        (deleted = false AND deleted_at IS NULL)
        OR (deleted = true  AND deleted_at IS NOT NULL)
    ),

    CONSTRAINT fk_students_user
        FOREIGN KEY (user_id) REFERENCES edushift.users(id) ON DELETE SET NULL
);

COMMENT ON TABLE  edushift.students                  IS 'Students enrolled at a tenant institution.';
COMMENT ON COLUMN edushift.students.public_uuid      IS 'External, stable identifier exposed via REST (UUIDv4)';
COMMENT ON COLUMN edushift.students.tenant_id        IS 'Tenant scope; managed by Hibernate discriminator multi-tenancy';
COMMENT ON COLUMN edushift.students.document_type    IS 'Identity document kind (DocumentType): DNI|CE|PASSPORT|OTHER';
COMMENT ON COLUMN edushift.students.gender           IS 'Gender (Gender): MALE|FEMALE|OTHER|NOT_SPECIFIED';
COMMENT ON COLUMN edushift.students.enrollment_status IS 'Lifecycle (EnrollmentStatus): PENDING|ENROLLED|GRADUATED|TRANSFERRED|WITHDRAWN';
COMMENT ON COLUMN edushift.students.user_id          IS 'Optional FK to users.id when the student also has an account';
COMMENT ON COLUMN edushift.students.metadata         IS 'Free-form jsonb for institution-specific extension fields';

-- ----------------------------------------------------------------------------
-- Indexes (partial: only non-deleted rows participate, hot path stays small)
-- ----------------------------------------------------------------------------

-- A given (tenant, documentType, documentNumber) triple identifies one student
CREATE UNIQUE INDEX uk_students_tenant_document_active
    ON edushift.students (tenant_id, document_type, document_number)
    WHERE deleted = false;

-- Optional email is unique per tenant when present
CREATE UNIQUE INDEX uk_students_tenant_email_active
    ON edushift.students (tenant_id, lower(email))
    WHERE deleted = false AND email IS NOT NULL;

-- List dashboards: filter by tenant + enrollment status (e.g. all ENROLLED)
CREATE INDEX idx_students_tenant_enrollment_status
    ON edushift.students (tenant_id, enrollment_status)
    WHERE deleted = false;

-- Search index for the case-insensitive name lookup hot path
CREATE INDEX idx_students_tenant_lastname
    ON edushift.students (tenant_id, lower(last_name), lower(first_name))
    WHERE deleted = false;

-- Reverse FK lookup: which student is associated with a given user?
CREATE INDEX idx_students_user_id
    ON edushift.students (user_id)
    WHERE deleted = false AND user_id IS NOT NULL;

-- ----------------------------------------------------------------------------
-- Triggers
-- ----------------------------------------------------------------------------
CREATE TRIGGER set_updated_at_students
    BEFORE UPDATE ON edushift.students
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();
