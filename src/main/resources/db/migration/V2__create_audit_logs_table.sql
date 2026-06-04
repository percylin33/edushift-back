-- =============================================================================
-- V2__create_audit_logs_table.sql
-- Append-only audit trail for the activity timeline.
-- Owned by the audit module; written by all modules via AuditEvent.
-- =============================================================================

CREATE TABLE edushift.audit_logs (
    id            uuid        PRIMARY KEY,
    tenant_id     uuid,
    actor_id      uuid,
    action        varchar(50) NOT NULL,
    resource_type varchar(100),
    resource_id   uuid,
    summary       text,
    metadata      jsonb,
    trace_id      varchar(64),
    occurred_at   timestamptz NOT NULL,
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL,
    deleted       boolean     NOT NULL DEFAULT false
);

COMMENT ON TABLE  edushift.audit_logs IS 'Append-only audit trail (activity timeline)';
COMMENT ON COLUMN edushift.audit_logs.tenant_id IS 'Tenant scope; null for system-wide events';
COMMENT ON COLUMN edushift.audit_logs.actor_id  IS 'User who performed the action; null for system';
COMMENT ON COLUMN edushift.audit_logs.metadata  IS 'Structured payload (diffs, IP, UA, custom fields)';

CREATE INDEX idx_audit_logs_tenant_time   ON edushift.audit_logs (tenant_id, occurred_at DESC);
CREATE INDEX idx_audit_logs_actor_time    ON edushift.audit_logs (actor_id, occurred_at DESC);
CREATE INDEX idx_audit_logs_resource      ON edushift.audit_logs (resource_type, resource_id);
CREATE INDEX idx_audit_logs_action        ON edushift.audit_logs (action);
CREATE INDEX idx_audit_logs_trace         ON edushift.audit_logs (trace_id);
CREATE INDEX idx_audit_logs_metadata_gin  ON edushift.audit_logs USING GIN (metadata);

CREATE TRIGGER set_updated_at_audit_logs
    BEFORE UPDATE ON edushift.audit_logs
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();
