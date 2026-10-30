ALTER TABLE edushift.ai_chat_sessions
    ADD COLUMN agent_type varchar(12),
    ADD COLUMN subject_public_uuid uuid;

ALTER TABLE edushift.ai_chat_sessions
    ADD CONSTRAINT chk_ai_chat_agent_type CHECK (
        agent_type IS NULL OR agent_type IN ('TUTOR','TEACHER','FAMILY')),
    ADD CONSTRAINT chk_ai_chat_agent_subject CHECK (
        (agent_type = 'FAMILY' AND subject_public_uuid IS NOT NULL)
        OR agent_type IS DISTINCT FROM 'FAMILY');

CREATE INDEX idx_ai_chat_sessions_agent
    ON edushift.ai_chat_sessions(tenant_id, user_id, agent_type, updated_at DESC)
    WHERE deleted = false;

COMMENT ON COLUMN edushift.ai_chat_sessions.agent_type IS
    'Sprint 21a assistant type; NULL identifies a legacy teacher chat.';
COMMENT ON COLUMN edushift.ai_chat_sessions.subject_public_uuid IS
    'Student UUID for tutor or linked child for family; validated on every message.';
