-- =============================================================================
-- V153__create_annual_disclosure_packages.sql
-- Versioned annual information packages for private-school families.
-- Drafts are editable; every publication is an immutable tenant-scoped snapshot.
-- =============================================================================

-- Composite keys let the database enforce that a package and its academic year
-- always belong to the same school, even if application code regresses later.
CREATE UNIQUE INDEX uk_academic_years_id_tenant
    ON edushift.academic_years (id, tenant_id);

CREATE TABLE edushift.annual_disclosure_packages (
    id                  uuid          PRIMARY KEY,
    tenant_id           uuid          NOT NULL,
    public_uuid         uuid          NOT NULL,
    academic_year_id    uuid          NOT NULL,
    status              varchar(20)   NOT NULL DEFAULT 'DRAFT',
    revision_number     integer       NOT NULL DEFAULT 1,
    content             jsonb         NOT NULL DEFAULT '{}'::jsonb,
    review_note         varchar(2000),
    submitted_at        timestamptz,
    submitted_by        uuid,
    approved_at         timestamptz,
    approved_by         uuid,
    published_at        timestamptz,
    published_by        uuid,
    created_at          timestamptz   NOT NULL,
    updated_at          timestamptz   NOT NULL,
    created_by          uuid,
    updated_by          uuid,
    deleted             boolean       NOT NULL DEFAULT false,
    deleted_at          timestamptz,
    CONSTRAINT uk_annual_disclosure_packages_public_uuid UNIQUE (public_uuid),
    CONSTRAINT uk_annual_disclosure_packages_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_disclosure_package_academic_year_tenant
        FOREIGN KEY (academic_year_id, tenant_id)
        REFERENCES edushift.academic_years (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT chk_disclosure_package_status
        CHECK (status IN ('DRAFT', 'IN_REVIEW', 'APPROVED', 'PUBLISHED')),
    CONSTRAINT chk_disclosure_package_revision_positive CHECK (revision_number > 0),
    CONSTRAINT chk_disclosure_package_deleted_at_consistent CHECK (
        (deleted = false AND deleted_at IS NULL) OR (deleted = true AND deleted_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uk_annual_disclosure_packages_tenant_year_active
    ON edushift.annual_disclosure_packages (tenant_id, academic_year_id)
    WHERE deleted = false;
CREATE INDEX idx_annual_disclosure_packages_tenant_status
    ON edushift.annual_disclosure_packages (tenant_id, status, updated_at DESC)
    WHERE deleted = false;
CREATE INDEX idx_annual_disclosure_packages_year
    ON edushift.annual_disclosure_packages (tenant_id, academic_year_id)
    WHERE deleted = false;
CREATE TRIGGER set_updated_at_annual_disclosure_packages
    BEFORE UPDATE ON edushift.annual_disclosure_packages
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON TABLE edushift.annual_disclosure_packages IS
    'Editable annual disclosure workspace for one private school and academic year.';
COMMENT ON COLUMN edushift.annual_disclosure_packages.tenant_id IS
    'School scope; Hibernate @TenantId sets and filters this value.';
COMMENT ON COLUMN edushift.annual_disclosure_packages.content IS
    'Validated field values, applicability states, deadlines and tenant-owned attachment references.';
COMMENT ON COLUMN edushift.annual_disclosure_packages.revision_number IS
    'Editable revision counter; published snapshots are stored separately.';

CREATE TABLE edushift.annual_disclosure_publications (
    id                  uuid          PRIMARY KEY,
    tenant_id           uuid          NOT NULL,
    public_uuid         uuid          NOT NULL,
    package_id          uuid          NOT NULL,
    version_number      integer       NOT NULL,
    content             jsonb         NOT NULL,
    content_sha256      varchar(64)   NOT NULL,
    approved_at         timestamptz   NOT NULL,
    approved_by         uuid          NOT NULL,
    published_at        timestamptz   NOT NULL,
    published_by        uuid          NOT NULL,
    created_at          timestamptz   NOT NULL,
    updated_at          timestamptz   NOT NULL,
    created_by          uuid,
    updated_by          uuid,
    deleted             boolean       NOT NULL DEFAULT false,
    deleted_at          timestamptz,
    CONSTRAINT uk_annual_disclosure_publications_public_uuid UNIQUE (public_uuid),
    CONSTRAINT uk_annual_disclosure_publications_version UNIQUE (package_id, version_number),
    CONSTRAINT fk_disclosure_publication_package_tenant
        FOREIGN KEY (package_id, tenant_id)
        REFERENCES edushift.annual_disclosure_packages (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT chk_disclosure_publication_version_positive CHECK (version_number > 0),
    CONSTRAINT chk_disclosure_publication_hash CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_disclosure_publication_timestamps CHECK (approved_at <= published_at),
    CONSTRAINT chk_disclosure_publication_not_deleted CHECK (deleted = false AND deleted_at IS NULL)
);

CREATE INDEX idx_annual_disclosure_publications_tenant_package_version
    ON edushift.annual_disclosure_publications (tenant_id, package_id, version_number DESC);
CREATE INDEX idx_annual_disclosure_publications_tenant_published
    ON edushift.annual_disclosure_publications (tenant_id, published_at DESC);

COMMENT ON TABLE edushift.annual_disclosure_publications IS
    'Immutable snapshots of approved annual disclosure packages published to families.';
COMMENT ON COLUMN edushift.annual_disclosure_publications.content IS
    'Frozen content snapshot for this publication version; never overwritten by a later revision.';
COMMENT ON COLUMN edushift.annual_disclosure_publications.content_sha256 IS
    'SHA-256 of canonical JSON snapshot for integrity comparison.';

CREATE FUNCTION edushift.reject_annual_disclosure_publication_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'annual disclosure publication snapshots are immutable';
END;
$$;

CREATE TRIGGER annual_disclosure_publications_immutable
    BEFORE UPDATE OR DELETE ON edushift.annual_disclosure_publications
    FOR EACH ROW EXECUTE FUNCTION edushift.reject_annual_disclosure_publication_mutation();
