-- Cash or verified transfers received while separating a vacancy are
-- recorded against the reservation, then applied once to the final invoice.
ALTER TABLE edushift.invoices
    ADD COLUMN advance_applied_cents bigint NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_invoices_advance_applied_cents
        CHECK (advance_applied_cents >= 0 AND advance_applied_cents <= total_cents);

CREATE TABLE edushift.admission_vacancy_reservation_advances (
    id                  uuid          PRIMARY KEY,
    tenant_id           uuid          NOT NULL,
    public_uuid         uuid          NOT NULL,
    reservation_id      uuid          NOT NULL,
    amount_cents        bigint        NOT NULL,
    applied_cents       bigint        NOT NULL DEFAULT 0,
    currency            varchar(3)    NOT NULL DEFAULT 'PEN',
    method              varchar(20)   NOT NULL,
    status              varchar(24)   NOT NULL,
    reference           varchar(120),
    notes               varchar(500),
    received_at         timestamptz   NOT NULL DEFAULT now(),
    created_at          timestamptz   NOT NULL DEFAULT now(),
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,
    deleted             boolean       NOT NULL DEFAULT false,
    deleted_at          timestamptz,
    CONSTRAINT uk_admission_vacancy_reservation_advances_public_uuid UNIQUE (public_uuid),
    CONSTRAINT uk_admission_vacancy_reservation_advances_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_admission_vacancy_reservation_advances_reservation_tenant
        FOREIGN KEY (reservation_id, tenant_id)
        REFERENCES edushift.admission_vacancy_reservations (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT chk_admission_vacancy_reservation_advances_amount
        CHECK (amount_cents > 0 AND applied_cents >= 0 AND applied_cents <= amount_cents),
    CONSTRAINT chk_admission_vacancy_reservation_advances_method
        CHECK (method IN ('CASH', 'TRANSFER', 'POS', 'OTHER')),
    CONSTRAINT chk_admission_vacancy_reservation_advances_status
        CHECK (status IN ('AVAILABLE', 'PARTIALLY_APPLIED', 'APPLIED', 'REFUNDED')),
    CONSTRAINT chk_admission_vacancy_reservation_advances_deleted
        CHECK ((deleted = false AND deleted_at IS NULL) OR (deleted = true AND deleted_at IS NOT NULL))
);

CREATE INDEX idx_admission_vacancy_reservation_advances_tenant_reservation
    ON edushift.admission_vacancy_reservation_advances (tenant_id, reservation_id, received_at)
    WHERE deleted = false;

CREATE TABLE edushift.admission_vacancy_reservation_advance_events (
    id                  uuid          PRIMARY KEY,
    tenant_id           uuid          NOT NULL,
    advance_id          uuid          NOT NULL,
    action              varchar(24)   NOT NULL,
    amount_cents        bigint        NOT NULL,
    invoice_public_uuid uuid,
    note                varchar(500),
    actor_user_id       uuid,
    created_at          timestamptz   NOT NULL DEFAULT now(),
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,
    deleted             boolean       NOT NULL DEFAULT false,
    deleted_at          timestamptz,
    CONSTRAINT uk_admission_vacancy_reservation_advance_events_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_admission_vacancy_reservation_advance_events_advance_tenant
        FOREIGN KEY (advance_id, tenant_id)
        REFERENCES edushift.admission_vacancy_reservation_advances (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT chk_admission_vacancy_reservation_advance_events_action
        CHECK (action IN ('RECEIVED', 'APPLIED', 'REFUNDED')),
    CONSTRAINT chk_admission_vacancy_reservation_advance_events_amount CHECK (amount_cents > 0),
    CONSTRAINT chk_admission_vacancy_reservation_advance_events_deleted
        CHECK (deleted = false AND deleted_at IS NULL)
);

CREATE INDEX idx_admission_vacancy_reservation_advance_events_tenant_advance
    ON edushift.admission_vacancy_reservation_advance_events (tenant_id, advance_id, created_at);

CREATE FUNCTION edushift.reject_admission_vacancy_reservation_advance_event_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'admission reservation advance history is append-only';
END;
$$;

CREATE TRIGGER admission_vacancy_reservation_advance_events_immutable
    BEFORE UPDATE OR DELETE ON edushift.admission_vacancy_reservation_advance_events
    FOR EACH ROW EXECUTE FUNCTION edushift.reject_admission_vacancy_reservation_advance_event_mutation();
