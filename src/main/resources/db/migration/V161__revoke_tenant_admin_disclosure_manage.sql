-- Tenant Admins review and publish annual disclosures; Staff prepare drafts.
-- Remove existing explicit grants that would preserve Tenant Admin edit access.
-- Rollback: restore only from a pre-migration backup; do not bulk-regrant these
-- permissions because this migration intentionally removes their prior provenance.
UPDATE edushift.role_permission_overrides
SET granted = false,
    updated_at = now()
WHERE role = 'TENANT_ADMIN'
  AND authority = 'LMS_DISCLOSURE_MANAGE'
  AND granted = true
  AND deleted = false;
