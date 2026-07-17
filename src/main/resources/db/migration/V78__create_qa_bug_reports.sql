-- =============================================================================
-- V78 - QA Bug Reports table (Centro de Pruebas por Rol / F-QA-PLAN 2026-07-17)
--
-- Contexto:
--   El nuevo Centro de Pruebas (`/help` en el FE) ejecuta cada capability como
--   una secuencia de steps HTTP reales. Cuando un step falla, el usuario
--   puede reportarlo y este reporte se persiste para alimentar el backlog.
--
-- Decisiones:
--   - Tabla append-only por convención (no se hard-eliminan filas; el FE
--     usa `status` OPEN/ACKNOWLEDGED/RESOLVED + soft-delete via BaseEntity).
--   - `tenant_id` NULLABLE para soportar reportes cross-tenant emitidos por
--     SUPER_ADMIN (D4 del plan: "ejecutar calls reales desde el navegador
--     con el rol logueado"; un SA reporta sin tenant context).
--   - `actor_id` es el users.id interno (no public_uuid) — heredamos la
--     convención de audit_logs. Mapeo en service.
--   - `request` jsonb guarda el snapshot del request fallido con el
--     `Authorization: Bearer ****` (sanitizado en el FE).
--   - Audit cols estándar (BaseEntity + AuditableEntity).
--   - Dedup hint: índice por (capability_id, step_id, created_at) para
--     soportar rate-limit de 30/min/IP y dedupe por día si se quiere añadir.
-- =============================================================================

CREATE TABLE edushift.qa_bug_reports (
    id           uuid         PRIMARY KEY,
    tenant_id    uuid         NULL,
    actor_id     uuid         NOT NULL,
    capability_id varchar(200) NOT NULL,
    step_id      varchar(200) NOT NULL,
    step_label   varchar(500),
    severity     varchar(20)  NOT NULL,
    status       varchar(20)  NOT NULL DEFAULT 'OPEN',
    notes        text,
    request      jsonb,
    created_at   timestamptz  NOT NULL,
    updated_at   timestamptz  NOT NULL,
    deleted      boolean      NOT NULL DEFAULT false,
    created_by   uuid,
    updated_by   uuid
);

COMMENT ON TABLE edushift.qa_bug_reports IS
    'Bug reports creados desde el Centro de Pruebas (/help) del FE. Append-only por convención.';

COMMENT ON COLUMN edushift.qa_bug_reports.tenant_id IS
    'Tenant scope del reporte. NULL para SUPER_ADMIN cross-tenant (sin X-Tenant-Slug).';

COMMENT ON COLUMN edushift.qa_bug_reports.actor_id IS
    'users.id interno del reportador (NO public_uuid).';

COMMENT ON COLUMN edushift.qa_bug_reports.severity IS
    'BLOCKER | MAJOR | MINOR | COSMETIC';

COMMENT ON COLUMN edushift.qa_bug_reports.status IS
    'OPEN | ACKNOWLEDGED | RESOLVED';

COMMENT ON COLUMN edushift.qa_bug_reports.request IS
    'Snapshot sanitizado del request: {method, path, status, body}. Authorization header enmascarado en FE.';

-- Indexes
CREATE INDEX idx_qa_bug_reports_tenant_capability
    ON edushift.qa_bug_reports (tenant_id, capability_id)
    WHERE NOT deleted;

CREATE INDEX idx_qa_bug_reports_actor
    ON edushift.qa_bug_reports (actor_id)
    WHERE NOT deleted;

CREATE INDEX idx_qa_bug_reports_status_created
    ON edushift.qa_bug_reports (status, created_at DESC)
    WHERE NOT deleted;

-- updated_at trigger (standard convention)
CREATE TRIGGER set_updated_at_qa_bug_reports
    BEFORE UPDATE ON edushift.qa_bug_reports
    FOR EACH ROW
    EXECUTE FUNCTION edushift.set_updated_at();

-- =============================================================================
-- FIN V78
-- =============================================================================