-- Admissions: operational follow-up. Existing records retain empty follow-up fields.
-- Additive migration: rollback application code first; keep columns to preserve history.
ALTER TABLE edushift.admission_inquiries
    ADD COLUMN responsible_name varchar(100),
    ADD COLUMN next_attention_date date,
    ADD COLUMN next_action varchar(300);

COMMENT ON COLUMN edushift.admission_inquiries.responsible_name
    IS 'Display name of the staff member responsible for follow-up; not an authorization identity';
COMMENT ON COLUMN edushift.admission_inquiries.next_attention_date
    IS 'Next school-local calendar date to contact the family';
COMMENT ON COLUMN edushift.admission_inquiries.next_action
    IS 'Next operational action agreed with the family';

CREATE INDEX idx_admission_inquiries_tenant_follow_up
    ON edushift.admission_inquiries (tenant_id, academic_year_id, next_attention_date)
    WHERE deleted = false AND status NOT IN ('CLOSED', 'CONVERTED');
