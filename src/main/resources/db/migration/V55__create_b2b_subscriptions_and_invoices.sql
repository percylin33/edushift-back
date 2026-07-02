-- =============================================================================
-- V55__create_b2b_subscriptions_and_invoices.sql
--
-- Sprint 15 (SUPER_ADMIN module) / BE-15.5, BE-15.6, BE-15.7, BE-15.10 —
-- Tablas B2B para gestion empresarial:
--
--   * b2b_subscriptions      suscripcion de cada tenant a EduShift
--   * b2b_invoices          facturas mensuales (cobradas por estudiante activo)
--   * b2b_payments          pagos recibidos contra una invoice
--   * admin_impersonation_log  audit log de impersonaciones de SUPER_ADMIN
--
-- Linea de negocio B2B:
--   EduShift → Colegios. NO confundir con payments (Sprint 10) que es
--   Colegio → Apoderado. Ambas conviven en schema edushift pero NO son lo mismo.
--
-- Decisiones clave:
--   * ADR-15.5: mismo schema que tenants y payments, no schema separado.
--   * ADR-15.6: facturacion por estudiante activo, contado como SNAPSHOT al
--     emitir la invoice. Si el plan cambia, las invoices ya emitidas NO cambian.
--   * Idempotencia: UNIQUE (subscription_id, period_start, period_end) evita
--     duplicados si el cron de emision se re-ejecuta.
--   * Optimistic locking: columna `version` en cada tabla.
-- =============================================================================

-- =============================================================================
-- 1. b2b_subscriptions
-- =============================================================================
CREATE TABLE IF NOT EXISTS edushift.b2b_subscriptions (
    id                       uuid           PRIMARY KEY,
    public_uuid              uuid           NOT NULL,
    tenant_id                uuid           NOT NULL,
    plan_id                  uuid           NOT NULL,
    status                   varchar(20)    NOT NULL DEFAULT 'ACTIVE',
    current_period_start     date           NOT NULL,
    current_period_end       date           NOT NULL,
    trial_ends_at            date,
    cancel_at_period_end     boolean        NOT NULL DEFAULT false,
    cancelled_at             timestamptz,
    cancellation_reason      varchar(200),
    next_billing_at          date           NOT NULL,
    version                  bigint         NOT NULL DEFAULT 0,
    created_at               timestamptz    NOT NULL,
    updated_at               timestamptz    NOT NULL,
    created_by               uuid,
    updated_by               uuid,
    deleted                  boolean        NOT NULL DEFAULT false,
    deleted_at               timestamptz,

    CONSTRAINT uk_b2b_subscriptions_public_uuid UNIQUE (public_uuid),
    -- Un tenant solo puede tener una suscripcion activa (modelo simple)
    CONSTRAINT uk_b2b_subscriptions_tenant UNIQUE (tenant_id),
    CONSTRAINT fk_b2b_subscriptions_tenant
        FOREIGN KEY (tenant_id) REFERENCES edushift.tenants(id) ON DELETE RESTRICT,
    CONSTRAINT fk_b2b_subscriptions_plan
        FOREIGN KEY (plan_id) REFERENCES edushift.platform_plans(id) ON DELETE RESTRICT,
    CONSTRAINT chk_b2b_subscriptions_status
        CHECK (status IN ('TRIAL', 'ACTIVE', 'PAST_DUE', 'CANCELED', 'EXPIRED')),
    CONSTRAINT chk_b2b_subscriptions_period
        CHECK (current_period_end >= current_period_start)
);

COMMENT ON TABLE edushift.b2b_subscriptions IS
    'Suscripcion SaaS B2B de un tenant a EduShift (Sprint 15 / BE-15.5). '
    'Un tenant solo tiene una suscripcion activa a la vez.';

CREATE INDEX idx_b2b_subscriptions_tenant_status
    ON edushift.b2b_subscriptions (tenant_id, status)
    WHERE deleted = false;

CREATE INDEX idx_b2b_subscriptions_status_next_billing
    ON edushift.b2b_subscriptions (status, next_billing_at)
    WHERE deleted = false AND status IN ('ACTIVE', 'TRIAL');

CREATE INDEX idx_b2b_subscriptions_plan
    ON edushift.b2b_subscriptions (plan_id)
    WHERE deleted = false;

CREATE TRIGGER set_updated_at_b2b_subscriptions
    BEFORE UPDATE ON edushift.b2b_subscriptions
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

-- =============================================================================
-- 2. b2b_invoices
-- =============================================================================
CREATE TABLE IF NOT EXISTS edushift.b2b_invoices (
    id                          uuid           PRIMARY KEY,
    public_uuid                 uuid           NOT NULL,
    tenant_id                   uuid           NOT NULL,
    subscription_id             uuid           NOT NULL,
    period_start                date           NOT NULL,
    period_end                  date           NOT NULL,
    active_student_count        int            NOT NULL DEFAULT 0,
    price_per_student_cents     int            NOT NULL,
    subtotal_cents              int            NOT NULL,
    discount_cents              int            NOT NULL DEFAULT 0,
    total_cents                 int            NOT NULL,
    status                      varchar(20)    NOT NULL DEFAULT 'PENDING',
    issued_at                   timestamptz    NOT NULL,
    due_at                      date           NOT NULL,
    paid_at                     timestamptz,
    notes                       varchar(500),
    version                     bigint         NOT NULL DEFAULT 0,
    created_at                  timestamptz    NOT NULL,
    updated_at                  timestamptz    NOT NULL,
    created_by                  uuid,
    updated_by                  uuid,
    deleted                     boolean        NOT NULL DEFAULT false,
    deleted_at                  timestamptz,

    CONSTRAINT uk_b2b_invoices_public_uuid UNIQUE (public_uuid),
    -- Idempotencia: una sola invoice por (subscription, periodo)
    CONSTRAINT uk_b2b_invoices_subscription_period
        UNIQUE (subscription_id, period_start, period_end),
    CONSTRAINT fk_b2b_invoices_tenant
        FOREIGN KEY (tenant_id) REFERENCES edushift.tenants(id) ON DELETE RESTRICT,
    CONSTRAINT fk_b2b_invoices_subscription
        FOREIGN KEY (subscription_id) REFERENCES edushift.b2b_subscriptions(id) ON DELETE RESTRICT,
    CONSTRAINT chk_b2b_invoices_status
        CHECK (status IN ('PENDING', 'PAID', 'OVERDUE', 'CANCELLED', 'REFUNDED')),
    CONSTRAINT chk_b2b_invoices_amounts
        CHECK (subtotal_cents >= 0 AND discount_cents >= 0 AND total_cents >= 0),
    CONSTRAINT chk_b2b_invoices_period
        CHECK (period_end >= period_start),
    CONSTRAINT chk_b2b_invoices_student_count
        CHECK (active_student_count >= 0)
);

COMMENT ON TABLE edushift.b2b_invoices IS
    'Facturas B2B emitidas por EduShift a cada tenant (Sprint 15 / BE-15.6). '
    'Snapshots: price_per_student_cents y active_student_count se congelan al emitir.';
COMMENT ON COLUMN edushift.b2b_invoices.active_student_count IS
    'SNAPSHOT: numero de estudiantes activos al momento de emitir. '
    'No se recalcula — eso garantiza que cambios retroactivos no afecten facturas ya emitidas.';
COMMENT ON COLUMN edushift.b2b_invoices.price_per_student_cents IS
    'SNAPSHOT: precio por estudiante del plan al momento de emitir.';

CREATE INDEX idx_b2b_invoices_tenant_status
    ON edushift.b2b_invoices (tenant_id, status)
    WHERE deleted = false;

CREATE INDEX idx_b2b_invoices_status_due_at
    ON edushift.b2b_invoices (status, due_at)
    WHERE deleted = false;

CREATE INDEX idx_b2b_invoices_issued_at
    ON edushift.b2b_invoices (issued_at)
    WHERE deleted = false;

CREATE INDEX idx_b2b_invoices_subscription
    ON edushift.b2b_invoices (subscription_id)
    WHERE deleted = false;

CREATE TRIGGER set_updated_at_b2b_invoices
    BEFORE UPDATE ON edushift.b2b_invoices
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

-- =============================================================================
-- 3. b2b_payments
-- =============================================================================
CREATE TABLE IF NOT EXISTS edushift.b2b_payments (
    id              uuid           PRIMARY KEY,
    public_uuid     uuid           NOT NULL,
    invoice_id      uuid           NOT NULL,
    tenant_id       uuid           NOT NULL,
    amount_cents    int            NOT NULL,
    payment_method  varchar(20)    NOT NULL,
    status          varchar(20)    NOT NULL DEFAULT 'APPROVED',
    external_ref    varchar(100),
    paid_at         timestamptz,
    notes           varchar(500),
    version         bigint         NOT NULL DEFAULT 0,
    created_at      timestamptz    NOT NULL,
    updated_at      timestamptz    NOT NULL,
    created_by      uuid,
    updated_by      uuid,
    deleted         boolean        NOT NULL DEFAULT false,
    deleted_at      timestamptz,

    CONSTRAINT uk_b2b_payments_public_uuid UNIQUE (public_uuid),
    -- Idempotencia: external_ref (referencia de transferencia/cheque) debe ser unica
    CONSTRAINT uk_b2b_payments_external_ref
        UNIQUE (tenant_id, external_ref),
    CONSTRAINT fk_b2b_payments_invoice
        FOREIGN KEY (invoice_id) REFERENCES edushift.b2b_invoices(id) ON DELETE RESTRICT,
    CONSTRAINT fk_b2b_payments_tenant
        FOREIGN KEY (tenant_id) REFERENCES edushift.tenants(id) ON DELETE RESTRICT,
    CONSTRAINT chk_b2b_payments_method
        CHECK (payment_method IN ('TRANSFER', 'CASH', 'DEBIT', 'MERCADOPAGO')),
    CONSTRAINT chk_b2b_payments_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'REFUNDED')),
    CONSTRAINT chk_b2b_payments_amount_positive
        CHECK (amount_cents > 0)
);

COMMENT ON TABLE edushift.b2b_payments IS
    'Pagos B2B registrados contra una invoice (Sprint 15 / BE-15.7). '
    'Puede haber multiples pagos (pagos parciales) por invoice. '
    'Una invoice pasa a PAID cuando la suma de APPROVED >= total_cents.';

CREATE INDEX idx_b2b_payments_invoice
    ON edushift.b2b_payments (invoice_id)
    WHERE deleted = false;

CREATE INDEX idx_b2b_payments_tenant_status
    ON edushift.b2b_payments (tenant_id, status)
    WHERE deleted = false;

CREATE TRIGGER set_updated_at_b2b_payments
    BEFORE UPDATE ON edushift.b2b_payments
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

-- =============================================================================
-- 4. admin_impersonation_log — audit de impersonaciones
-- =============================================================================
CREATE TABLE IF NOT EXISTS edushift.admin_impersonation_log (
    id               uuid           PRIMARY KEY,
    public_uuid      uuid           NOT NULL,
    admin_id         uuid           NOT NULL,                  -- SUPER_ADMIN que impersona
    target_user_id   uuid           NOT NULL,                  -- usuario impersonado
    tenant_id        uuid           NOT NULL,                  -- tenant del target
    action           varchar(100)   NOT NULL,                  -- 'POST /admin/payments'
    path             varchar(200)   NOT NULL,
    method           varchar(10)    NOT NULL,
    ip               inet,
    user_agent       varchar(500),
    impersonated_at  timestamptz    NOT NULL,
    duration_ms      int            NOT NULL,
    created_at       timestamptz    NOT NULL,

    -- Sin FK a tenants (cross-tenant by definition). El tenant_id es solo metadato.
    -- Sin FK a users porque podrian borrarse. Conservamos el id historico.
    CONSTRAINT uk_admin_impersonation_public_uuid UNIQUE (public_uuid),
    CONSTRAINT chk_admin_impersonation_method
        CHECK (method IN ('GET', 'POST', 'PUT', 'PATCH', 'DELETE'))
);

COMMENT ON TABLE edushift.admin_impersonation_log IS
    'Audit log de cada request hecho por SUPER_ADMIN impersonando a un usuario '
    '(Sprint 15 / BE-15.10). Irrefutable: cada accion queda registrada con admin + target + '
    'path + timestamp.';

CREATE INDEX idx_admin_impersonation_admin_at
    ON edushift.admin_impersonation_log (admin_id, impersonated_at DESC);

CREATE INDEX idx_admin_impersonation_target_at
    ON edushift.admin_impersonation_log (target_user_id, impersonated_at DESC);

CREATE INDEX idx_admin_impersonation_tenant_at
    ON edushift.admin_impersonation_log (tenant_id, impersonated_at DESC);

-- Esta tabla NO usa AuditableEntity (es append-only); no necesita set_updated_at.
