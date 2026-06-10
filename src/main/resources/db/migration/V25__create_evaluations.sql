-- =====================================================================
-- V25 - Sprint 5B / BE-5B.1 - Evaluations.evaluations
--
-- An evaluation is a graded item (TASK, QUIZ, EXAM, RUBRIC, COMPETENCY)
-- authored by a teacher against a TeacherAssignment. Optionally anchored
-- to a Unit (tag pedagógico) or a specific LearningSession (concreción
-- de un día). Lifecycle DRAFT -> PUBLISHED -> CLOSED, terminal en CLOSED
-- (ADR-5B.7).
--
-- Scale is the second axis: SCORE_0_20 (numérica 0..20) o escalas
-- literales (AD/A, NA/A, A/B/C/D). El shape del GradeRecord depende
-- del scale (BE-5B.3), y la coherencia kind/scale la valida el servicio
-- (EVAL_KIND_SCALE_MISMATCH).
--
-- Multi-tenant via tenant_id + Hibernate's @TenantId discriminator
-- on TenantAwareEntity. Soft-delete via `deleted` flag.
-- =====================================================================

CREATE TABLE edushift.evaluations (
    id                          uuid          PRIMARY KEY,
    tenant_id                   uuid          NOT NULL,
    public_uuid                 uuid          NOT NULL,
    created_at                  timestamptz   NOT NULL,
    updated_at                  timestamptz   NOT NULL,
    created_by                  uuid,
    updated_by                  uuid,
    deleted                     boolean       NOT NULL DEFAULT false,
    deleted_at                  timestamptz,

    teacher_assignment_id       uuid          NOT NULL,
    unit_id                     uuid,
    learning_session_id         uuid,

    -- Pedagoógico.
    kind                        varchar(16)   NOT NULL,
    name                        varchar(200)  NOT NULL,
    description                 text,
    weight                      numeric(5,2)  NOT NULL DEFAULT 1.00,
    scheduled_date              date          NOT NULL,
    due_date                    date,

    -- Scoring.
    scale                       varchar(24)   NOT NULL,

    -- Lifecycle.
    status                      varchar(16)   NOT NULL DEFAULT 'DRAFT',
    published_at                timestamptz,
    closed_at                   timestamptz,

    is_active                   boolean       NOT NULL DEFAULT true,

    CONSTRAINT uk_evaluations_public_uuid
        UNIQUE (public_uuid),

    -- Case-insensitive uniqueness on (assignment, name) for non-deleted rows.
    -- Implemented with a unique index on lower(name) to be case-insensitive
    -- without relying on citext extension.
    CONSTRAINT chk_evaluations_kind
        CHECK (kind IN ('TASK','QUIZ','EXAM','RUBRIC','COMPETENCY')),
    CONSTRAINT chk_evaluations_scale
        CHECK (scale IN ('SCORE_0_20','LITERAL_AD','LITERAL_NA','LITERAL_A_B_C_D')),
    CONSTRAINT chk_evaluations_status
        CHECK (status IN ('DRAFT','PUBLISHED','CLOSED')),

    -- weight 0..999.99 — un 0.00 se permite (no ponderado, sólo informativo).
    CONSTRAINT chk_evaluations_weight
        CHECK (weight BETWEEN 0.00 AND 999.99),

    -- due_date >= scheduled_date when both present.
    CONSTRAINT chk_evaluations_date_window
        CHECK (due_date IS NULL OR due_date >= scheduled_date),

    -- Coherence between status and timestamps.
    CONSTRAINT chk_evaluations_status_timestamps
        CHECK (
            (status = 'DRAFT'
             AND published_at IS NULL AND closed_at IS NULL)
         OR (status = 'PUBLISHED'
             AND published_at IS NOT NULL AND closed_at IS NULL)
         OR (status = 'CLOSED'
             AND published_at IS NOT NULL AND closed_at IS NOT NULL)
        ),

    CONSTRAINT fk_evaluations_assignment
        FOREIGN KEY (teacher_assignment_id)
        REFERENCES edushift.teacher_assignments (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_evaluations_unit
        FOREIGN KEY (unit_id)
        REFERENCES edushift.academic_units (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_evaluations_learning_session
        FOREIGN KEY (learning_session_id)
        REFERENCES edushift.learning_sessions (id)
        ON DELETE RESTRICT
);

-- Hot path: list evaluations per assignment ordered by scheduled_date desc.
-- Drives the assignment-scoped tab in FE-5B.1.
CREATE INDEX idx_evaluations_tenant_assignment_date
    ON edushift.evaluations (tenant_id, teacher_assignment_id, scheduled_date DESC)
    WHERE NOT deleted;

-- Hot path: filter by status + date across the tenant (grade book queries).
CREATE INDEX idx_evaluations_tenant_status_date
    ON edushift.evaluations (tenant_id, status, scheduled_date)
    WHERE NOT deleted;

-- Reverse lookup: "what evaluations reference this unit" — drives
-- EVAL_UNIT_IN_USE check + "is this unit in use by evaluations?" from
-- the FE-5B.4 grade book.
CREATE INDEX idx_evaluations_tenant_unit
    ON edushift.evaluations (tenant_id, unit_id)
    WHERE NOT deleted AND unit_id IS NOT NULL;

-- Reverse lookup: "what evaluations reference this learning session".
CREATE INDEX idx_evaluations_tenant_session
    ON edushift.evaluations (tenant_id, learning_session_id)
    WHERE NOT deleted AND learning_session_id IS NOT NULL;

-- Case-insensitive uniqueness for (tenant, assignment, name) on non-deleted
-- rows. Implemented via a unique index on lower(name) — Postgres way to
-- emulate citext without enabling the extension.
CREATE UNIQUE INDEX uk_evaluations_tenant_assignment_name_ci
    ON edushift.evaluations (tenant_id, teacher_assignment_id, lower(name))
    WHERE NOT deleted;

CREATE TRIGGER set_updated_at_evaluations
    BEFORE UPDATE ON edushift.evaluations
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON TABLE  edushift.evaluations                       IS 'Graded item authored against a TeacherAssignment (Sprint 5B / BE-5B.1).';
COMMENT ON COLUMN edushift.evaluations.kind                   IS 'Pedagogical kind: TASK | QUIZ | EXAM | RUBRIC | COMPETENCY.';
COMMENT ON COLUMN edushift.evaluations.weight                 IS 'Ponderación para el promedio final. NUMERIC(5,2) en [0, 999.99]. Default 1.00.';
COMMENT ON COLUMN edushift.evaluations.scale                  IS 'Escala de calificación: SCORE_0_20 (numérica) o LITERAL_* (cualitativa). BE-5B.3 valida coherencia kind/scale.';
COMMENT ON COLUMN edushift.evaluations.status                 IS 'Lifecycle: DRAFT -> PUBLISHED -> CLOSED. Terminal en CLOSED (ADR-5B.7).';
COMMENT ON COLUMN edushift.evaluations.published_at           IS 'Set por POST /evaluations/{uuid}/publish. Inmutable post-publish salvo close.';
COMMENT ON COLUMN edushift.evaluations.closed_at              IS 'Set por POST /evaluations/{uuid}/close. Tras CLOSED, la row es read-only.';
