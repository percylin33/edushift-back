-- =============================================================================
-- V124 — Who authored a course competency.
-- MINEDU: on-demand curriculum seed.
-- INSTITUTIONAL: school catalog (admin, and the backfill of existing rows).
-- TEACHER: created by a teacher on a course they are assigned to.
-- =============================================================================

ALTER TABLE edushift.competencies
    ADD COLUMN origin varchar(20) NOT NULL DEFAULT 'INSTITUTIONAL';

ALTER TABLE edushift.competencies
    ADD CONSTRAINT chk_competencies_origin
        CHECK (origin IN ('MINEDU', 'INSTITUTIONAL', 'TEACHER'));

COMMENT ON COLUMN edushift.competencies.origin IS
    'MINEDU = curriculum seed, INSTITUTIONAL = school catalog, TEACHER = created by an assigned teacher. Existing rows backfill to INSTITUTIONAL.';
