-- =====================================================================
-- V28 - Sprint 5B / BE-5B.4 - Evaluations.evaluation_rubric (M:N)
--
-- Join table between `evaluations` and `rubrics`. The relation is modelled
-- as M:N (per ADR-5B.6 in the sprint plan: "use M:N table to keep the
-- door open to multi-rubric per evaluation in the future, DEBT-EVAL-6"),
-- but the unique index on `evaluation_id` enforces 0..1 rubrics per
-- evaluation in the MVP — the second association replaces the first
-- (POST /v1/.../rubric is upsert-style).
--
-- The row carries audit cols (`created_at`, `created_by`) so we know
-- which admin attached the rubric. We don't carry `updated_at`/
-- `deleted` because the relation is immutable: any change is a
-- delete + reinsert. No public_uuid: the relation is not a first-class
-- resource — it is operated via the parent evaluation
-- (`POST/GET/DELETE /evaluations/{publicUuid}/rubric`).
--
-- ON DELETE strategy:
--  - evaluation_id → CASCADE (an evaluation deleted should drop its
--    rubric link transparently; the EVAL_HAS_GRADES guard already
--    prevents deletes when there are graded students).
--  - rubric_id → RESTRICT (deleting a rubric used by an evaluation
--    should fail loudly with RUBRIC_IN_USE_BY_EVALUATIONS at the
--    service layer).
--
-- Multi-tenant: redundant `tenant_id` column to enable per-tenant
-- partial indexes and to make tenant-isolation IT queries explicit.
-- The Hibernate @TenantId discriminator on EvaluationRubricEntity
-- still drives auto-filtering at the JPA layer.
-- =====================================================================

CREATE TABLE edushift.evaluation_rubric (
    id                          uuid          PRIMARY KEY,
    tenant_id                   uuid          NOT NULL,

    created_at                  timestamptz   NOT NULL,
    updated_at                  timestamptz   NOT NULL,
    created_by                  uuid,
    updated_by                  uuid,
    deleted                     boolean       NOT NULL DEFAULT false,
    deleted_at                  timestamptz,

    evaluation_id               uuid          NOT NULL,
    rubric_id                   uuid          NOT NULL,

    CONSTRAINT chk_evaluation_rubric_deleted_at_consistent
        CHECK (
            (deleted = false AND deleted_at IS NULL)
            OR (deleted = true AND deleted_at IS NOT NULL)
        ),

    CONSTRAINT fk_evaluation_rubric_evaluation
        FOREIGN KEY (evaluation_id)
        REFERENCES edushift.evaluations (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_evaluation_rubric_rubric
        FOREIGN KEY (rubric_id)
        REFERENCES edushift.rubrics (id)
        ON DELETE RESTRICT
);

-- Enforces 0..1 rubric per evaluation on non-deleted rows. The service
-- layer relies on this for upsert semantics (POST /rubric replaces).
CREATE UNIQUE INDEX uk_evaluation_rubric_evaluation
    ON edushift.evaluation_rubric (evaluation_id)
    WHERE NOT deleted;

-- Reverse lookup: "what evaluations use this rubric?" — drives
-- the RUBRIC_IN_USE_BY_EVALUATIONS guard in
-- RubricServiceImpl.deleteRubric (BE-5B.2 placeholder, lit up here).
CREATE INDEX idx_evaluation_rubric_tenant_rubric
    ON edushift.evaluation_rubric (tenant_id, rubric_id)
    WHERE NOT deleted;

CREATE TRIGGER set_updated_at_evaluation_rubric
    BEFORE UPDATE ON edushift.evaluation_rubric
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON TABLE  edushift.evaluation_rubric              IS 'M:N association between Evaluation and Rubric (Sprint 5B / BE-5B.4). MVP: 0..1 per evaluation via uk_evaluation_rubric_evaluation.';
COMMENT ON COLUMN edushift.evaluation_rubric.evaluation_id IS 'FK to evaluations.id. CASCADE on parent delete (the EVAL_HAS_GRADES guard at the service layer already protects evaluations with grades).';
COMMENT ON COLUMN edushift.evaluation_rubric.rubric_id     IS 'FK to rubrics.id. RESTRICT on parent delete: deleting a rubric attached to an evaluation surfaces RUBRIC_IN_USE_BY_EVALUATIONS (409) instead of orphaning the link.';
