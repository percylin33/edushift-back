-- =============================================================================
-- V158__enforce_unique_tenant_hostname_aliases.sql
-- Ensure each tenant's local hostname alias (slug with dashes removed) is unique.
-- This keeps legacy slugs usable while new tenants use dash-free slugs.
-- =============================================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM edushift.tenants
        WHERE deleted = false
          AND char_length(translate(slug, '-', '')) > 63
    ) THEN
        RAISE EXCEPTION 'Tenant hostname aliases exceed the 63-character DNS label limit; shorten the affected slugs before applying V158';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM edushift.tenants
        WHERE deleted = false
        GROUP BY lower(translate(slug, '-', ''))
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Tenant hostname aliases collide after removing dashes; resolve the duplicate aliases before applying V158';
    END IF;
END $$;

CREATE UNIQUE INDEX uk_tenants_hostname_alias_lower
    ON edushift.tenants (lower(translate(slug, '-', '')))
    WHERE deleted = false;

COMMENT ON INDEX edushift.uk_tenants_hostname_alias_lower IS
    'Unique tenant hostname identity after removing legacy slug dashes';
