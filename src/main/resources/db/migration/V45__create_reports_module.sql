-- =============================================================================
-- V45 - Sprint 9 / BE-9.2 - Reports module: async generation + storage.
-- =============================================================================

CREATE TABLE edushift.report_jobs (
    id                       uuid         PRIMARY KEY,
    tenant_id                uuid         NOT NULL,
    public_uuid              uuid         NOT NULL,
    requested_by_user_id     uuid,
    report_type              varchar(40)  NOT NULL,
    format                   varchar(10)  NOT NULL,
    params                   jsonb        NOT NULL DEFAULT '{}'::jsonb,
    idem_key                 varchar(80)  NOT NULL DEFAULT '',
    status                   varchar(15)  NOT NULL DEFAULT 'PENDING',
    progress_pct             smallint     NOT NULL DEFAULT 0,
    output_file_id           uuid,
    error_code               varchar(50),
    error_message            varchar(2000),
    requested_at             timestamptz  NOT NULL DEFAULT now(),
    started_at               timestamptz,
    finished_at              timestamptz,
    expires_at               timestamptz  NOT NULL DEFAULT (now() + interval '10 minutes'),
    deleted                  boolean      NOT NULL DEFAULT false,
    deleted_at               timestamptz,
    created_at               timestamptz  NOT NULL DEFAULT now(),
    updated_at               timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT fk_report_jobs_user
        FOREIGN KEY (requested_by_user_id) REFERENCES edushift.users(id) ON DELETE SET NULL,
    CONSTRAINT chk_report_jobs_format
        CHECK (format IN ('PDF', 'XLSX', 'CSV')),
    CONSTRAINT chk_report_jobs_status
        CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_report_jobs_progress
        CHECK (progress_pct BETWEEN 0 AND 100),
    CONSTRAINT chk_report_jobs_report_type
        CHECK (report_type IN ('GRADE_BOOK', 'ATTENDANCE_SUMMARY', 'PERIOD_CLOSE', 'STUDENT_TRANSCRIPT'))
);

-- Idempotency: if idem_key is empty, the UNIQUE collapses (Postgres
-- treats empty as a value, so two rows with '' collide; that's fine —
-- the FE only sets idem_key when it wants dedup, otherwise the FE
-- accepts a new job).
CREATE UNIQUE INDEX uk_report_jobs_idem
    ON edushift.report_jobs (tenant_id, requested_by_user_id, idem_key);

CREATE UNIQUE INDEX uk_report_jobs_public_uuid
    ON edushift.report_jobs (public_uuid);

CREATE INDEX idx_report_jobs_tenant_status
    ON edushift.report_jobs (tenant_id, status, requested_at)
    WHERE deleted = false;

CREATE INDEX idx_report_jobs_tenant_user_recent
    ON edushift.report_jobs (tenant_id, requested_by_user_id, requested_at DESC)
    WHERE deleted = false;

CREATE INDEX idx_report_jobs_expires
    ON edushift.report_jobs (expires_at)
    WHERE deleted = false AND status = 'RUNNING';

COMMENT ON TABLE  edushift.report_jobs
    IS 'Report generation jobs (BE-9.2). Multi-tenant via tenant_id + Hibernate @TenantId. Polled by FE for status.';
COMMENT ON COLUMN edushift.report_jobs.idem_key
    IS 'Client-supplied idempotency key. UNIQUE per (tenant, user, key).';
COMMENT ON COLUMN edushift.report_jobs.expires_at
    IS 'Hard timeout (default 10 min). Sweeper marks RUNNING > expires_at as FAILED.';
