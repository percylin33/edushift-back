-- =============================================================================
-- V121 — Session templates: family + source + is_default (organized catalog)
-- =============================================================================
-- Enables per-tenant organized catalog (MINEDU families + custom), adopt tracking,
-- and a single default template per tenant for session create preselection.
-- =============================================================================

ALTER TABLE edushift.session_templates
    ADD COLUMN IF NOT EXISTS family VARCHAR(40);

ALTER TABLE edushift.session_templates
    ADD COLUMN IF NOT EXISTS source_template_uuid UUID;

ALTER TABLE edushift.session_templates
    ADD COLUMN IF NOT EXISTS is_default BOOLEAN NOT NULL DEFAULT false;

-- Backfill family from template_key (system seed + any custom rows).
UPDATE edushift.session_templates
SET family = CASE
    WHEN template_key = 'minedu-standard' THEN 'STANDARD'
    WHEN template_key = 'minedu-abc' THEN 'COMMUNICATION'
    WHEN template_key = 'minedu-math' THEN 'MATH'
    WHEN template_key LIKE 'minedu-standard%' THEN 'STANDARD'
    WHEN template_key LIKE 'minedu-abc%' THEN 'COMMUNICATION'
    WHEN template_key LIKE 'minedu-math%' THEN 'MATH'
    ELSE 'CUSTOM'
END
WHERE family IS NULL;

ALTER TABLE edushift.session_templates
    ALTER COLUMN family SET NOT NULL;

ALTER TABLE edushift.session_templates
    DROP CONSTRAINT IF EXISTS chk_session_templates_family;

ALTER TABLE edushift.session_templates
    ADD CONSTRAINT chk_session_templates_family CHECK (
        family IN ('STANDARD', 'COMMUNICATION', 'MATH', 'CUSTOM')
    );

CREATE UNIQUE INDEX IF NOT EXISTS uk_session_templates_tenant_default_active
    ON edushift.session_templates (tenant_id)
    WHERE deleted = false AND is_default = true;

CREATE INDEX IF NOT EXISTS idx_session_templates_tenant_family
    ON edushift.session_templates (tenant_id, family)
    WHERE deleted = false;

COMMENT ON COLUMN edushift.session_templates.family IS
    'Organization group: STANDARD | COMMUNICATION | MATH | CUSTOM';
COMMENT ON COLUMN edushift.session_templates.source_template_uuid IS
    'public_uuid of the catalog/system template this row was adopted from';
COMMENT ON COLUMN edushift.session_templates.is_default IS
    'Single default per tenant for session-create preselection';
