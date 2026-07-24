-- =============================================================================
-- V82__create_payment_concepts.sql
--
-- Cierre-A / B5 part 1 — Payment concepts (tenant-defined billing catalog).
--
-- Tabla `payment_concepts`: catálogo de conceptos cobrables definidos por
-- cada tenant (ej. "Matrícula 2026", "Cuota Marzo", "Materiales",
-- "Refrigerio", "Examen de recuperación"). Los admins emiten facturas
-- seleccionando 1+ conceptos; cada concepto se materializa como una fila
-- en `invoice_items` con su `unit_amount_cents` y `quantity` resueltos en
-- ese momento (snapshot).
--
-- Decisiones:
--   * ADR-Cierre-A.5 — append-only con soft-delete (`deleted_at`). Un
--     concepto NO se elimina duro para preservar auditabilidad de las
--     facturas históricas que lo referencian.
--   * ADR-Cierre-A.5 — `code` único por tenant (no global). Permite
--     que dos tenants tengan "MATRICULA" sin colisionar.
--   * ADR-Cierre-A.5 — `default_amount_cents` snapshot al momento de
--     emitir; NO se propaga retroactivamente a facturas ya emitidas.
--   * ADR-Cierre-A.5 — multi-tenant via columna `tenant_id` +
--     Hibernate `@TenantId` discriminator.
-- =============================================================================

CREATE TABLE IF NOT EXISTS edushift.payment_concepts (
    id                          uuid          PRIMARY KEY,
    tenant_id                   uuid          NOT NULL,
    public_uuid                 uuid          NOT NULL,
    created_at                  timestamptz   NOT NULL,
    updated_at                  timestamptz   NOT NULL,
    created_by                  uuid,
    updated_by                  uuid,
    deleted                     boolean       NOT NULL DEFAULT false,
    deleted_at                  timestamptz,

    code                        varchar(40)   NOT NULL,
    name                        varchar(120)  NOT NULL,
    description                 varchar(500),
    category                    varchar(40)   NOT NULL,
    default_amount_cents        bigint        NOT NULL,
    currency                    varchar(3)    NOT NULL DEFAULT 'PEN',
    is_recurring                boolean       NOT NULL DEFAULT false,
    is_active                   boolean       NOT NULL DEFAULT true,
    sort_order                  integer       NOT NULL DEFAULT 0,

    CONSTRAINT uk_payment_concepts_public_uuid
        UNIQUE (public_uuid),

    CONSTRAINT uk_payment_concepts_tenant_code
        UNIQUE (tenant_id, code) WHERE NOT deleted,

    CONSTRAINT chk_payment_concepts_amount_positive
        CHECK (default_amount_cents >= 0),

    CONSTRAINT chk_payment_concepts_currency_iso
        CHECK (currency ~ '^[A-Z]{3}$'),

    CONSTRAINT chk_payment_concepts_category
        CHECK (category IN (
            'TUITION',
            'ENROLLMENT',
            'MATERIALS',
            'MEALS',
            'TRANSPORT',
            'EXAM',
            'EVENT',
            'OTHER'
        )),

    CONSTRAINT chk_payment_concepts_deleted_at_consistent
        CHECK (
            (deleted = false AND deleted_at IS NULL)
         OR (deleted = true  AND deleted_at IS NOT NULL)
        ),

    CONSTRAINT fk_payment_concepts_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES edushift.tenants (id)
        ON DELETE RESTRICT
);

-- Hot path: GET /admin/payments/concepts (lista del tenant para el dropdown
-- del invoice composer).
CREATE INDEX idx_payment_concepts_tenant_active
    ON edushift.payment_concepts (tenant_id, is_active, sort_order)
    WHERE deleted = false;

CREATE TRIGGER set_updated_at_payment_concepts
    BEFORE UPDATE ON edushift.payment_concepts
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON TABLE  edushift.payment_concepts                       IS 'Tenant-defined billing concepts catalog (Sprint cierre-A / B5).';
COMMENT ON COLUMN edushift.payment_concepts.code                  IS 'Stable short code per tenant (e.g. MATRICULA, CUOTA_MAR). Unique within tenant.';
COMMENT ON COLUMN edushift.payment_concepts.category              IS 'TUITION | ENROLLMENT | MATERIALS | MEALS | TRANSPORT | EXAM | EVENT | OTHER.';
COMMENT ON COLUMN edushift.payment_concepts.default_amount_cents  IS 'Default unit amount in centavos. Snapshot at invoice-emit time.';
COMMENT ON COLUMN edushift.payment_concepts.is_recurring          IS 'Hint for invoice cron — does NOT auto-generate invoices yet.';