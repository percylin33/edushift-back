-- =====================================================================
-- V41 - Sprint 7b / BE-7b.3 - LMS Quizzes ↔ Evaluations.Rubric bridge
--
-- A Quiz can optionally carry a qualitative rubric in addition to the
-- numeric MC/TF/SHORT_ANSWER grading. The rubric itself lives in the
-- existing `edushift.rubrics` table (V26) — this migration adds the
-- pivot column on `lms_quizzes` plus a derived `Evaluation` row that
-- anchors the per-student `grade_records` entries (Sprint 5b / V27
-- requires `grade_records.evaluation_id` NOT NULL, so we cannot
-- store the rubric grade directly on the quiz).
--
-- Decisiones (ver sprint-07b-lms-intelligence.md, ticket BE-7b.3):
--   * D-RUB-01 - The rubric is attached via `rubric_id` on the quiz
--                 header (NOT a M:N table). The MVP restricts each
--                 quiz to 0..1 rubric; the spec doesn't require
--                 multi-rubric, and a M:N table would just duplicate
--                 `evaluation_rubric` semantics with no new
--                 capability. Future-proofing note: if multi-rubric
--                 ever becomes a requirement, the cleanest migration
--                 is to repurpose `lms_quizzes_rubric` as a M:N
--                 table and move the column out of `lms_quizzes`.
--   * D-RUB-02 - `rubric_evaluation_id` points to a *derived*
--                 `edushift.evaluations` row (kind=QUIZ, scale=
--                 LITERAL_A_B_C_D, status=PUBLISHED) created by the
--                 service the first time the rubric is attached. The
--                 derived evaluation re-uses the quiz owner's active
--                 `teacher_assignment` on the same `section` (BE-7b.3
--                 decision A1). The derived evaluation's `name`
--                 carries the prefix "[QuizRubric] " so it surfaces
--                 unambiguously in the grade book UI.
--   * D-RUB-03 - Both columns are NULLable. A quiz without rubric
--                 behaves exactly as in BE-7b.0..2 (numeric only).
--   * D-RUB-04 - rubric_id → ON DELETE RESTRICT. Deleting a rubric
--                 used by a quiz surfaces RUBRIC_IN_USE_BY_QUIZZES
--                 (409) at the service layer, mirroring the existing
--                 RUBRIC_IN_USE_BY_EVALUATIONS guard.
--   * D-RUB-05 - rubric_evaluation_id → ON DELETE SET NULL. The
--                 derived evaluation is owned by the quiz lifecycle;
--                 if the quiz is soft-deleted, the derived evaluation
--                 stays in the grade book for historical transcript
--                 integrity. If the derived evaluation itself is
--                 hard-deleted (rare; protected by the EVAL_HAS_GRADES
--                 guard in EvaluationServiceImpl), the quiz falls back
--                 to numeric-only grading.
--   * D-RUB-06 - All audit cols are inherited from
--                 TenantAwareEntity / BaseEntity — no new columns
--                 needed (created_at/updated_at/deleted are
--                 inherited, and rubric metadata is captured in
--                 `grade_records.recorded_at` + `recorded_by_user_id`
--                 on the per-student write).
--
-- Multi-tenant: rubric_id and rubric_evaluation_id are tenant-scoped
-- via the FKs (rubrics and evaluations both carry tenant_id NOT NULL
-- and the application enforces @TenantId on their JPA entities).
-- =====================================================================

ALTER TABLE edushift.lms_quizzes
    ADD COLUMN IF NOT EXISTS rubric_id              uuid,
    ADD COLUMN IF NOT EXISTS rubric_evaluation_id   uuid;

-- The rubric FK. RESTRICT = deleting a rubric that still anchors a
-- quiz surfaces a clear 409 at the service layer instead of
-- orphaning the quiz.
ALTER TABLE edushift.lms_quizzes
    ADD CONSTRAINT fk_lms_quizzes_rubric
        FOREIGN KEY (rubric_id)
        REFERENCES edushift.rubrics (id)
        ON DELETE RESTRICT;

-- The derived-evaluation FK. SET NULL preserves the historical grade
-- rows under grade_records.evaluation_id if the derived evaluation
-- is ever removed; the quiz then falls back to numeric-only.
ALTER TABLE edushift.lms_quizzes
    ADD CONSTRAINT fk_lms_quizzes_rubric_evaluation
        FOREIGN KEY (rubric_evaluation_id)
        REFERENCES edushift.evaluations (id)
        ON DELETE SET NULL;

-- Hot path: "which quizzes use this rubric?" — drives the
-- RUBRIC_IN_USE_BY_QUIZZES guard in RubricServiceImpl.deleteRubric.
-- Tenant-led, partial index on non-deleted rows.
CREATE INDEX idx_lms_quizzes_tenant_rubric
    ON edushift.lms_quizzes (tenant_id, rubric_id)
    WHERE NOT deleted AND rubric_id IS NOT NULL;

-- Reverse: "which derived evaluations were created from a quiz?"
-- Used by the grade book filter ("show me the rubric grades that
-- originated from a quiz") and by cleanup jobs. Tenant-led, partial.
CREATE INDEX idx_lms_quizzes_tenant_rubric_evaluation
    ON edushift.lms_quizzes (tenant_id, rubric_evaluation_id)
    WHERE NOT deleted AND rubric_evaluation_id IS NOT NULL;

-- The trigger is inherited from lms_quizzes' base table (V35
-- creates set_updated_at_lms_quizzes). The two new columns ride on
-- the trigger for free.

COMMENT ON COLUMN edushift.lms_quizzes.rubric_id
    IS 'Optional FK to edushift.rubrics (V26). NULL → quiz is numeric-only. RESTRICT on delete (RUBRIC_IN_USE_BY_QUIZZES guard at service layer).';

COMMENT ON COLUMN edushift.lms_quizzes.rubric_evaluation_id
    IS 'Optional FK to a derived edushift.evaluations row (kind=QUIZ, scale=LITERAL_A_B_C_D) created on first attach. Anchors the per-student grade_records entries. SET NULL on delete (preserves grade book history).';
