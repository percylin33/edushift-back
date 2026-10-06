-- =============================================================================
-- V126 — Grading scale on grade-register templates, plus CNEB literal on evaluations.
-- SCORE_0_20 stays the default. LITERAL_CNEB is AD / A / B / C.
-- Rollback: drop chk_grt_grading_scale and grading_scale, then restore
-- chk_evaluations_scale without LITERAL_CNEB.
-- =============================================================================

ALTER TABLE edushift.evaluations
    DROP CONSTRAINT IF EXISTS chk_evaluations_scale;

ALTER TABLE edushift.evaluations
    ADD CONSTRAINT chk_evaluations_scale
        CHECK (scale IN (
            'SCORE_0_20',
            'LITERAL_AD',
            'LITERAL_NA',
            'LITERAL_A_B_C_D',
            'LITERAL_CNEB'
        ));

ALTER TABLE edushift.grade_register_templates
    ADD COLUMN IF NOT EXISTS grading_scale varchar(24) NOT NULL DEFAULT 'SCORE_0_20';

ALTER TABLE edushift.grade_register_templates
    DROP CONSTRAINT IF EXISTS chk_grt_grading_scale;

ALTER TABLE edushift.grade_register_templates
    ADD CONSTRAINT chk_grt_grading_scale
        CHECK (grading_scale IN ('SCORE_0_20', 'LITERAL_CNEB'));

COMMENT ON COLUMN edushift.grade_register_templates.grading_scale IS
    'Scale of this working copy. SCORE_0_20 or LITERAL_CNEB (AD, A, B, C). Changing it is blocked once evaluations or grades exist.';
