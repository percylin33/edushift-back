-- =============================================================================
-- V127 — Keep only the standard MINEDU session template in the system catalog.
-- Tenant-owned rows (is_system = false), including copies adopted earlier,
-- stay available.
-- =============================================================================

UPDATE edushift.session_templates
SET deleted = true,
    deleted_at = COALESCE(deleted_at, NOW()),
    updated_at = NOW(),
    is_default = false
WHERE deleted = false
  AND is_system = true
  AND template_key IN ('minedu-abc', 'minedu-math');
