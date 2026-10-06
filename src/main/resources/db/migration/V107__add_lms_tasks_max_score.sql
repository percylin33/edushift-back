-- V107 — LMS tasks: configurable max_score for grading (BUG maxScore saved as 0)

ALTER TABLE edushift.lms_tasks
    ADD COLUMN max_score smallint NOT NULL DEFAULT 100;

ALTER TABLE edushift.lms_tasks
    ADD CONSTRAINT chk_lms_tasks_max_score_range
        CHECK (max_score >= 1 AND max_score <= 1000);

COMMENT ON COLUMN edushift.lms_tasks.max_score IS
    'Maximum grade points for this task (1..1000). Used by SubmissionService.grade validation.';

-- Widen submission grade ceiling so tasks may use max_score > 100.
ALTER TABLE edushift.lms_submissions
    DROP CONSTRAINT chk_lms_submissions_grade_range;

ALTER TABLE edushift.lms_submissions
    ADD CONSTRAINT chk_lms_submissions_grade_range
        CHECK (grade IS NULL OR (grade >= 0 AND grade <= 1000));
