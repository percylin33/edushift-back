-- =============================================================================
-- V83__extend_bulk_import_job_type_check.sql
--
-- Cierre-B / F7 — Bulk import teachers.
--
-- V12 created `bulk_import_jobs.job_type` with a CHECK constraint
-- that whitelisted only `STUDENTS`. The closure plan calls for bulk
-- import teachers; we extend the whitelist via drop + recreate.
--
-- Using a partial UNIQUE-style approach would be wrong here (it's a
-- CHECK on a varchar column, not an index). The clean path is:
--   1. DROP CONSTRAINT chk_bulk_import_jobs_job_type;
--   2. ADD CONSTRAINT with the extended whitelist.
--
-- The new whitelist is `{STUDENTS, TEACHERS}`; future bulk imports
-- (guardians, sections, …) will append more constants via later
-- migrations.
-- =============================================================================

ALTER TABLE edushift.bulk_import_jobs
    DROP CONSTRAINT chk_bulk_import_jobs_job_type;

ALTER TABLE edushift.bulk_import_jobs
    ADD CONSTRAINT chk_bulk_import_jobs_job_type
    CHECK (job_type IN ('STUDENTS', 'TEACHERS'));

COMMENT ON COLUMN edushift.bulk_import_jobs.job_type
    IS 'BulkImportJobType \u2014 STUDENTS | TEACHERS (whitelist extended in V83 for cierre-B / F7).';