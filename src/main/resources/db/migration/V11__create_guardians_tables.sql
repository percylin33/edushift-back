-- =============================================================================
-- V11__create_guardians_tables.sql
-- Guardians (parents / responsible adults) and the student↔guardian link.
-- Owned by the `students.guardians` sub-module.
--
-- Why two tables and not a denormalised JSON blob on `students`?
--   * A guardian is frequently shared between siblings (one parent, two
--     enrolled kids). Modelling that as a real association lets us
--     update the guardian's phone number once and have it correct on
--     both children.
--   * The relationship itself carries data — primary contact flag,
--     pickup permission — that belongs on the link, not on either side.
--
-- Lifecycle:
--   * `guardians` row is per-person, per-tenant. Survives the
--     unlinking of any individual student.
--   * `student_guardians` is the join row; soft-deleting it is the
--     "unlink" operation. The pair (student_id, guardian_id) is unique
--     among non-deleted links so re-linking after an unlink is OK.
-- =============================================================================

CREATE TABLE edushift.guardians (
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

    -- Identity / document (same shape as students.document_*)
    document_type       varchar(20)   NOT NULL,
    document_number     varchar(20)   NOT NULL,

    -- Profile
    first_name          varchar(100)  NOT NULL,
    last_name           varchar(100)  NOT NULL,

    -- Contact (all optional but typically populated)
    email               varchar(254),
    phone               varchar(32),
    occupation          varchar(100),

    -- Optional FK to users (when the guardian also has a portal account)
    user_id             uuid,

    CONSTRAINT uk_guardians_public_uuid UNIQUE (public_uuid),

    CONSTRAINT chk_guardians_document_type CHECK (
        document_type IN ('DNI', 'CE', 'PASSPORT', 'OTHER')
    ),

    CONSTRAINT chk_guardians_email_format CHECK (
        email IS NULL OR (email = lower(email) AND email LIKE '%_@_%.__%')
    ),

    CONSTRAINT chk_guardians_deleted_at_consistent CHECK (
        (deleted = false AND deleted_at IS NULL)
        OR (deleted = true  AND deleted_at IS NOT NULL)
    ),

    CONSTRAINT fk_guardians_user
        FOREIGN KEY (user_id) REFERENCES edushift.users(id) ON DELETE SET NULL
);

COMMENT ON TABLE  edushift.guardians                 IS 'Parents / responsible adults (per-tenant). Many-to-many with students.';
COMMENT ON COLUMN edushift.guardians.document_type   IS 'Identity document kind (DocumentType): DNI|CE|PASSPORT|OTHER';

CREATE UNIQUE INDEX uk_guardians_tenant_document_active
    ON edushift.guardians (tenant_id, document_type, document_number)
    WHERE deleted = false;

CREATE UNIQUE INDEX uk_guardians_tenant_email_active
    ON edushift.guardians (tenant_id, lower(email))
    WHERE deleted = false AND email IS NOT NULL;

CREATE INDEX idx_guardians_tenant_lastname
    ON edushift.guardians (tenant_id, lower(last_name))
    WHERE deleted = false;

CREATE TRIGGER set_updated_at_guardians
    BEFORE UPDATE ON edushift.guardians
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

-- =============================================================================
-- Join table: student ↔ guardian
-- =============================================================================

CREATE TABLE edushift.student_guardians (
    id                      uuid          PRIMARY KEY,
    tenant_id               uuid          NOT NULL,
    public_uuid             uuid          NOT NULL,

    created_at              timestamptz   NOT NULL,
    updated_at              timestamptz   NOT NULL,
    created_by              uuid,
    updated_by              uuid,
    deleted                 boolean       NOT NULL DEFAULT false,
    deleted_at              timestamptz,

    student_id              uuid          NOT NULL,
    guardian_id             uuid          NOT NULL,

    relationship            varchar(20)   NOT NULL,
    is_primary_contact      boolean       NOT NULL DEFAULT false,
    can_pickup_student      boolean       NOT NULL DEFAULT false,

    CONSTRAINT uk_student_guardians_public_uuid UNIQUE (public_uuid),

    CONSTRAINT chk_student_guardians_relationship CHECK (
        relationship IN ('FATHER', 'MOTHER', 'GRANDPARENT', 'GUARDIAN', 'OTHER')
    ),

    CONSTRAINT chk_student_guardians_deleted_at_consistent CHECK (
        (deleted = false AND deleted_at IS NULL)
        OR (deleted = true  AND deleted_at IS NOT NULL)
    ),

    CONSTRAINT fk_student_guardians_student
        FOREIGN KEY (student_id) REFERENCES edushift.students(id),

    CONSTRAINT fk_student_guardians_guardian
        FOREIGN KEY (guardian_id) REFERENCES edushift.guardians(id)
);

COMMENT ON TABLE edushift.student_guardians IS 'Many-to-many link between students and guardians, with relationship metadata.';

-- A given (student, guardian) pair is unique among non-deleted links
-- (re-linking after a soft delete is OK; we just won't have duplicate
-- active links).
CREATE UNIQUE INDEX uk_student_guardians_pair_active
    ON edushift.student_guardians (student_id, guardian_id)
    WHERE deleted = false;

-- Hot path: list a student's guardians
CREATE INDEX idx_student_guardians_student
    ON edushift.student_guardians (student_id)
    WHERE deleted = false;

-- Reverse lookup: find which students a guardian is linked to
CREATE INDEX idx_student_guardians_guardian
    ON edushift.student_guardians (guardian_id)
    WHERE deleted = false;

CREATE TRIGGER set_updated_at_student_guardians
    BEFORE UPDATE ON edushift.student_guardians
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();
