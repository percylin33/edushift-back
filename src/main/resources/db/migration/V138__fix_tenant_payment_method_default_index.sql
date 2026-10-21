DROP INDEX IF EXISTS edushift.idx_tenant_payment_methods_default;

CREATE UNIQUE INDEX idx_tenant_payment_methods_default
    ON edushift.tenant_payment_methods (tenant_id)
    WHERE is_default = true AND deleted = false;