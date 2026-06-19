-- =============================================================================
-- V31__create_lms_file_objects.sql
-- Registry of every binary uploaded via the LMS module (Sprint 7a / BE-7a.0).
--
-- Key design decisions:
--   * id           internal UUIDv7 (time-ordered; never exposed via API)
--   * public_uuid  external UUIDv4 surfaced to clients; used in
--                  /v1/files/{publicUuid}/download and as the FK target
--                  for lms_materials, lms_submissions, etc.
--   * tenant_id    mandatory; managed by Hibernate discriminator (@TenantId)
--   * provider     FIREBASE (prod/staging) or LOCAL_FS (dev/test)
--   * remote_key   the storage-side key. For LOCAL_FS it's a relative path
--                  under app.storage.local-fs.root; for FIREBASE it's the
--                  object name inside the GCS bucket. The
--                  uk_file_objects_tenant_provider_remote_key_active
--                  index guarantees one DB row per (tenant, provider, key)
--                  so retries do not create duplicates.
--   * checksum     SHA-256 of the bytes. Used for de-duplication (DEBT-7A-4)
--                  and integrity verification on download.
--   * size_bytes   enforced by Spring multipart (max-file-size=25MB).
--   * content_type stored as sniffed MIME; falls back to
--                  application/octet-stream when unrecognised.
--
-- Why we do NOT store the binary in Postgres (DEBT-7A-x backstop):
--   * Backups stay small.
--   * The provider can be swapped (Local-FS <-> Firebase) without
--     rewriting the binary stream.
--   * The storage is a concern of the provider; the DB only owns
--     metadata + the access control flag.
-- =============================================================================

CREATE TABLE edushift.lms_file_objects (
    id              uuid          PRIMARY KEY,
    tenant_id       uuid          NOT NULL,
    public_uuid     uuid          NOT NULL,

    -- Audit (inherited from AuditableEntity / BaseEntity)
    created_at      timestamptz   NOT NULL,
    updated_at      timestamptz   NOT NULL,
    created_by      uuid,
    updated_by      uuid,
    deleted         boolean       NOT NULL DEFAULT false,
    deleted_at      timestamptz,

    -- Domain columns
    provider        varchar(16)   NOT NULL,
    remote_key      varchar(512)  NOT NULL,
    original_name   varchar(255)  NOT NULL,
    content_type    varchar(127)  NOT NULL,
    size_bytes      bigint        NOT NULL,
    checksum_sha256 varchar(64)   NOT NULL,
    bucket          varchar(128),
    reference_count integer       NOT NULL DEFAULT 0,

    -- Constraints
    CONSTRAINT fk_file_objects_tenant FOREIGN KEY (tenant_id)
        REFERENCES edushift.tenants (id)
        ON DELETE RESTRICT,
    CONSTRAINT uk_file_objects_public_uuid UNIQUE (public_uuid),

    -- Domain invariants
    CONSTRAINT chk_file_objects_provider CHECK (
        provider IN ('FIREBASE', 'LOCAL_FS')
    ),
    CONSTRAINT chk_file_objects_size_positive CHECK (size_bytes > 0),
    CONSTRAINT chk_file_objects_checksum_format CHECK (
        checksum_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_file_objects_remote_key_not_empty CHECK (
        length(remote_key) > 0
    ),
    CONSTRAINT chk_file_objects_deleted_at_consistent CHECK (
        (deleted = false AND deleted_at IS NULL)
        OR (deleted = true  AND deleted_at IS NOT NULL)
    )
);

COMMENT ON TABLE  edushift.lms_file_objects IS
    'Metadata-only registry of every LMS-uploaded binary. The bytes live in the '
    'provider (Firebase Storage in prod/staging, local filesystem in dev/test). '
    'Cross-referenced by lms_materials, lms_submissions, and future lms_* tables.';

COMMENT ON COLUMN edushift.lms_file_objects.id          IS 'Internal UUIDv7 PK; never exposed via API';
COMMENT ON COLUMN edushift.lms_file_objects.public_uuid IS 'External, stable identifier (UUIDv4)';
COMMENT ON COLUMN edushift.lms_file_objects.tenant_id   IS 'Tenant scope; managed by Hibernate @TenantId';
COMMENT ON COLUMN edushift.lms_file_objects.provider    IS 'FIREBASE (prod/staging) or LOCAL_FS (dev/test)';
COMMENT ON COLUMN edushift.lms_file_objects.remote_key  IS
    'Provider-side object key. For LOCAL_FS: relative path under app.storage.local-fs.root. '
    'For FIREBASE: object name inside the GCS bucket.';
COMMENT ON COLUMN edushift.lms_file_objects.original_name IS
    'Filename as uploaded by the user; surfaced for download Content-Disposition.';
COMMENT ON COLUMN edushift.lms_file_objects.content_type  IS
    'MIME sniffed at upload time; falls back to application/octet-stream.';
COMMENT ON COLUMN edushift.lms_file_objects.size_bytes    IS 'Payload size in bytes; max 25MB enforced by Spring';
COMMENT ON COLUMN edushift.lms_file_objects.checksum_sha256 IS
    'Lowercase hex SHA-256 of the bytes; enables de-duplication (DEBT-7A-4).';
COMMENT ON COLUMN edushift.lms_file_objects.bucket        IS
    'GCS bucket name (FIREBASE provider only); NULL for LOCAL_FS.';
COMMENT ON COLUMN edushift.lms_file_objects.reference_count IS
    'How many domain entities (materials, submissions) currently reference this row. '
    'Used by housekeeping to decide when a binary can be physically deleted.';
COMMENT ON COLUMN edushift.lms_file_objects.deleted_at     IS 'Soft-delete timestamp (populated by @SQLDelete)';

-- ----------------------------------------------------------------------------
-- Indexes
-- ----------------------------------------------------------------------------

-- Mandatory tenant index (ALWAYS for tenant-scoped tables)
CREATE INDEX idx_file_objects_tenant
    ON edushift.lms_file_objects (tenant_id)
    WHERE deleted = false;

-- Per-tenant uniqueness for naturally-unique storage key
CREATE UNIQUE INDEX uk_file_objects_tenant_provider_remote_key_active
    ON edushift.lms_file_objects (tenant_id, provider, remote_key)
    WHERE deleted = false;

-- Public UUID lookup (download path)
CREATE INDEX idx_file_objects_public_uuid
    ON edushift.lms_file_objects (public_uuid)
    WHERE deleted = false;

-- Hot path: list files by tenant + most recent
CREATE INDEX idx_file_objects_tenant_created
    ON edushift.lms_file_objects (tenant_id, created_at DESC)
    WHERE deleted = false;

-- (tenant, sha256) for de-duplication probes (DEBT-7A-4)
CREATE INDEX idx_file_objects_tenant_checksum
    ON edushift.lms_file_objects (tenant_id, checksum_sha256)
    WHERE deleted = false;

-- ----------------------------------------------------------------------------
-- Trigger
-- ----------------------------------------------------------------------------

CREATE TRIGGER set_updated_at_file_objects
    BEFORE UPDATE ON edushift.lms_file_objects
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();
