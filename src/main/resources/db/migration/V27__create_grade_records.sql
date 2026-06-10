-- =====================================================================
-- V27 - Sprint 5B / BE-5B.3 - Evaluations.grade_records
--
-- A grade record is the score (or qualitative literal) that a teacher
-- registers for a single student against a single evaluation. The pair
-- (evaluation, student) is unique on non-deleted rows (ADR-5B.5): the
-- service uses upsert semantics so re-posting the same pair updates the
-- existing row instead of creating a duplicate.
--
-- Bulk-record (BE-5B.3 / ADR-5B.6) processes N (≤ section size) records
-- in a single @Transactional unit; one invalid row aborts the whole
-- batch (atomic, all-or-nothing).
--
-- Score shape depends on the parent Evaluation.scale (validated in the
-- service, NOT in DB — it would require a cross-table CHECK):
--   SCORE_0_20         -> score in [0, 20.00], literal NULL.
--   LITERAL_AD         -> literal in {"AD", "A"}, score NULL.
--   LITERAL_NA         -> literal in {"NA", "A"}, score NULL.
--   LITERAL_A_B_C_D    -> literal in {"A", "B", "C", "D"}, score NULL.
--
-- Lifecycle gate: writes are accepted only when the parent evaluation
-- is in DRAFT or PUBLISHED. CLOSED is read-only and the service throws
-- GRADE_EVAL_CLOSED (409). The DB enforces only the existence of the
-- parent evaluation via FK.
--
-- Multi-tenant via tenant_id + Hibernate's @TenantId discriminator on
-- TenantAwareEntity. Soft-delete via `deleted` flag.
-- =====================================================================

CREATE TABLE edushift.grade_records (
    id                          uuid          PRIMARY KEY,
    tenant_id                   uuid          NOT NULL,
    public_uuid                 uuid          NOT NULL,
    created_at                  timestamptz   NOT NULL,
    updated_at                  timestamptz   NOT NULL,
    created_by                  uuid,
    updated_by                  uuid,
    deleted                     boolean       NOT NULL DEFAULT false,
    deleted_at                  timestamptz,

    evaluation_id               uuid          NOT NULL,
    student_id                  uuid          NOT NULL,

    -- Score shape (one of: numeric or literal). Both nullable but at
    -- least one must be non-null per chk_grade_records_value_present.
    score                       numeric(6,2),
    literal                     varchar(8),

    -- Optional teacher commentary (≤ 1000 chars).
    comments                    varchar(1000),

    -- Audit del registro: timestamp del último write y autor del último
    -- write. Distintos de created_at / updated_at del BaseEntity porque
    -- en el contexto pedagógico interesa "cuándo lo registró el docente"
    -- aunque BaseEntity haga lo mismo (DEBT-EVAL-3 unificará en Fase 7).
    recorded_at                 timestamptz   NOT NULL,
    recorded_by_user_id         uuid          NOT NULL,

    is_active                   boolean       NOT NULL DEFAULT true,

    CONSTRAINT uk_grade_records_public_uuid
        UNIQUE (public_uuid),

    -- Al menos uno de score / literal debe estar presente. La validación
    -- fina (rango por scale) la hace el servicio.
    CONSTRAINT chk_grade_records_value_present
        CHECK (score IS NOT NULL OR literal IS NOT NULL),

    -- Numeric range fence (the service enforces the per-scale upper bound).
    CONSTRAINT chk_grade_records_score_range
        CHECK (score IS NULL OR (score >= 0.00 AND score <= 20.00)),

    -- Catálogo cerrado de literales aceptados across scales. La selección
    -- por scale la hace el service (GRADE_LITERAL_INVALID).
    CONSTRAINT chk_grade_records_literal_values
        CHECK (literal IS NULL OR literal IN ('AD','A','B','C','D','NA')),

    CONSTRAINT chk_grade_records_deleted_at_consistent
        CHECK (
            (deleted = false AND deleted_at IS NULL)
            OR (deleted = true AND deleted_at IS NOT NULL)
        ),

    CONSTRAINT fk_grade_records_evaluation
        FOREIGN KEY (evaluation_id)
        REFERENCES edushift.evaluations (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_grade_records_student
        FOREIGN KEY (student_id)
        REFERENCES edushift.students (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_grade_records_recorded_by
        FOREIGN KEY (recorded_by_user_id)
        REFERENCES edushift.users (id)
        ON DELETE RESTRICT
);

-- Hot path: list grades of an evaluation (drives the grade book screen
-- and the GET /evaluations/{uuid}/grade-records endpoint).
CREATE INDEX idx_grade_records_tenant_evaluation
    ON edushift.grade_records (tenant_id, evaluation_id)
    WHERE NOT deleted;

-- Hot path: list grades of a student across evaluations (drives the
-- student's transcript view in FE-5B.4 and the per-student filter).
CREATE INDEX idx_grade_records_tenant_student
    ON edushift.grade_records (tenant_id, student_id)
    WHERE NOT deleted;

-- Reverse audit lookup: "who recorded the last write" (admin reports).
CREATE INDEX idx_grade_records_recorded_by
    ON edushift.grade_records (tenant_id, recorded_by_user_id)
    WHERE NOT deleted;

-- One grade per (evaluation, student) on non-deleted rows. Drives the
-- upsert semantics of POST /evaluations/{uuid}/grade-records: re-posting
-- the same student updates the row instead of creating a duplicate.
CREATE UNIQUE INDEX uk_grade_records_evaluation_student
    ON edushift.grade_records (evaluation_id, student_id)
    WHERE NOT deleted;

CREATE TRIGGER set_updated_at_grade_records
    BEFORE UPDATE ON edushift.grade_records
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON TABLE  edushift.grade_records                     IS 'Grade record per (evaluation, student) — Sprint 5B / BE-5B.3.';
COMMENT ON COLUMN edushift.grade_records.score               IS 'Numeric score for SCORE_0_20 evaluations. NULL for literal scales. DB fence [0,20]; service enforces the per-scale range.';
COMMENT ON COLUMN edushift.grade_records.literal             IS 'Qualitative literal for LITERAL_* scales (AD/A/B/C/D/NA). NULL for SCORE_0_20. Service validates the allowed subset per scale.';
COMMENT ON COLUMN edushift.grade_records.recorded_at         IS 'Timestamp del último write (insert o upsert update). Útil para reportes "última corrección antes del cierre del bimestre".';
COMMENT ON COLUMN edushift.grade_records.recorded_by_user_id IS 'Usuario autor del último write. Inyectado desde CurrentUserProvider.currentUserId().';
COMMENT ON COLUMN edushift.grade_records.is_active           IS 'Flag de desactivación lógica (e.g., "esta nota fue reemplazada por una posterior pero la conservamos para auditoría"). Independiente de soft-delete.';
