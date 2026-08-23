-- =============================================================================
-- V91 - Fix tenant_ai_settings.default_model for MiniMax provider.
--
-- Contexto:
--   Seeds V38/V39 and early demos stored OpenRouter-style model IDs
--   (e.g. anthropic/claude-3.5-sonnet). MiniMax is now the only active
--   LLM provider and rejects those IDs with HTTP 400:
--     unknown model 'anthropic/claude-3.5-sonnet' (2013)
--
-- Decision (forward-only):
--   Rewrite legacy OpenRouter-style default_model values to MiniMax-M3,
--   matching app.llm.minimax.default-model.
-- =============================================================================

UPDATE edushift.tenant_ai_settings
   SET default_model = 'MiniMax-M3',
       updated_at = now()
 WHERE deleted = false
   AND default_model IS NOT NULL
   AND (
         default_model LIKE '%/%'
      OR default_model ILIKE 'gpt-%'
      OR default_model ILIKE 'claude-%'
   );

COMMENT ON COLUMN edushift.tenant_ai_settings.default_model IS
    'Default LLM model id for this tenant. Must be a MiniMax model id '
    '(e.g. MiniMax-M3). OpenRouter-style ids (provider/model) are invalid.';
