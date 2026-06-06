-- =============================================================================
-- V17__create_academic_periods.sql
-- AcademicPeriod sub-module (Sprint 4 / BE-4.5).
--
-- A period is a slice of an AcademicYear used to anchor grade reports
-- (Sprint 7) and teacher assignments (BE-4.7). Schools mix:
--   * BIMESTRE  (4 per year, the most common in MINEDU Peru)
--   * TRIMESTRE (3 per year)
--   * ANUAL     (1 spanning the whole year, used for final averages)
--
-- Ordinals are 1..N within a (year, type), unique and CONTIGUOUS — the
-- service layer enforces "no gaps" so you cannot create Bimestre 1, 2,
-- 4 without 3. Date ranges within the same (year, type) MUST NOT
-- overlap, but two ANUAL + 4 BIMESTRE in the same year is valid.
-- =============================================================================

CREATE TABLE edushift.academic_periods (
    id                uuid           PRIMARY KEY,
    tenant_id         uuid           NOT NULL,
    public_uuid       uuid           NOT NULL,

    -- Audit
    created_at        timestamptz    NOT NULL,
    updated_at        timestamptz    NOT NULL,
    created_by        uuid,
    updated_by        uuid,
    deleted           boolean        NOT NULL DEFAULT false,
    deleted_at        timestamptz,

    -- Relationship
    academic_year_id  uuid           NOT NULL,

    -- Identity
    period_type       varchar(16)    NOT NULL,
    ordinal           int            NOT NULL,
    name              varchar(60)    NOT NULL,

    -- Range
    start_date        date           NOT NULL,
    end_date          date           NOT NULL,

    CONSTRAINT uk_academic_periods_public_uuid UNIQUE (public_uuid),

    CONSTRAINT chk_academic_periods_period_type CHECK (
        period_type IN ('BIMESTRE', 'TRIMESTRE', 'ANUAL')
    ),

    CONSTRAINT chk_academic_periods_ordinal_positive CHECK (
        ordinal >= 1
    ),

    -- end_date inclusive — same-day periods are unrealistic for school
    -- but harmless; we forbid only "end before start".
    CONSTRAINT chk_academic_periods_date_order CHECK (
        end_date >= start_date
    ),

    CONSTRAINT chk_academic_periods_deleted_at_consistent CHECK (
        (deleted = false AND deleted_at IS NULL)
        OR (deleted = true  AND deleted_at IS NOT NULL)
    ),

    CONSTRAINT fk_academic_periods_year
        FOREIGN KEY (academic_year_id) REFERENCES edushift.academic_years(id)
        ON DELETE RESTRICT
);

COMMENT ON TABLE  edushift.academic_periods                IS 'Academic periods (BIMESTRE / TRIMESTRE / ANUAL) per academic year (Sprint 4 / BE-4.5).';
COMMENT ON COLUMN edushift.academic_periods.tenant_id      IS 'Tenant scope; managed by Hibernate discriminator multi-tenancy';
COMMENT ON COLUMN edushift.academic_periods.period_type    IS 'BIMESTRE | TRIMESTRE | ANUAL — multiple types may coexist in a year.';
COMMENT ON COLUMN edushift.academic_periods.ordinal        IS 'Position inside (year, type). Unique and contiguous (1..N).';
COMMENT ON COLUMN edushift.academic_periods.name           IS 'Display name; auto-generated as "<I> <Type>" if not provided (e.g. "II Bimestre").';

-- (academic_year_id, period_type, ordinal) unique on non-deleted rows
CREATE UNIQUE INDEX uk_academic_periods_year_type_ordinal_active
    ON edushift.academic_periods (academic_year_id, period_type, ordinal)
    WHERE deleted = false;

-- Hot path: list a year's periods (includes type+ordinal for sort)
CREATE INDEX idx_academic_periods_tenant_year
    ON edushift.academic_periods (tenant_id, academic_year_id, period_type, ordinal)
    WHERE deleted = false;

CREATE TRIGGER set_updated_at_academic_periods
    BEFORE UPDATE ON edushift.academic_periods
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();
