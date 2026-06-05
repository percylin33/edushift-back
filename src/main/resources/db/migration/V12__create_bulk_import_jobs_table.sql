-- =============================================================================
-- V12__create_bulk_import_jobs_table.sql
-- Asynchronous bulk-import jobs (currently used for students; the schema is
-- generic via `job_type` so future bulk imports — guardians, classes,
-- enrolments — can reuse the same table without a new migration).
--
-- Lifecycle:
--   PENDING   -> the multipart upload was accepted; payload sits in memory /
--                temp file, the worker has not yet started.
--   PROCESSING -> a worker is parsing rows.
--   COMPLETED -> all rows were processed (some may have failed individually,
--                see error_rows + errors_json).
--   FAILED    -> the job aborted before completing all rows (e.g. invalid
--                spreadsheet format, transient DB outage).
--
-- The errors_json column carries an array of per-row failures so the UI can
-- render a "fix this row" CSV without making an extra query. Capping the
-- column at JSON keeps it queryable and avoids storing each error as a row
-- in a sister table (overkill for a feature whose audience is admins, not
-- consumer-grade analytics).
-- =============================================================================

CREATE TABLE edushift.bulk_import_jobs (
    id                  uuid          PRIMARY KEY,
    tenant_id           uuid          NOT NULL,
    public_uuid         uuid          NOT NULL,

    -- Audit
    created_at          timestamptz   NOT NULL,
    updated_at          timestamptz   NOT NULL,
    created_by          uuid,
    updated_by          uuid,
    deleted             boolean       NOT NULL DEFAULT false,

    -- What kind of import (extensible for Sprint 4+)
    job_type            varchar(40)   NOT NULL,

    -- Lifecycle
    status              varchar(20)   NOT NULL,
    started_at          timestamptz,
    finished_at         timestamptz,

    -- Source
    file_name           varchar(255)  NOT NULL,
    file_size_bytes     bigint        NOT NULL,

    -- Counters (filled in by the worker, monotonically advanced).
    -- total_rows is set once the worker has read the header; before
    -- that it stays NULL.
    total_rows          int,
    processed_rows      int           NOT NULL DEFAULT 0,
    error_rows          int           NOT NULL DEFAULT 0,

    -- Per-row errors as JSON, e.g. [{ "row": 3, "code": "…", "message": "…" }]
    errors_json         jsonb         NOT NULL DEFAULT '[]'::jsonb,

    -- A short message describing the high-level failure when status=FAILED.
    -- Per-row errors stay inside errors_json.
    fail_reason         varchar(500),

    CONSTRAINT uk_bulk_import_jobs_public_uuid UNIQUE (public_uuid),

    CONSTRAINT chk_bulk_import_jobs_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')
    ),

    CONSTRAINT chk_bulk_import_jobs_job_type CHECK (
        job_type IN ('STUDENTS')
    ),

    CONSTRAINT chk_bulk_import_jobs_counters_non_negative CHECK (
        processed_rows >= 0
        AND error_rows >= 0
        AND (total_rows IS NULL OR total_rows >= 0)
    )
);

COMMENT ON TABLE  edushift.bulk_import_jobs              IS 'Async bulk-import job tracker (per tenant). Drives progress UIs.';
COMMENT ON COLUMN edushift.bulk_import_jobs.job_type     IS 'BulkImportJobType — STUDENTS today, more in Sprint 4+';
COMMENT ON COLUMN edushift.bulk_import_jobs.status       IS 'BulkImportStatus — PENDING|PROCESSING|COMPLETED|FAILED';
COMMENT ON COLUMN edushift.bulk_import_jobs.errors_json  IS 'Per-row errors: [{row, code, message}].';

-- Hot path: list a tenant's recent jobs ordered by creation time.
CREATE INDEX idx_bulk_import_jobs_tenant_created
    ON edushift.bulk_import_jobs (tenant_id, created_at DESC)
    WHERE deleted = false;

CREATE TRIGGER set_updated_at_bulk_import_jobs
    BEFORE UPDATE ON edushift.bulk_import_jobs
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();
