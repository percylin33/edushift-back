-- =============================================================================
-- V156 — Competency evidence per student and evaluation.
-- Stores school-configured achievement labels, criterion evidence, and
-- descriptive conclusions without computing or claiming official grades.
-- Every update produces an immutable revision snapshot.
-- =============================================================================

CREATE UNIQUE INDEX IF NOT EXISTS uk_evaluations_id_tenant_comp_results
    ON edushift.evaluations (id, tenant_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_competencies_id_tenant_comp_results
    ON edushift.competencies (id, tenant_id);

CREATE TABLE edushift.student_competency_results (
    id                       uuid          PRIMARY KEY,
    tenant_id                uuid          NOT NULL,
    public_uuid              uuid          NOT NULL,
    evaluation_id            uuid          NOT NULL,
    student_id               uuid          NOT NULL,
    competency_id            uuid          NOT NULL,
    achievement_level        varchar(32)   NOT NULL,
    evidence_summary         text,
    descriptive_conclusion   text,
    criteria_results         jsonb         NOT NULL DEFAULT '[]'::jsonb,
    revision                 integer       NOT NULL DEFAULT 1,
    recorded_at              timestamptz   NOT NULL,
    recorded_by_user_id      uuid          NOT NULL,
    is_active                boolean       NOT NULL DEFAULT true,
    created_at               timestamptz   NOT NULL,
    updated_at               timestamptz   NOT NULL,
    created_by               uuid,
    updated_by               uuid,
    deleted                  boolean       NOT NULL DEFAULT false,
    deleted_at               timestamptz,
    CONSTRAINT uk_student_competency_result_public_uuid UNIQUE (public_uuid),
    CONSTRAINT uk_student_competency_results_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uk_student_competency_results_scope
        UNIQUE (tenant_id, evaluation_id, student_id, competency_id),
    CONSTRAINT fk_student_competency_results_evaluation_tenant
        FOREIGN KEY (evaluation_id, tenant_id)
        REFERENCES edushift.evaluations (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_competency_results_student_tenant
        FOREIGN KEY (student_id, tenant_id)
        REFERENCES edushift.students (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_competency_results_competency_tenant
        FOREIGN KEY (competency_id, tenant_id)
        REFERENCES edushift.competencies (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT chk_student_competency_results_level
        CHECK (length(btrim(achievement_level)) BETWEEN 1 AND 32),
    CONSTRAINT chk_student_competency_results_revision CHECK (revision > 0),
    CONSTRAINT chk_student_competency_results_criteria_array
        CHECK (jsonb_typeof(criteria_results) = 'array'),
    CONSTRAINT chk_student_competency_results_deleted CHECK (
        (deleted = false AND deleted_at IS NULL)
        OR (deleted = true AND deleted_at IS NOT NULL)
    )
);

CREATE INDEX idx_student_competency_results_tenant_student
    ON edushift.student_competency_results (tenant_id, student_id, evaluation_id)
    WHERE deleted = false AND is_active = true;
CREATE INDEX idx_student_competency_results_tenant_competency
    ON edushift.student_competency_results (tenant_id, competency_id, evaluation_id)
    WHERE deleted = false AND is_active = true;
CREATE TRIGGER set_updated_at_student_competency_results
    BEFORE UPDATE ON edushift.student_competency_results
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

CREATE TABLE edushift.student_competency_result_revisions (
    id                  uuid          PRIMARY KEY,
    tenant_id           uuid          NOT NULL,
    result_id           uuid          NOT NULL,
    revision            integer       NOT NULL,
    snapshot            jsonb         NOT NULL,
    changed_by_user_id  uuid          NOT NULL,
    changed_at          timestamptz   NOT NULL,
    created_at          timestamptz   NOT NULL,
    updated_at          timestamptz   NOT NULL,
    created_by          uuid,
    updated_by          uuid,
    deleted             boolean       NOT NULL DEFAULT false,
    deleted_at          timestamptz,
    CONSTRAINT uk_student_competency_result_revision
        UNIQUE (result_id, revision),
    CONSTRAINT fk_student_competency_result_revisions_result_tenant
        FOREIGN KEY (result_id, tenant_id)
        REFERENCES edushift.student_competency_results (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT chk_student_competency_result_revision_positive CHECK (revision > 0),
    CONSTRAINT chk_student_competency_result_revision_deleted CHECK (
        deleted = false AND deleted_at IS NULL
    )
);
CREATE INDEX idx_student_competency_result_revisions_result
    ON edushift.student_competency_result_revisions (tenant_id, result_id, revision DESC);

CREATE FUNCTION edushift.reject_student_competency_result_revision_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'student competency result revisions are immutable';
END;
$$;
CREATE TRIGGER immutable_student_competency_result_revisions
    BEFORE UPDATE OR DELETE ON edushift.student_competency_result_revisions
    FOR EACH ROW EXECUTE FUNCTION edushift.reject_student_competency_result_revision_mutation();

COMMENT ON TABLE edushift.student_competency_results IS
    'School-managed competency outcomes and evidence attached to a student evaluation.';
COMMENT ON COLUMN edushift.student_competency_results.achievement_level IS
    'School-configured label; not automatically interpreted as an official MINEDU grade.';
COMMENT ON TABLE edushift.student_competency_result_revisions IS
    'Immutable snapshots of every competency result change for academic traceability.';
