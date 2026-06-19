-- =============================================================================
-- V42 - Sprint 8 / BE-8.3 - AI Chat sessions + messages (memory in-session).
--
-- 2 tablas nuevas en schema edushift, multi-tenant via tenant_id + Hibernate
-- @TenantId discriminator (mismo patron que ai_generations):
--
--   1. ai_chat_sessions   - Una conversacion de chat IA. Vive 7 dias (TTL
--                            hard-coded en ChatSessionSweeper job). Una por
--                            (user, "thread"); si el user crea otra, se le
--                            ofrece "nueva conversacion".
--                            ADR-8.1: memory in-session only; cross-session
--                            queda para v2 (reagendado Sprint 10+).
--   2. ai_chat_messages   - Los mensajes user/assistant. Lleva parent_uuid
--                            para threading futuro (no usado en MVP). Lleva
--                            status (PENDING/STREAMING/COMPLETED/FAILED) +
--                            tokens + latency para billing/audit.
--
-- Decisiones:
--   * CHAT-MT-01 - tenant_id UUID NOT NULL en ambas tablas. Mismo patron que
--                   ai_generations (Hibernate @TenantId lo aplica en SELECT).
--   * CHAT-MT-02 - ai_chat_sessions.user_id FK a users(id) ON DELETE CASCADE
--                   (si el user se borra, su chat se va con el).
--   * CHAT-MT-03 - ai_chat_messages.chat_session_id FK a ai_chat_sessions(id)
--                   ON DELETE CASCADE (borrar sesion borra sus mensajes).
--   * CHAT-MT-04 - role es 'user' | 'assistant' | 'system' (este ultimo solo
--                   para inyeccion de contexto del tenant; el user nunca lo ve).
--   * CHAT-MT-05 - status del mensaje sigue enum del ai-rules.mdc:
--                   PENDING | STREAMING | COMPLETED | FAILED | CANCELLED.
--                   STREAMING es nuevo para chat (no existia en
--                   ai_generations).
--   * CHAT-PERF-01 - idx (chat_session_id, created_at) para paginar mensajes
--                    de una sesion en orden cronologico.
--   * CHAT-PERF-02 - idx (tenant_id, user_id, updated_at desc) en sessions
--                    para "listar mis conversaciones recientes" en el FE.
--   * CHAT-TTL-01 - expires_at en sessions; NULL = "no expira todavia" pero el
--                   sweeper igual limpia sesiones > 7 dias.
--   * CHAT-AUDIT-01 - Cada mensaje assistant persiste tokens_in/tokens_out/
--                    latency_ms para billing; el system prompt (invisible al
--                    user) NO se persiste contenido del LLM, solo metadata.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. ai_chat_sessions
-- -----------------------------------------------------------------------------
CREATE TABLE edushift.ai_chat_sessions (
    id                  uuid         PRIMARY KEY,
    tenant_id           uuid         NOT NULL,
    public_uuid         uuid         NOT NULL,
    user_id             uuid         NOT NULL,
    title               varchar(200) NOT NULL DEFAULT 'Nueva conversacion',
    status              varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    message_count       integer      NOT NULL DEFAULT 0,
    total_tokens_in     integer      NOT NULL DEFAULT 0,
    total_tokens_out    integer      NOT NULL DEFAULT 0,
    last_message_at     timestamptz,
    expires_at          timestamptz,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    deleted             boolean      NOT NULL DEFAULT false,
    deleted_at          timestamptz,
    CONSTRAINT fk_chat_sessions_user
        FOREIGN KEY (user_id) REFERENCES edushift.users(id) ON DELETE CASCADE,
    CONSTRAINT chk_chat_sessions_status
        CHECK (status IN ('ACTIVE', 'ARCHIVED', 'DELETED')),
    CONSTRAINT chk_chat_sessions_deleted_at
        CHECK ((deleted = false AND deleted_at IS NULL) OR (deleted = true AND deleted_at IS NOT NULL))
);

CREATE UNIQUE INDEX uk_ai_chat_sessions_public_uuid
    ON edushift.ai_chat_sessions (public_uuid);

CREATE INDEX idx_ai_chat_sessions_tenant_user_updated
    ON edushift.ai_chat_sessions (tenant_id, user_id, updated_at DESC)
    WHERE deleted = false;

CREATE INDEX idx_ai_chat_sessions_tenant_expires
    ON edushift.ai_chat_sessions (tenant_id, expires_at)
    WHERE deleted = false;

COMMENT ON TABLE  edushift.ai_chat_sessions
    IS 'AI chat sessions (BE-8.3). Multi-tenant via tenant_id + Hibernate @TenantId. TTL 7d via ChatSessionSweeper.';
COMMENT ON COLUMN edushift.ai_chat_sessions.title
    IS 'Auto-generated from first user message (truncated 200 chars). User can rename.';
COMMENT ON COLUMN edushift.ai_chat_sessions.expires_at
    IS 'TTL marker. NULL until set by sweeper (default +7d from last_message_at).';

-- -----------------------------------------------------------------------------
-- 2. ai_chat_messages
-- -----------------------------------------------------------------------------
CREATE TABLE edushift.ai_chat_messages (
    id                  uuid         PRIMARY KEY,
    tenant_id           uuid         NOT NULL,
    public_uuid         uuid         NOT NULL,
    chat_session_id     uuid         NOT NULL,
    role                varchar(20)  NOT NULL,
    content             text         NOT NULL,
    status              varchar(20)  NOT NULL DEFAULT 'COMPLETED',
    parent_message_id   uuid,
    model_used          varchar(100),
    prompt_tokens       integer,
    response_tokens     integer,
    latency_ms          integer,
    error_code          varchar(50),
    error_message       varchar(2000),
    input_hash          varchar(64),
    output_hash         varchar(64),
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    deleted             boolean      NOT NULL DEFAULT false,
    deleted_at          timestamptz,
    CONSTRAINT fk_chat_messages_session
        FOREIGN KEY (chat_session_id) REFERENCES edushift.ai_chat_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_messages_parent
        FOREIGN KEY (parent_message_id) REFERENCES edushift.ai_chat_messages(id) ON DELETE SET NULL,
    CONSTRAINT chk_chat_messages_role
        CHECK (role IN ('user', 'assistant', 'system')),
    CONSTRAINT chk_chat_messages_status
        CHECK (status IN ('PENDING', 'STREAMING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_chat_messages_deleted_at
        CHECK ((deleted = false AND deleted_at IS NULL) OR (deleted = true AND deleted_at IS NOT NULL))
);

CREATE UNIQUE INDEX uk_ai_chat_messages_public_uuid
    ON edushift.ai_chat_messages (public_uuid);

CREATE INDEX idx_ai_chat_messages_session_created
    ON edushift.ai_chat_messages (chat_session_id, created_at)
    WHERE deleted = false;

CREATE INDEX idx_ai_chat_messages_tenant_user_recent
    ON edushift.ai_chat_messages (tenant_id, chat_session_id, created_at DESC)
    WHERE deleted = false;

-- SEC-8.1: hash columns for abuse detection. SHA-256 hex of the
-- post-mask input / output text. Indexed for fast "have we seen
-- this input before?" queries from the abuse dashboard.
CREATE INDEX idx_ai_chat_messages_input_hash
    ON edushift.ai_chat_messages (input_hash)
    WHERE deleted = false AND input_hash IS NOT NULL;

-- Also extend ai_generations (BE-7c.1 table) with the same columns.
ALTER TABLE edushift.ai_generations
    ADD COLUMN input_hash  varchar(64),
    ADD COLUMN output_hash varchar(64);

CREATE INDEX idx_ai_generations_input_hash
    ON edushift.ai_generations (input_hash)
    WHERE deleted = false AND input_hash IS NOT NULL;

COMMENT ON TABLE  edushift.ai_chat_messages
    IS 'AI chat messages (BE-8.3). Belongs to ai_chat_sessions. role=user/assistant/system.';
COMMENT ON COLUMN edushift.ai_chat_messages.content
    IS 'Text content. For system messages this is the injected tenant context (cap 4KB per ADR-8.4).';
COMMENT ON COLUMN edushift.ai_chat_messages.parent_message_id
    IS 'Threading hook (unused in MVP v1). Reserved for v2 branches.';
