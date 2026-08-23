-- =============================================================================
-- V104 — Day plan metadata on day_schedule_templates (entrada / salida / periodo)
-- ADR-SCH-12: wizard generates non-teaching blocks; academic gaps are suggested
-- periods derived from day_start, day_end, period_minutes.
-- =============================================================================

ALTER TABLE edushift.day_schedule_templates
    ADD COLUMN IF NOT EXISTS day_start time;

ALTER TABLE edushift.day_schedule_templates
    ADD COLUMN IF NOT EXISTS day_end time;

ALTER TABLE edushift.day_schedule_templates
    ADD COLUMN IF NOT EXISTS period_minutes integer;

ALTER TABLE edushift.day_schedule_templates
    DROP CONSTRAINT IF EXISTS chk_day_schedule_templates_day_window;

ALTER TABLE edushift.day_schedule_templates
    ADD CONSTRAINT chk_day_schedule_templates_day_window CHECK (
        day_start IS NULL
        OR day_end IS NULL
        OR day_end > day_start
    );

ALTER TABLE edushift.day_schedule_templates
    DROP CONSTRAINT IF EXISTS chk_day_schedule_templates_period_minutes;

ALTER TABLE edushift.day_schedule_templates
    ADD CONSTRAINT chk_day_schedule_templates_period_minutes CHECK (
        period_minutes IS NULL
        OR (period_minutes >= 15 AND period_minutes <= 120)
    );

COMMENT ON COLUMN edushift.day_schedule_templates.day_start IS
    'School-day start (entrada) for this template; used to suggest academic periods.';

COMMENT ON COLUMN edushift.day_schedule_templates.day_end IS
    'School-day end (salida) for this template; used to suggest academic periods.';

COMMENT ON COLUMN edushift.day_schedule_templates.period_minutes IS
    'Default academic period length in minutes (hora académica).';
