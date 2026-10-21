CREATE TABLE edushift.ai_token_reservations (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES edushift.tenants(public_uuid) ON DELETE RESTRICT,
    usage_day date NOT NULL,
    reserved_tokens bigint NOT NULL,
    actual_tokens bigint NOT NULL DEFAULT 0,
    status varchar(12) NOT NULL DEFAULT 'RESERVED',
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    updated_by uuid,
    deleted boolean NOT NULL DEFAULT false,
    deleted_at timestamptz,
    CONSTRAINT chk_ai_token_reservation_tokens CHECK
        (reserved_tokens > 0 AND actual_tokens >= 0),
    CONSTRAINT chk_ai_token_reservation_status CHECK
        (status IN ('RESERVED','SETTLED','EXPIRED')),
    CONSTRAINT chk_ai_token_reservation_deleted_at CHECK
        ((deleted = false AND deleted_at IS NULL)
         OR (deleted = true AND deleted_at IS NOT NULL))
);

CREATE INDEX idx_ai_token_reservation_active
    ON edushift.ai_token_reservations(tenant_id, usage_day, expires_at)
    WHERE status = 'RESERVED';

CREATE TRIGGER set_updated_at_ai_token_reservations
    BEFORE UPDATE ON edushift.ai_token_reservations
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();
