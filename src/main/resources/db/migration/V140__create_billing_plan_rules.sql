-- Persistent, tenant-scoped rules for assigning recurring tuition plans.
CREATE TABLE edushift.billing_plan_rules (
    id                  uuid         PRIMARY KEY,
    tenant_id           uuid         NOT NULL,
    public_uuid         uuid         NOT NULL,
    concept_id          uuid         NOT NULL,
    level_id            uuid,
    grade_id            uuid,
    amount_cents        bigint       NOT NULL,
    currency            varchar(3)   NOT NULL DEFAULT 'PEN',
    billing_period      varchar(10)  NOT NULL,
    billing_day_of_month smallint    NOT NULL,
    billing_month       smallint,
    active              boolean      NOT NULL DEFAULT true,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,
    deleted             boolean      NOT NULL DEFAULT false,
    deleted_at          timestamptz,
    CONSTRAINT uk_billing_plan_rules_public_uuid UNIQUE (public_uuid),
    CONSTRAINT fk_billing_plan_rules_tenant FOREIGN KEY (tenant_id)
        REFERENCES edushift.tenants (id) ON DELETE RESTRICT,
    CONSTRAINT chk_billing_plan_rules_scope CHECK ((level_id IS NULL) <> (grade_id IS NULL)),
    CONSTRAINT chk_billing_plan_rules_amount CHECK (amount_cents > 0),
    CONSTRAINT chk_billing_plan_rules_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_billing_plan_rules_period CHECK (billing_period IN ('MONTHLY', 'ANNUAL')),
    CONSTRAINT chk_billing_plan_rules_day CHECK (billing_day_of_month BETWEEN 1 AND 31),
    CONSTRAINT chk_billing_plan_rules_month CHECK (
        (billing_period = 'MONTHLY' AND billing_month IS NULL)
        OR (billing_period = 'ANNUAL' AND billing_month BETWEEN 1 AND 12)
    ),
    CONSTRAINT chk_billing_plan_rules_deleted_consistency CHECK (
        (deleted = false AND deleted_at IS NULL) OR (deleted = true AND deleted_at IS NOT NULL)
    )
);

CREATE INDEX idx_billing_plan_rules_tenant_active
    ON edushift.billing_plan_rules (tenant_id, active, created_at DESC)
    WHERE deleted = false;
CREATE UNIQUE INDEX uk_billing_plan_rules_level_active
    ON edushift.billing_plan_rules (tenant_id, concept_id, level_id)
    WHERE deleted = false AND active = true AND level_id IS NOT NULL;
CREATE UNIQUE INDEX uk_billing_plan_rules_grade_active
    ON edushift.billing_plan_rules (tenant_id, concept_id, grade_id)
    WHERE deleted = false AND active = true AND grade_id IS NOT NULL;
CREATE TRIGGER set_updated_at_billing_plan_rules
    BEFORE UPDATE ON edushift.billing_plan_rules
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON TABLE edushift.billing_plan_rules IS
    'Tenant rules that auto-assign a recurring plan to currently and newly enrolled students in a grade or academic level.';
COMMENT ON COLUMN edushift.billing_plan_rules.concept_id IS
    'Internal payment concept id; ownership is validated through Hibernate tenant filtering in the service.';
COMMENT ON COLUMN edushift.billing_plan_rules.active IS
    'When false, stops future plan assignments; existing subscriptions continue independently.';
