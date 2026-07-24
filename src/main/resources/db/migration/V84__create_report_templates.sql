-- =============================================================================
-- V84__create_report_templates.sql
--
-- Cierre-C / B12 cierre -- Report templates catalog + cron scheduler.
--
-- Tabla `report_templates`: plantillas por tenant con schedule (cron),
-- lista de recipients (JSONB) y formato de salida. El runner
-- (ReportTemplateRunner) corre cada minuto, evalua el cron de cada
-- plantilla y dispara un `report_jobs` por cada una que toque. Cuando
-- el job termina, el runner envia el resultado a los recipients por
-- email usando `EmailSender` (Sprint 9 / BE-9.1, gated por
-- `app.notifications.email.enabled`).
--
-- Decisiones:
--   * ADR-Cierre-C.12 -- append-only con soft-delete (`deleted_at`).
--   * ADR-Cierre-C.12 -- `cron_expression` validada por la app
--     (Spring `CronExpression.parse`), no por la DB.
--   * ADR-Cierre-C.12 -- `recipients` es JSONB (array de email
--     strings o {userId, email, role} objects). El runner los aplana
--     a una lista de strings para EmailSender.send().
--   * ADR-Cierre-C.12 -- `last_run_at` + `next_run_at` son columnas
--     desnormalizadas que el runner actualiza; el `next_run_at` se
--     recalcula en save para UI feedback.
--   * ADR-Cierre-C.12 -- multi-tenant via columna `tenant_id` +
--     Hibernate `@TenantId` discriminator.
-- =============================================================================

CREATE TABLE IF NOT EXISTS edushift.report_templates (
    id                      uuid          PRIMARY KEY,
    tenant_id               uuid          NOT NULL,
    public_uuid             uuid          NOT NULL,

    -- Audit (BaseEntity contract)
    created_at              timestamptz   NOT NULL,
    updated_at              timestamptz   NOT NULL,
    created_by              uuid,
    updated_by              uuid,
    deleted                 boolean       NOT NULL DEFAULT false,
    deleted_at              timestamptz,

    -- Template identity + display
    name                    varchar(120)  NOT NULL,
    description             varchar(500),
    report_type             varchar(40)   NOT NULL,
    format                  varchar(10)   NOT NULL DEFAULT 'PDF',
    active                  boolean       NOT NULL DEFAULT true,

    -- Schedule
    cron_expression         varchar(80)   NOT NULL,
    timezone                varchar(40)   NOT NULL DEFAULT 'America/Lima',
    last_run_at             timestamptz,
    next_run_at             timestamptz,

    -- Delivery (recipients is JSONB; null = no email, only the generated file)
    recipients              jsonb         NOT NULL DEFAULT '[]'::jsonb,
    email_subject           varchar(200),
    email_body_template     text,

    -- Report params (passed through to the existing ReportJob / generator)
    params                  jsonb         NOT NULL DEFAULT '{}'::jsonb,

    CONSTRAINT uk_report_templates_public_uuid UNIQUE (public_uuid),

    CONSTRAINT chk_report_templates_report_type CHECK (
        report_type IN ('GRADE_BOOK', 'ATTENDANCE_SUMMARY', 'PERIOD_CLOSE', 'STUDENT_TRANSCRIPT')
    ),

    CONSTRAINT chk_report_templates_format CHECK (
        format IN ('PDF', 'XLSX', 'CSV')
    ),

    CONSTRAINT chk_report_templates_timezone CHECK (
        timezone ~ '^[A-Za-z][A-Za-z0-9_+\-/]*$'
    ),

    CONSTRAINT chk_report_templates_cron_nonempty CHECK (
        length(trim(cron_expression)) BETWEEN 5 AND 80
    ),

    CONSTRAINT chk_report_templates_deleted_at_consistent CHECK (
        (deleted = false AND deleted_at IS NULL)
     OR (deleted = true  AND deleted_at IS NOT NULL)
    ),

    CONSTRAINT fk_report_templates_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES edushift.tenants (id)
        ON DELETE RESTRICT
);

-- Hot path: scheduler tick (every minute) -- SELECT all active templates
-- whose `next_run_at <= now()`.
CREATE INDEX idx_report_templates_due
    ON edushift.report_templates (next_run_at)
    WHERE deleted = false AND active = true;

-- Hot path: tenant catalog list (F12 FE).
CREATE INDEX idx_report_templates_tenant_created
    ON edushift.report_templates (tenant_id, created_at DESC)
    WHERE deleted = false;

CREATE TRIGGER set_updated_at_report_templates
    BEFORE UPDATE ON edushift.report_templates
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON TABLE  edushift.report_templates                IS 'Tenant-defined recurring report templates with cron + recipients (Sprint cierre-C / B12).';
COMMENT ON COLUMN edushift.report_templates.cron_expression IS 'Spring CronExpression format (sec min hour dom mon dow). Validated by the app on save.';
COMMENT ON COLUMN edushift.report_templates.timezone        IS 'IANA timezone id, e.g. America/Lima. Default America/Lima for Peru tenants.';
COMMENT ON COLUMN edushift.report_templates.recipients     IS 'JSONB array of email strings or {userPublicUuid, email} objects. Empty array = no email, only the generated file.';
COMMENT ON COLUMN edushift.report_templates.params         IS 'JSONB pass-through to the existing ReportJob / generator (course uuid, period uuid, etc).';