-- =============================================================================
-- V85__add_report_jobs_triggered_by_template.sql
--
-- Cierre-C / B12 cierre -- backlink from `report_jobs` to the template that
-- triggered them (nullable; ad-hoc wizard runs leave it NULL).
--
-- Decisiones:
--   * ADR-Cierre-C.12 -- column is nullable + no FK constraint to keep
--     soft-deleted templates' history intact.
--   * ADR-Cierre-C.12 -- no DEFAULT; existing rows get NULL on apply.
-- =============================================================================

ALTER TABLE edushift.report_jobs
    ADD COLUMN IF NOT EXISTS triggered_by_template uuid;

COMMENT ON COLUMN edushift.report_jobs.triggered_by_template
    IS 'Sprint cierre-C / B12 -- publicUuid of the ReportTemplate that dispatched this job, NULL for ad-hoc wizard runs.';