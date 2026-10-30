CREATE UNIQUE INDEX uq_ai_chat_sessions_id_tenant
    ON edushift.ai_chat_sessions(id, tenant_id);

CREATE TABLE edushift.ai_agent_request_keys (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES edushift.tenants(id) ON DELETE RESTRICT,
    session_id uuid NOT NULL,
    user_id uuid NOT NULL,
    idempotency_key varchar(100) NOT NULL,
    input_hash char(64) NOT NULL,
    status varchar(12) NOT NULL DEFAULT 'PROCESSING',
    assistant_public_uuid uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    updated_by uuid,
    deleted boolean NOT NULL DEFAULT false,
    deleted_at timestamptz,
    CONSTRAINT fk_ai_agent_request_session_tenant
        FOREIGN KEY (session_id, tenant_id)
        REFERENCES edushift.ai_chat_sessions(id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT uq_ai_agent_request_key
        UNIQUE (tenant_id, session_id, user_id, idempotency_key),
    CONSTRAINT chk_ai_agent_request_status
        CHECK (status IN ('PROCESSING','COMPLETED','FAILED')),
    CONSTRAINT chk_ai_agent_request_completed
        CHECK (status <> 'COMPLETED' OR assistant_public_uuid IS NOT NULL),
    CONSTRAINT chk_ai_agent_request_deleted_at
        CHECK ((deleted = false AND deleted_at IS NULL)
            OR (deleted = true AND deleted_at IS NOT NULL))
);

CREATE INDEX idx_ai_agent_request_stale
    ON edushift.ai_agent_request_keys(status, created_at)
    WHERE status = 'PROCESSING';

CREATE TRIGGER set_updated_at_ai_agent_request_keys
    BEFORE UPDATE ON edushift.ai_agent_request_keys
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();
