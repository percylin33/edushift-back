-- =============================================================================
-- V155 — Admission applications, state history, and school-year vacancy views.
-- Seat availability is derived from section capacity and active enrollments.
-- =============================================================================

CREATE UNIQUE INDEX IF NOT EXISTS uk_grades_id_tenant_admissions
    ON edushift.grades (id, tenant_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_student_enrollments_public_tenant_admissions
    ON edushift.student_enrollments (public_uuid, tenant_id);

CREATE TABLE edushift.admission_applications (
    id                      uuid          PRIMARY KEY,
    tenant_id               uuid          NOT NULL,
    public_uuid             uuid          NOT NULL,
    academic_year_id        uuid          NOT NULL,
    student_id              uuid          NOT NULL,
    grade_id                uuid          NOT NULL,
    status                  varchar(20)   NOT NULL DEFAULT 'RECEIVED',
    source                  varchar(20)   NOT NULL DEFAULT 'IN_PERSON',
    submitted_at            timestamptz   NOT NULL,
    last_status_note        varchar(2000),
    enrollment_public_uuid  uuid,
    created_at              timestamptz   NOT NULL,
    updated_at              timestamptz   NOT NULL,
    created_by              uuid,
    updated_by              uuid,
    deleted                 boolean       NOT NULL DEFAULT false,
    deleted_at              timestamptz,
    CONSTRAINT uk_admission_applications_public_uuid UNIQUE (public_uuid),
    CONSTRAINT uk_admission_applications_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_admission_applications_year_tenant
        FOREIGN KEY (academic_year_id, tenant_id)
        REFERENCES edushift.academic_years (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_admission_applications_student_tenant
        FOREIGN KEY (student_id, tenant_id)
        REFERENCES edushift.students (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_admission_applications_grade_tenant
        FOREIGN KEY (grade_id, tenant_id)
        REFERENCES edushift.grades (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_admission_applications_enrollment_tenant
        FOREIGN KEY (enrollment_public_uuid, tenant_id)
        REFERENCES edushift.student_enrollments (public_uuid, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT chk_admission_applications_status CHECK (status IN (
        'RECEIVED', 'UNDER_REVIEW', 'ADMITTED', 'WAITLISTED', 'REJECTED', 'WITHDRAWN', 'ENROLLED'
    )),
    CONSTRAINT chk_admission_applications_source CHECK (source IN (
        'IN_PERSON', 'REFERRAL', 'WEBSITE', 'OTHER'
    )),
    CONSTRAINT chk_admission_applications_enrolled_link CHECK (
        (status = 'ENROLLED' AND enrollment_public_uuid IS NOT NULL)
        OR (status <> 'ENROLLED' AND enrollment_public_uuid IS NULL)
    ),
    CONSTRAINT chk_admission_applications_deleted_at CHECK (
        (deleted = false AND deleted_at IS NULL) OR (deleted = true AND deleted_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uk_admission_applications_tenant_year_student
    ON edushift.admission_applications (tenant_id, academic_year_id, student_id)
    WHERE deleted = false;
CREATE INDEX idx_admission_applications_tenant_year_status
    ON edushift.admission_applications (tenant_id, academic_year_id, status, submitted_at DESC)
    WHERE deleted = false;
CREATE INDEX idx_admission_applications_tenant_year_grade
    ON edushift.admission_applications (tenant_id, academic_year_id, grade_id, status)
    WHERE deleted = false;
CREATE TRIGGER set_updated_at_admission_applications
    BEFORE UPDATE ON edushift.admission_applications
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON TABLE edushift.admission_applications IS
    'School-managed admission applications, separated from active student_enrollments.';
COMMENT ON COLUMN edushift.admission_applications.enrollment_public_uuid IS
    'Links a successfully enrolled applicant to the existing per-year enrollment record.';

CREATE TABLE edushift.admission_status_events (
    id              uuid          PRIMARY KEY,
    tenant_id       uuid          NOT NULL,
    application_id  uuid          NOT NULL,
    from_status     varchar(20),
    to_status       varchar(20)   NOT NULL,
    note            varchar(2000),
    actor_user_id   uuid          NOT NULL,
    created_at      timestamptz   NOT NULL,
    updated_at      timestamptz   NOT NULL,
    created_by      uuid,
    updated_by      uuid,
    deleted         boolean       NOT NULL DEFAULT false,
    deleted_at      timestamptz,
    CONSTRAINT uk_admission_status_events_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_admission_status_events_application_tenant
        FOREIGN KEY (application_id, tenant_id)
        REFERENCES edushift.admission_applications (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT chk_admission_status_events_to_status CHECK (to_status IN (
        'RECEIVED', 'UNDER_REVIEW', 'ADMITTED', 'WAITLISTED', 'REJECTED', 'WITHDRAWN', 'ENROLLED'
    )),
    CONSTRAINT chk_admission_status_events_from_status CHECK (from_status IS NULL OR from_status IN (
        'RECEIVED', 'UNDER_REVIEW', 'ADMITTED', 'WAITLISTED', 'REJECTED', 'WITHDRAWN', 'ENROLLED'
    )),
    CONSTRAINT chk_admission_status_events_deleted CHECK (deleted = false AND deleted_at IS NULL)
);

CREATE INDEX idx_admission_status_events_tenant_application
    ON edushift.admission_status_events (tenant_id, application_id, created_at);
CREATE FUNCTION edushift.reject_admission_status_event_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'admission status history is append-only';
END;
$$;
CREATE TRIGGER admission_status_events_immutable
    BEFORE UPDATE OR DELETE ON edushift.admission_status_events
    FOR EACH ROW EXECUTE FUNCTION edushift.reject_admission_status_event_mutation();

COMMENT ON TABLE edushift.admission_status_events IS
    'Append-only history of decisions and status changes in admissions.';
