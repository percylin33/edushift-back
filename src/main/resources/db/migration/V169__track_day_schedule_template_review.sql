-- Track explicit admin review separately from automatic/default configuration.
ALTER TABLE edushift.day_schedule_templates
    ADD COLUMN reviewed_at timestamptz,
    ADD COLUMN reviewed_by uuid;

ALTER TABLE edushift.day_schedule_templates
    ADD CONSTRAINT fk_day_schedule_templates_reviewed_by
        FOREIGN KEY (reviewed_by)
        REFERENCES edushift.users (public_uuid)
        ON DELETE SET NULL;

COMMENT ON COLUMN edushift.day_schedule_templates.reviewed_at IS
    'Timestamp when a tenant admin last reviewed the current school-day plan.';

COMMENT ON COLUMN edushift.day_schedule_templates.reviewed_by IS
    'public_uuid of the tenant admin who last reviewed the current school-day plan.';
