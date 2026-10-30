-- =============================================================================
-- V111 — recommended_levels on grade_register_templates (catalog tags by level)
-- =============================================================================

ALTER TABLE edushift.grade_register_templates
    ADD COLUMN IF NOT EXISTS recommended_levels jsonb NOT NULL DEFAULT '[]'::jsonb;

COMMENT ON COLUMN edushift.grade_register_templates.recommended_levels IS
    'Catalog hint: INICIAL | PRIMARIA | SECUNDARIA. Empty for tenant working copies.';
