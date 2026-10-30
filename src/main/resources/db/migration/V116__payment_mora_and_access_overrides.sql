-- =============================================================================
-- V116 - Payment policy support: mora tracking + LMS soft-gate overrides
-- =============================================================================

ALTER TABLE edushift.invoices
    ADD COLUMN IF NOT EXISTS mora_cents bigint NOT NULL DEFAULT 0;

ALTER TABLE edushift.invoices
    ADD COLUMN IF NOT EXISTS mora_applied_at timestamptz;

ALTER TABLE edushift.invoices
    ADD COLUMN IF NOT EXISTS mora_exempt boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN edushift.invoices.mora_cents IS 'Late-fee amount applied (centavos); included in total_cents when applied';
COMMENT ON COLUMN edushift.invoices.mora_exempt IS 'When true, MoraScheduler skips this invoice';

CREATE TABLE IF NOT EXISTS edushift.payment_access_overrides (
    id              uuid         PRIMARY KEY,
    tenant_id       uuid         NOT NULL,
    public_uuid     uuid         NOT NULL,
    user_public_uuid uuid        NOT NULL,
    student_public_uuid uuid,
    reason          varchar(500),
    until_at        timestamptz  NOT NULL,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,
    deleted         boolean      NOT NULL DEFAULT false
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_access_overrides_pub
    ON edushift.payment_access_overrides (public_uuid);
CREATE INDEX IF NOT EXISTS idx_payment_access_overrides_user
    ON edushift.payment_access_overrides (tenant_id, user_public_uuid)
    WHERE deleted = false;

COMMENT ON TABLE edushift.payment_access_overrides IS
    'Temporary lift of LMS soft-gate for a user (delinquency override)';
