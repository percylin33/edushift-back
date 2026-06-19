-- =============================================================================
-- V36 - Sprint 7c / BE-7c.1 - AI assistant module: quota settings + usage + generations
--
-- 3 tablas (todas en schema edushift, multi-tenant via tenant_id + Hibernate
-- @TenantId discriminator, mismo patron que lms_quizzes):
--
--   1. tenant_ai_settings   - Quota/feature flags por tenant (1 row por tenant).
--                              Toggle de AI assistant + limites de uso.
--   2. tenant_ai_usage      - Contadores rolled-up por dia y por tenant.
--                              Una fila por (tenant, day); upsert en cada request.
--   3. ai_generations       - Audit de cada llamada al LLM (prompt + response
--                              + tokens + status + tenant). Closes los
--                              AUDIT-AI-* requirements del ai-rules.mdc.
--
-- Decisiones:
--   * AI-QUOTA-01 - Quotas son por tenant, no por usuario. Razon: el LLM cobra
--                    por tenant (la API key es del tenant), y el cap es de
--                    plan de billing, no de user.
--   * AI-QUOTA-02 - daily_quota es un max de requests/dia. NULL = ilimitado.
--                    monthly_token_quota es el cap agregado de tokens
--                    input+output/mes (mismo NULL = ilimitado).
--   * AI-QUOTA-03 - El plan default es `ai_enabled=false` (el teacher tiene
--                    que activar AI explicito desde el panel de tenant
--                    settings). Esto es fail-safe: no se gasta dinero
--                    en LLM sin opt-in.
--   * AI-QUOTA-04 - `usage_day` es DATE (no timestamptz) para que el UPSERT
--                    por (tenant, day) sea trivial. Reset implicito a
--                    medianoche UTC.
--   * AI-AUDIT-01 - ai_generations persiste el JSON crudo de la respuesta
--                    del LLM (text + parsed). Esto permite debuggear
--                    generaciones rotas y regenerar en frio sin pagar
--                    otra vez.
--   * AI-AUDIT-02 - status sigue el enum del ai-rules.mdc:
--                    PENDING | PROCESSING | COMPLETED | FAILED | CANCELLED.
--                    MVP: todas las generaciones son synchronous (BE-7c.1),
--                    asi que PENDING/PROCESSING no se usan; el registro se
--                    inserta en COMPLETED o FAILED. Se deja el enum para
--                    cuando llegue el async en BE-7c.2.
--   * AI-AUDIT-03 - model_used, tokens_in, tokens_out, latency_ms
--                    son nullable: si la llamada falla antes de llegar al
--                    LLM (p.ej. quota exceeded), no tenemos esos datos.
--   * AI-MT-01    - tenant_id es UUID NOT NULL en las 3 tablas (patron del
--                    proyecto, via Hibernate @TenantId discriminator).
--                    Las FKs a tenants usan la columna uuid publica del
--                    tenant (patron lms_quizzes). Para ai_generations
--                    no se hace FK fisica a tenants porque romperia la
--                    tenancy: el filtro @TenantId lo aplica Hibernate en
--                    SELECT, no necesitamos una FK para aislamiento.
--   * AI-MT-02    - ai_generations.request_user_id referencia users(id) con
--                    ON DELETE SET NULL (si el user se borra, su audit
--                    trail se queda pero sin attribution).
--   * AI-MT-03    - ai_generations.idx (tenant_id, created_at desc) para
--                    el panel "Recent generations" del teacher.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. tenant_ai_settings (1 row per tenant)
-- -----------------------------------------------------------------------------
CREATE TABLE edushift.tenant_ai_settings (
    id                  uuid         PRIMARY KEY,
    public_uuid         uuid         NOT NULL,
    tenant_id           uuid         NOT NULL,
    ai_enabled          boolean      NOT NULL DEFAULT false,
    daily_request_quota int,
    monthly_token_quota bigint,
    default_model       varchar(120),
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,

    CONSTRAINT uq_tenant_ai_settings_tenant UNIQUE (tenant_id),
    CONSTRAINT uq_tenant_ai_settings_uuid   UNIQUE (public_uuid),
    CONSTRAINT fk_tenant_ai_settings_tenant
        FOREIGN KEY (tenant_id) REFERENCES edushift.tenants(public_uuid) ON DELETE CASCADE,
    CONSTRAINT chk_tenant_ai_settings_daily_quota
        CHECK (daily_request_quota IS NULL OR daily_request_quota > 0),
    CONSTRAINT chk_tenant_ai_settings_monthly_token_quota
        CHECK (monthly_token_quota IS NULL OR monthly_token_quota > 0)
);

COMMENT ON TABLE  edushift.tenant_ai_settings                              IS 'Per-tenant AI assistant settings: master enable + daily/monthly caps. 1 row per tenant (singleton).';
COMMENT ON COLUMN edushift.tenant_ai_settings.ai_enabled                  IS 'Master switch. False = AI assistant is unavailable for this tenant (fail-safe default).';
COMMENT ON COLUMN edushift.tenant_ai_settings.daily_request_quota         IS 'Max AI requests per UTC day. NULL = unlimited within monthly_token_quota.';
COMMENT ON COLUMN edushift.tenant_ai_settings.monthly_token_quota        IS 'Max tokens (input+output) per UTC month. NULL = unlimited within daily_request_quota.';
COMMENT ON COLUMN edushift.tenant_ai_settings.default_model               IS 'Override OpenRouter default model. NULL = use app.integrations.openrouter.default-model.';

CREATE INDEX idx_tenant_ai_settings_tenant ON edushift.tenant_ai_settings(tenant_id);

-- -----------------------------------------------------------------------------
-- 2. tenant_ai_usage (rolled-up counters, 1 row per tenant per UTC day)
-- -----------------------------------------------------------------------------
CREATE TABLE edushift.tenant_ai_usage (
    id                uuid         PRIMARY KEY,
    tenant_id         uuid         NOT NULL,
    usage_day         date         NOT NULL,
    request_count     int          NOT NULL DEFAULT 0,
    success_count     int          NOT NULL DEFAULT 0,
    failed_count      int          NOT NULL DEFAULT 0,
    tokens_in_total   bigint       NOT NULL DEFAULT 0,
    tokens_out_total  bigint       NOT NULL DEFAULT 0,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_tenant_ai_usage_tenant_day UNIQUE (tenant_id, usage_day),
    CONSTRAINT chk_tenant_ai_usage_request_count
        CHECK (request_count >= 0 AND success_count >= 0 AND failed_count >= 0),
    CONSTRAINT chk_tenant_ai_usage_counters_consistent
        CHECK (success_count + failed_count <= request_count)
);

COMMENT ON TABLE  edushift.tenant_ai_usage              IS 'Daily rolled-up AI usage counters. Upserted on every request. Used to enforce daily_request_quota and to power the tenant AI dashboard.';
COMMENT ON COLUMN edushift.tenant_ai_usage.usage_day    IS 'UTC date. Implicit reset at 00:00 UTC. Row only exists for days with at least 1 request.';
COMMENT ON COLUMN edushift.tenant_ai_usage.request_count IS 'Total AI requests this UTC day (success + failed + rejected by quota).';
COMMENT ON COLUMN edushift.tenant_ai_usage.success_count IS 'Subset of request_count that returned a successful LLM response.';
COMMENT ON COLUMN edushift.tenant_ai_usage.failed_count  IS 'Subset of request_count that returned FAILED (LLM error, parse error, timeout, etc).';
COMMENT ON COLUMN edushift.tenant_ai_usage.tokens_in_total IS 'Total input (prompt) tokens this UTC day. Excludes quota-rejected calls.';
COMMENT ON COLUMN edushift.tenant_ai_usage.tokens_out_total IS 'Total output (completion) tokens this UTC day. Excludes quota-rejected calls.';

CREATE INDEX idx_tenant_ai_usage_tenant_day ON edushift.tenant_ai_usage(tenant_id, usage_day DESC);

-- -----------------------------------------------------------------------------
-- 3. ai_generations (audit log of every LLM call)
-- -----------------------------------------------------------------------------
CREATE TABLE edushift.ai_generations (
    id                 uuid         PRIMARY KEY,
    public_uuid        uuid         NOT NULL,
    tenant_id          uuid         NOT NULL,
    request_user_id    uuid,
    feature            varchar(60)  NOT NULL,
    prompt_text        text         NOT NULL,
    prompt_tokens      int,
    response_text      text,
    response_parsed    jsonb,
    response_tokens    int,
    model_used         varchar(120),
    status             varchar(20)  NOT NULL,
    error_code         varchar(80),
    error_message      text,
    latency_ms         int,
    created_at         timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_ai_generations_uuid UNIQUE (public_uuid),
    CONSTRAINT fk_ai_generations_user
        FOREIGN KEY (request_user_id) REFERENCES edushift.users(id) ON DELETE SET NULL,
    CONSTRAINT chk_ai_generations_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_ai_generations_feature
        CHECK (feature IN ('QUIZ_QUESTION_SUGGEST', 'RUBRIC_SUGGEST', 'SESSION_OUTLINE_SUGGEST', 'OTHER'))
);

COMMENT ON TABLE  edushift.ai_generations             IS 'Audit log of every AI/LLM call made by EduShift. Persists raw prompt + response + parsed + tokens. Required by ai-rules.mdc (AI AUDIT RULES).';
COMMENT ON COLUMN edushift.ai_generations.feature     IS 'Which EduShift feature triggered the call. Drives grouping in the audit dashboard and allows per-feature quotas in the future.';
COMMENT ON COLUMN edushift.ai_generations.prompt_text IS 'The full prompt sent to the LLM. Includes the system prompt and the few-shot examples. Persisted for debugging and reproducibility.';
COMMENT ON COLUMN edushift.ai_generations.response_text IS 'Raw text response from the LLM. Null if the call failed before the LLM responded (e.g. quota, auth).';
COMMENT ON COLUMN edushift.ai_generations.response_parsed IS 'The structured JSON we extracted from response_text (validated against our schema). Null on FAILED.';
COMMENT ON COLUMN edushift.ai_generations.status      IS 'PENDING | PROCESSING | COMPLETED | FAILED | CANCELLED. BE-7c.1 only emits COMPLETED/FAILED (sync). Async states land in BE-7c.2.';
COMMENT ON COLUMN edushift.ai_generations.error_code  IS 'Stable code (e.g. LLM_TIMEOUT, LLM_PARSE_ERROR, AI_QUOTA_EXCEEDED) for client error mapping. Null on COMPLETED.';
COMMENT ON COLUMN edushift.ai_generations.latency_ms  IS 'Wall-clock time spent waiting for the LLM. Null if the call was rejected before the LLM was called.';

CREATE INDEX idx_ai_generations_tenant_created
    ON edushift.ai_generations(tenant_id, created_at DESC);
CREATE INDEX idx_ai_generations_tenant_feature_created
    ON edushift.ai_generations(tenant_id, feature, created_at DESC);
CREATE INDEX idx_ai_generations_tenant_user_created
    ON edushift.ai_generations(tenant_id, request_user_id, created_at DESC);
CREATE INDEX idx_ai_generations_status
    ON edushift.ai_generations(status) WHERE status IN ('PENDING', 'PROCESSING');

-- -----------------------------------------------------------------------------
-- 4. Seed the demo tenant with default (disabled) settings.
-- -----------------------------------------------------------------------------
INSERT INTO edushift.tenant_ai_settings
    (id, public_uuid, tenant_id, ai_enabled, daily_request_quota, monthly_token_quota, default_model)
SELECT gen_random_uuid(), gen_random_uuid(), t.public_uuid, false, 100, 1000000, NULL
FROM   edushift.tenants t
WHERE  t.slug = 'demo'
  AND  NOT EXISTS (
      SELECT 1 FROM edushift.tenant_ai_settings s WHERE s.tenant_id = t.public_uuid
  );
