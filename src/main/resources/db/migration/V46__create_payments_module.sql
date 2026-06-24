-- =============================================================================
-- V46 - Sprint 10 / BE-10.1 - Payments module: subscriptions, invoices, items,
--                               payments (with MercadoPago bridge).
-- =============================================================================
--
-- Design notes:
-- * All amounts in minor units (centavos PEN) using BIGINT. Never FLOAT
--   for money. Currency hardcoded to 'PEN' for MVP (ADR-10.5).
-- * `invoices` are the source of truth: a Payment is just a settlement
--   attempt against one or more invoices. Idempotency: invoices have
--   `idempotency_key` (unique within tenant) to prevent double-billing
--   from a retried cron or a webhook replay.
-- * `payments` mirror the MercadoPago `payment` resource: external_id is
--   the MP payment_id, status mirrors MP's status enum. Webhook updates
--   flip PENDING -> APPROVED/REJECTED/CANCELLED.
-- * Multi-tenant: every table has tenant_id, indexed; soft-delete on
--   invoices + payments via `deleted_at` (AuditableEntity).
-- =============================================================================

-- ----- subscriptions (one per family/student) -----
CREATE TABLE edushift.subscriptions (
    id                       uuid         PRIMARY KEY,
    tenant_id                uuid         NOT NULL,
    public_uuid              uuid         NOT NULL,
    student_id               uuid         NOT NULL,
    guardian_user_id         uuid         NOT NULL,
    plan_code                varchar(40)  NOT NULL,           -- 'MONTHLY_300' | 'ANNUAL_3000' | 'CUSTOM'
    amount_cents             bigint       NOT NULL,           -- recurring amount in centavos
    currency                 varchar(3)   NOT NULL DEFAULT 'PEN',
    billing_period           varchar(10)  NOT NULL,           -- 'MONTHLY' | 'ANNUAL'
    status                   varchar(15)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | PAUSED | CANCELLED
    start_at                 timestamptz  NOT NULL,
    next_billing_at          timestamptz,                      -- pre-computed by the cron
    cancelled_at             timestamptz,
    metadata                 jsonb        NOT NULL DEFAULT '{}'::jsonb,
    created_at               timestamptz  NOT NULL DEFAULT now(),
    updated_at               timestamptz  NOT NULL DEFAULT now(),
    deleted_at               timestamptz
);

CREATE UNIQUE INDEX uq_subscriptions_pubuuid ON edushift.subscriptions (public_uuid);
CREATE UNIQUE INDEX uq_subscriptions_tenant_id ON edushift.subscriptions (tenant_id, id);
CREATE INDEX        idx_subscriptions_tenant  ON edushift.subscriptions (tenant_id);
CREATE INDEX        idx_subscriptions_student ON edushift.subscriptions (tenant_id, student_id);
CREATE INDEX        idx_subscriptions_guardian ON edushift.subscriptions (tenant_id, guardian_user_id);
CREATE INDEX        idx_subscriptions_due     ON edushift.subscriptions (tenant_id, status, next_billing_at)
        WHERE deleted_at IS NULL;

COMMENT ON TABLE  edushift.subscriptions                 IS 'Recurring billing plans per student/guardian';
COMMENT ON COLUMN edushift.subscriptions.amount_cents    IS 'Recurring amount in minor units (centavos PEN)';
COMMENT ON COLUMN edushift.subscriptions.billing_period  IS 'MONTHLY | ANNUAL';


-- ----- invoices (one per period due) -----
CREATE TABLE edushift.invoices (
    id                       uuid         PRIMARY KEY,
    tenant_id                uuid         NOT NULL,
    public_uuid              uuid         NOT NULL,
    subscription_id          uuid,                            -- nullable for one-off invoices
    student_id               uuid         NOT NULL,
    guardian_user_id         uuid         NOT NULL,
    idempotency_key          varchar(120) NOT NULL,           -- e.g. 'sub:UUID:2026-06' — unique per tenant
    period_label             varchar(40)  NOT NULL,           -- '2026-06' | 'EXTRA-2026-06-15'
    currency                 varchar(3)   NOT NULL DEFAULT 'PEN',
    subtotal_cents           bigint       NOT NULL DEFAULT 0,
    discount_cents           bigint       NOT NULL DEFAULT 0,
    tax_cents                bigint       NOT NULL DEFAULT 0,
    total_cents              bigint       NOT NULL,           -- subtotal - discount + tax
    status                   varchar(15)  NOT NULL DEFAULT 'PENDING',  -- PENDING | PAID | OVERDUE | CANCELLED | REFUNDED
    issued_at                timestamptz  NOT NULL DEFAULT now(),
    due_at                   timestamptz  NOT NULL,
    paid_at                  timestamptz,
    notes                    text,
    created_at               timestamptz  NOT NULL DEFAULT now(),
    updated_at               timestamptz  NOT NULL DEFAULT now(),
    deleted_at               timestamptz
);

CREATE UNIQUE INDEX uq_invoices_pubuuid   ON edushift.invoices (public_uuid);
CREATE UNIQUE INDEX uq_invoices_tenant_id ON edushift.invoices (tenant_id, id);
CREATE UNIQUE INDEX uq_invoices_idem      ON edushift.invoices (tenant_id, idempotency_key) WHERE deleted_at IS NULL;
CREATE INDEX        idx_invoices_tenant   ON edushift.invoices (tenant_id);
CREATE INDEX        idx_invoices_student  ON edushift.invoices (tenant_id, student_id, status);
CREATE INDEX        idx_invoices_guardian ON edushift.invoices (tenant_id, guardian_user_id, status);
CREATE INDEX        idx_invoices_due      ON edushift.invoices (tenant_id, status, due_at) WHERE deleted_at IS NULL;
CREATE INDEX        idx_invoices_sub      ON edushift.invoices (tenant_id, subscription_id);

COMMENT ON TABLE  edushift.invoices             IS 'Receivable invoices (cuotas) issued to guardians';
COMMENT ON COLUMN edushift.invoices.total_cents IS 'Final amount in minor units (centavos PEN)';
COMMENT ON COLUMN edushift.invoices.idempotency_key IS 'Dedupes cron runs and webhook replays';


-- ----- invoice_items (line items) -----
CREATE TABLE edushift.invoice_items (
    id                       uuid         PRIMARY KEY,
    tenant_id                uuid         NOT NULL,
    invoice_id               uuid         NOT NULL,
    description              varchar(255) NOT NULL,
    quantity                 integer      NOT NULL DEFAULT 1,
    unit_amount_cents        bigint       NOT NULL,
    line_total_cents         bigint       NOT NULL,
    created_at               timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_invoice_items_invoice ON edushift.invoice_items (tenant_id, invoice_id);

COMMENT ON TABLE edushift.invoice_items IS 'Line items per invoice (tuition, materials, etc.)';


-- ----- payments (MercadoPago bridge) -----
CREATE TABLE edushift.payments (
    id                       uuid         PRIMARY KEY,
    tenant_id                uuid         NOT NULL,
    public_uuid              uuid         NOT NULL,
    invoice_id               uuid         NOT NULL,
    guardian_user_id         uuid         NOT NULL,
    provider                 varchar(20)  NOT NULL DEFAULT 'MERCADOPAGO',  -- MERCADOPAGO | MANUAL | CASH
    external_id              varchar(80),                  -- MP payment_id; null for MANUAL until recorded
    external_reference       varchar(120),                 -- MP external_reference (we put our invoice public_uuid)
    status                   varchar(20)  NOT NULL DEFAULT 'PENDING',     -- PENDING | APPROVED | REJECTED | CANCELLED | REFUNDED | IN_PROCESS
    amount_cents             bigint       NOT NULL,
    currency                 varchar(3)   NOT NULL DEFAULT 'PEN',
    payment_method           varchar(40),                  -- visa | master | account_money | etc.
    installments             integer,
    paid_at                  timestamptz,
    raw_response             jsonb        NOT NULL DEFAULT '{}'::jsonb,  -- last webhook payload (audit)
    failure_reason           text,
    created_at               timestamptz  NOT NULL DEFAULT now(),
    updated_at               timestamptz  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_payments_pubuuid    ON edushift.payments (public_uuid);
CREATE UNIQUE INDEX uq_payments_external   ON edushift.payments (tenant_id, provider, external_id)
        WHERE external_id IS NOT NULL;
CREATE INDEX        idx_payments_tenant    ON edushift.payments (tenant_id);
CREATE INDEX        idx_payments_invoice   ON edushift.payments (tenant_id, invoice_id);
CREATE INDEX        idx_payments_guardian  ON edushift.payments (tenant_id, guardian_user_id, status);

COMMENT ON TABLE  edushift.payments                  IS 'Payment attempts (mostly MercadoPago webhooks)';
COMMENT ON COLUMN edushift.payments.external_id      IS 'MercadoPago payment_id (unique per provider+tenant)';
COMMENT ON COLUMN edushift.payments.external_reference IS 'Our invoice public_uuid sent as MP external_reference';


-- ----- FKs (added last to avoid forward-ref errors) -----
-- NB: we FK to students(tenant_id, id) and a UNIQUE index on (tenant_id, id) is
-- required. `students.id` is the PK (so unique), but Postgres does not infer
-- a composite UNIQUE from the PK alone. We add an explicit index that pairs
-- the tenant discriminator with the PK column so the FKs declared below can
-- reference a real UNIQUE key.
CREATE UNIQUE INDEX uq_students_tenant_id ON edushift.students (tenant_id, id);

ALTER TABLE edushift.subscriptions
    ADD CONSTRAINT fk_subscriptions_student
        FOREIGN KEY (tenant_id, student_id)
        REFERENCES edushift.students (tenant_id, id) ON DELETE RESTRICT;

ALTER TABLE edushift.invoices
    ADD CONSTRAINT fk_invoices_subscription
        FOREIGN KEY (tenant_id, subscription_id)
        REFERENCES edushift.subscriptions (tenant_id, id) ON DELETE SET NULL;

ALTER TABLE edushift.invoices
    ADD CONSTRAINT fk_invoices_student
        FOREIGN KEY (tenant_id, student_id)
        REFERENCES edushift.students (tenant_id, id) ON DELETE RESTRICT;

ALTER TABLE edushift.invoice_items
    ADD CONSTRAINT fk_invoice_items_invoice
        FOREIGN KEY (tenant_id, invoice_id)
        REFERENCES edushift.invoices (tenant_id, id) ON DELETE CASCADE;

ALTER TABLE edushift.payments
    ADD CONSTRAINT fk_payments_invoice
        FOREIGN KEY (tenant_id, invoice_id)
        REFERENCES edushift.invoices (tenant_id, id) ON DELETE RESTRICT;
