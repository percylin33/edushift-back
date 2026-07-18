-- =============================================================================
-- V65__create_ai_prompts.sql
--
-- Sprint 18 / BE-18.5 — AI prompt management.
--
-- Schema decisions:
-- * `template_key` (varchar 64) is a stable identifier per use case
--   (e.g. "session-generator", "rubric-generator",
--   "quiz-question-generator"). The system always uses the "active"
--   row for a given key when building LLM requests.
-- * `version` (varchar 16) is bumped whenever the system prompt text
--   changes. We log it on every ai_generations row so post-mortems
--   can correlate prompt versions with output quality.
-- * `system_prompt` and `user_prompt_template` are CLOB. The user
--   template is rendered with named placeholders
--   (e.g. courseName, durationMinutes).
-- * `is_active` (boolean) is the source of truth at runtime. Older
--   versions of the same template_key stay in the table for audit
--   (and so the FE can show "you are running prompt v3, current is v4").
-- * Tenant scope: every prompt is system-wide (no tenant_id column).
--   Custom per-tenant prompts are explicitly out of scope for MVP
--   and added in Sprint 10+ as a per-tenant override table.
-- * Audit columns: created_at / updated_at via the standard
--   AuditableEntity mixin.
-- * Unique constraint: (template_key, version) so the BE rejects
--   duplicate version uploads.
-- =============================================================================

CREATE TABLE IF NOT EXISTS edushift.ai_prompts (
    id                   uuid         PRIMARY KEY,
    public_uuid          uuid         NOT NULL,
    template_key         varchar(64)  NOT NULL,
    version              varchar(16)  NOT NULL,
    description          varchar(500),
    system_prompt        text         NOT NULL,
    user_prompt_template text         NOT NULL,
    is_active            boolean      NOT NULL DEFAULT false,
    created_at           timestamptz  NOT NULL DEFAULT now(),
    updated_at           timestamptz  NOT NULL DEFAULT now(),
    deleted              boolean      NOT NULL DEFAULT false,
    deleted_at           timestamptz,

    CONSTRAINT uk_ai_prompts_public_uuid UNIQUE (public_uuid),
    CONSTRAINT uk_ai_prompts_key_version  UNIQUE (template_key, version)
);

-- Only one ACTIVE row per template_key. Implemented via a partial
-- unique index that excludes soft-deleted rows. The BE keeps this
-- invariant in code (a save that flips is_active=true atomically
-- flips the previous one to false inside a transaction).
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_prompts_active_key
    ON edushift.ai_prompts (template_key)
    WHERE is_active = true AND deleted = false;

-- Fast lookup by (key, is_active) when the BE builds an LLM request.
-- The LLM hot path does this on every generation.
CREATE INDEX IF NOT EXISTS ix_ai_prompts_key_active
    ON edushift.ai_prompts (template_key, is_active)
    WHERE deleted = false;

-- Seed the three current prompt versions. The hard-coded text in
-- the prompt builders (SessionGeneratorPromptBuilder,
-- RubricGeneratorPromptBuilder, QuizQuestionPromptBuilder) is the
-- source of truth — if the seed below drifts, regenerate it.
--
-- NOTE: User-prompt templates embed runtime placeholders that look
-- like Flyway placeholders (e.g. "topic"). Flyway's parser scans
-- every SQL statement for placeholder patterns and fails when it cannot
-- resolve them. To avoid the false positive we use PostgreSQL
-- dollar-quoted strings ($$ ... $$), which Flyway treats as opaque
-- blobs and skips scanning.
INSERT INTO edushift.ai_prompts
    (id, public_uuid, template_key, version, description, system_prompt, user_prompt_template, is_active)
VALUES
     (gen_random_uuid(), gen_random_uuid(),
      'session-generator', 'v1',
      'Initial seed — generated lesson outline (sprint-generator/v1).',
      'You are an expert pedagogy assistant...',
      $$Generate a session outline for topic "$${topic}"...$$,
      true),
     (gen_random_uuid(), gen_random_uuid(),
      'rubric-generator', 'v1',
      'Initial seed — rubric generation (rubric-generator/v1).',
      'You are an expert assessment designer...',
      $$Generate rubric criteria for "$${title}"...$$,
      true),
     (gen_random_uuid(), gen_random_uuid(),
      'quiz-question-generator', 'v1',
      'Initial seed — quiz question generation (quiz-question/v1).',
      'You are an expert quiz designer...',
      $$Generate quiz questions for topic "$${topic}"...$$,
      true);
