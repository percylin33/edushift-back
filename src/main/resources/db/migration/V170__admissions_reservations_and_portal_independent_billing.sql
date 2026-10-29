-- Explicit school-origin inquiries, auditable vacancy reservations, and
-- receivables that remain usable before a guardian activates the portal.

ALTER TABLE edushift.admission_inquiries
    ALTER COLUMN privacy_accepted_at DROP NOT NULL,
    ADD COLUMN source varchar(20) NOT NULL DEFAULT 'WEBSITE',
    ADD COLUMN initial_channel varchar(20) NOT NULL DEFAULT 'WEBSITE',
    ADD COLUMN privacy_notice_given_at timestamptz;

UPDATE edushift.admission_inquiries
SET privacy_notice_given_at = privacy_accepted_at
WHERE privacy_accepted_at IS NOT NULL;

ALTER TABLE edushift.admission_inquiries
    ADD CONSTRAINT chk_admission_inquiries_source
        CHECK (source IN ('WEBSITE', 'PRESENTIAL')),
    ADD CONSTRAINT chk_admission_inquiries_initial_channel
        CHECK (initial_channel IN ('WEBSITE', 'PRESENTIAL', 'PHONE', 'WHATSAPP', 'OTHER'));

CREATE TABLE edushift.admission_vacancy_reservations (
    id                  uuid          PRIMARY KEY,
    tenant_id           uuid          NOT NULL,
    public_uuid         uuid          NOT NULL,
    application_id      uuid          NOT NULL,
    academic_year_id    uuid          NOT NULL,
    grade_id            uuid          NOT NULL,
    status              varchar(20)   NOT NULL,
    expires_at          timestamptz,
    reason              varchar(1000),
    created_at          timestamptz   NOT NULL DEFAULT now(),
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,
    deleted             boolean       NOT NULL DEFAULT false,
    deleted_at          timestamptz,
    CONSTRAINT uk_admission_vacancy_reservations_public_uuid UNIQUE (public_uuid),
    CONSTRAINT uk_admission_vacancy_reservations_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uk_admission_vacancy_reservations_application UNIQUE (tenant_id, application_id),
    CONSTRAINT fk_admission_vacancy_reservations_application_tenant
        FOREIGN KEY (application_id, tenant_id)
        REFERENCES edushift.admission_applications (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_admission_vacancy_reservations_year_tenant
        FOREIGN KEY (academic_year_id, tenant_id)
        REFERENCES edushift.academic_years (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_admission_vacancy_reservations_grade_tenant
        FOREIGN KEY (grade_id, tenant_id)
        REFERENCES edushift.grades (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT chk_admission_vacancy_reservations_status
        CHECK (status IN ('TEMPORARY', 'COMMITTED', 'CONSUMED', 'EXPIRED', 'RELEASED')),
    CONSTRAINT chk_admission_vacancy_reservations_expiry
        CHECK (status <> 'TEMPORARY' OR expires_at IS NOT NULL),
    CONSTRAINT chk_admission_vacancy_reservations_deleted
        CHECK ((deleted = false AND deleted_at IS NULL) OR (deleted = true AND deleted_at IS NOT NULL))
);

CREATE INDEX idx_admission_vacancy_reservations_tenant_capacity
    ON edushift.admission_vacancy_reservations (tenant_id, academic_year_id, grade_id, status, expires_at)
    WHERE deleted = false;

CREATE TABLE edushift.admission_vacancy_reservation_events (
    id                  uuid          PRIMARY KEY,
    tenant_id           uuid          NOT NULL,
    reservation_id      uuid          NOT NULL,
    from_status         varchar(20),
    to_status           varchar(20)   NOT NULL,
    note                varchar(1000),
    actor_user_id       uuid,
    created_at          timestamptz   NOT NULL DEFAULT now(),
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,
    deleted             boolean       NOT NULL DEFAULT false,
    deleted_at          timestamptz,
    CONSTRAINT uk_admission_vacancy_reservation_events_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_admission_vacancy_reservation_events_reservation_tenant
        FOREIGN KEY (reservation_id, tenant_id)
        REFERENCES edushift.admission_vacancy_reservations (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT chk_admission_vacancy_reservation_events_status
        CHECK (to_status IN ('TEMPORARY', 'COMMITTED', 'CONSUMED', 'EXPIRED', 'RELEASED')
            AND (from_status IS NULL OR from_status IN ('TEMPORARY', 'COMMITTED', 'CONSUMED', 'EXPIRED', 'RELEASED'))),
    CONSTRAINT chk_admission_vacancy_reservation_events_deleted
        CHECK (deleted = false AND deleted_at IS NULL)
);

CREATE INDEX idx_admission_vacancy_reservation_events_tenant_reservation
    ON edushift.admission_vacancy_reservation_events (tenant_id, reservation_id, created_at);

CREATE FUNCTION edushift.reject_admission_vacancy_reservation_event_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'admission vacancy reservation history is append-only';
END;
$$;

CREATE TRIGGER admission_vacancy_reservation_events_immutable
    BEFORE UPDATE OR DELETE ON edushift.admission_vacancy_reservation_events
    FOR EACH ROW EXECUTE FUNCTION edushift.reject_admission_vacancy_reservation_event_mutation();

-- Preserve the current capacity calculation while moving reservation state
-- out of the ADMITTED application status.
INSERT INTO edushift.admission_vacancy_reservations (
    id, tenant_id, public_uuid, application_id, academic_year_id, grade_id,
    status, expires_at, reason, created_at, updated_at
)
SELECT gen_random_uuid(), a.tenant_id, gen_random_uuid(), a.id, a.academic_year_id,
       a.grade_id, 'COMMITTED', NULL, 'Migrada desde postulación admitida pendiente de matrícula.',
       now(), now()
FROM edushift.admission_applications a
WHERE a.deleted = false
  AND a.status = 'ADMITTED'
  AND a.enrollment_public_uuid IS NULL
ON CONFLICT (tenant_id, application_id) DO NOTHING;

ALTER TABLE edushift.invoices
    ALTER COLUMN guardian_user_id DROP NOT NULL,
    ADD COLUMN guardian_public_uuid uuid,
    ADD COLUMN academic_year_public_uuid uuid;

ALTER TABLE edushift.payments
    ALTER COLUMN guardian_user_id DROP NOT NULL;

COMMENT ON COLUMN edushift.invoices.guardian_user_id IS
    'Optional portal account for family access; the student and guardian person remain the billing identity.';
COMMENT ON COLUMN edushift.invoices.guardian_public_uuid IS
    'Guardian person recorded as responsible payer, whether or not they have a portal account.';
COMMENT ON COLUMN edushift.invoices.academic_year_public_uuid IS
    'Academic year for one-off enrollment charges; nullable for historical and recurring invoices.';
