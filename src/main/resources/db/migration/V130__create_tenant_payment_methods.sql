CREATE TABLE tenant_payment_methods (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    public_uuid     UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    method_type     VARCHAR(20) NOT NULL,
    display_name    VARCHAR(100),

    bank_name       VARCHAR(100),
    account_holder  VARCHAR(200),
    account_number  TEXT,
    cci             TEXT,

    wallet_phone    VARCHAR(15),
    wallet_qr_url   TEXT,

    is_active       BOOLEAN DEFAULT true,
    is_default      BOOLEAN DEFAULT false,

    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    deleted_at      TIMESTAMPTZ
);

CREATE INDEX idx_tenant_payment_methods_tenant
    ON tenant_payment_methods(tenant_id)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX idx_tenant_payment_methods_default
    ON tenant_payment_methods(tenant_id)
    WHERE is_default = true AND deleted_at IS NULL;
