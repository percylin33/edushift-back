-- Public inquiry intake, formal applicant details, and family-confirmed offers.
-- Existing applications remain compatible: they can keep their linked student
-- and use offer_status=NOT_REQUIRED until a new formal applicant is admitted.

CREATE TABLE edushift.admission_inquiries (
    id                       uuid          PRIMARY KEY,
    tenant_id                uuid          NOT NULL,
    public_uuid              uuid          NOT NULL,
    academic_year_id         uuid          NOT NULL,
    grade_id                 uuid          NOT NULL,
    contact_name             varchar(200)  NOT NULL,
    contact_type             varchar(10)   NOT NULL,
    contact_value            varchar(254)  NOT NULL,
    status                   varchar(20)   NOT NULL DEFAULT 'RECEIVED',
    privacy_accepted_at      timestamptz   NOT NULL,
    note                     varchar(2000),
    converted_application_id uuid,
    created_at               timestamptz   NOT NULL,
    updated_at               timestamptz   NOT NULL,
    created_by               uuid,
    updated_by               uuid,
    deleted                  boolean       NOT NULL DEFAULT false,
    deleted_at               timestamptz,
    CONSTRAINT uk_admission_inquiries_public_uuid UNIQUE (public_uuid),
    CONSTRAINT uk_admission_inquiries_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_admission_inquiries_year_tenant
        FOREIGN KEY (academic_year_id, tenant_id)
        REFERENCES edushift.academic_years (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_admission_inquiries_grade_tenant
        FOREIGN KEY (grade_id, tenant_id)
        REFERENCES edushift.grades (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT chk_admission_inquiries_contact_type CHECK (contact_type IN ('EMAIL', 'PHONE')),
    CONSTRAINT chk_admission_inquiries_status CHECK (status IN (
        'RECEIVED', 'CONTACTED', 'QUALIFIED', 'CONVERTED', 'CLOSED'
    )),
    CONSTRAINT chk_admission_inquiries_deleted_at CHECK (
        (deleted = false AND deleted_at IS NULL) OR (deleted = true AND deleted_at IS NOT NULL)
    )
);

CREATE INDEX idx_admission_inquiries_tenant_year_status
    ON edushift.admission_inquiries (tenant_id, academic_year_id, status, created_at DESC)
    WHERE deleted = false;
CREATE TRIGGER set_updated_at_admission_inquiries
    BEFORE UPDATE ON edushift.admission_inquiries
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

CREATE TABLE edushift.admission_inquiry_events (
    id              uuid          PRIMARY KEY,
    tenant_id       uuid          NOT NULL,
    inquiry_id      uuid          NOT NULL,
    from_status     varchar(20),
    to_status       varchar(20)   NOT NULL,
    note            varchar(2000),
    actor_user_id   uuid,
    actor_type      varchar(10)   NOT NULL,
    created_at      timestamptz   NOT NULL,
    updated_at      timestamptz   NOT NULL,
    created_by      uuid,
    updated_by      uuid,
    deleted         boolean       NOT NULL DEFAULT false,
    deleted_at      timestamptz,
    CONSTRAINT uk_admission_inquiry_events_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_admission_inquiry_events_inquiry_tenant
        FOREIGN KEY (inquiry_id, tenant_id)
        REFERENCES edushift.admission_inquiries (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT chk_admission_inquiry_events_to_status CHECK (to_status IN (
        'RECEIVED', 'CONTACTED', 'QUALIFIED', 'CONVERTED', 'CLOSED'
    )),
    CONSTRAINT chk_admission_inquiry_events_from_status CHECK (from_status IS NULL OR from_status IN (
        'RECEIVED', 'CONTACTED', 'QUALIFIED', 'CONVERTED', 'CLOSED'
    )),
    CONSTRAINT chk_admission_inquiry_events_actor_type CHECK (actor_type IN ('SYSTEM', 'STAFF')),
    CONSTRAINT chk_admission_inquiry_events_deleted CHECK (deleted = false AND deleted_at IS NULL)
);
CREATE INDEX idx_admission_inquiry_events_tenant_inquiry
    ON edushift.admission_inquiry_events (tenant_id, inquiry_id, created_at);
CREATE FUNCTION edushift.reject_admission_inquiry_event_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'admission inquiry history is append-only';
END;
$$;
CREATE TRIGGER admission_inquiry_events_immutable
    BEFORE UPDATE OR DELETE ON edushift.admission_inquiry_events
    FOR EACH ROW EXECUTE FUNCTION edushift.reject_admission_inquiry_event_mutation();

ALTER TABLE edushift.admission_applications
    ALTER COLUMN student_id DROP NOT NULL,
    ADD COLUMN inquiry_id uuid,
    ADD COLUMN student_document_type varchar(20),
    ADD COLUMN student_document_number varchar(20),
    ADD COLUMN student_first_name varchar(100),
    ADD COLUMN student_last_name varchar(100),
    ADD COLUMN student_second_last_name varchar(100),
    ADD COLUMN student_birth_date date,
    ADD COLUMN student_gender varchar(20),
    ADD COLUMN guardian_document_type varchar(20),
    ADD COLUMN guardian_document_number varchar(20),
    ADD COLUMN guardian_first_name varchar(100),
    ADD COLUMN guardian_last_name varchar(100),
    ADD COLUMN guardian_email varchar(254),
    ADD COLUMN guardian_phone varchar(32),
    ADD COLUMN guardian_relationship varchar(20),
    ADD COLUMN offer_status varchar(20) NOT NULL DEFAULT 'NOT_REQUIRED',
    ADD COLUMN offer_token_hash varchar(64),
    ADD COLUMN offer_sent_at timestamptz,
    ADD COLUMN offer_response_at timestamptz,
    ADD COLUMN offer_reminder_sent_at timestamptz;

ALTER TABLE edushift.admission_applications
    ADD CONSTRAINT fk_admission_applications_inquiry_tenant
        FOREIGN KEY (inquiry_id, tenant_id)
        REFERENCES edushift.admission_inquiries (id, tenant_id) ON DELETE RESTRICT,
    ADD CONSTRAINT chk_admission_applications_offer_status CHECK (offer_status IN (
        'NOT_REQUIRED', 'PENDING', 'CONFIRMED', 'DECLINED', 'AT_RISK', 'RELEASED'
    )),
    ADD CONSTRAINT chk_admission_applications_applicant_document_type CHECK (
        student_document_type IS NULL OR student_document_type IN ('DNI', 'CE', 'PASSPORT', 'OTHER')
    ),
    ADD CONSTRAINT chk_admission_applications_guardian_document_type CHECK (
        guardian_document_type IS NULL OR guardian_document_type IN ('DNI', 'CE', 'PASSPORT', 'OTHER')
    );

CREATE UNIQUE INDEX uk_admission_applications_tenant_year_applicant_document
    ON edushift.admission_applications (
        tenant_id, academic_year_id, student_document_type, student_document_number
    )
    WHERE deleted = false AND student_document_number IS NOT NULL;
CREATE INDEX idx_admission_applications_tenant_offer_due
    ON edushift.admission_applications (tenant_id, offer_status, offer_sent_at)
    WHERE deleted = false AND status = 'ADMITTED' AND offer_status IN ('PENDING', 'AT_RISK');
CREATE UNIQUE INDEX uk_admission_applications_tenant_offer_token
    ON edushift.admission_applications (tenant_id, offer_token_hash)
    WHERE offer_token_hash IS NOT NULL;

ALTER TABLE edushift.admission_inquiries
    ADD CONSTRAINT fk_admission_inquiries_converted_application_tenant
        FOREIGN KEY (converted_application_id, tenant_id)
        REFERENCES edushift.admission_applications (id, tenant_id) ON DELETE RESTRICT;
CREATE UNIQUE INDEX uk_admission_inquiries_converted_application
    ON edushift.admission_inquiries (tenant_id, converted_application_id)
    WHERE converted_application_id IS NOT NULL;

ALTER TABLE edushift.admission_status_events
    ALTER COLUMN actor_user_id DROP NOT NULL;

COMMENT ON TABLE edushift.admission_inquiries IS
    'Public family inquiries. Only name, contact channel, and interested grade are collected here.';
COMMENT ON COLUMN edushift.admission_applications.offer_status IS
    'Family confirmation state; PENDING and AT_RISK offers reserve a seat until explicitly declined or released.';
COMMENT ON COLUMN edushift.admission_applications.offer_token_hash IS
    'SHA-256 hash of the family confirmation token; raw tokens are only sent to the family.';
