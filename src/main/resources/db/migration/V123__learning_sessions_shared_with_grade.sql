-- =============================================================================
-- V123 — Share a learning-session plan with other teachers of the same
-- course, grade and period. The row stays on its section; colleagues
-- copy it. Observations are not part of that view.
-- =============================================================================

ALTER TABLE edushift.learning_sessions
    ADD COLUMN shared_with_grade boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN edushift.learning_sessions.shared_with_grade IS
    'When true, other teachers with an active assignment in the same course, grade and period may view the plan (without observations) and copy it into their own section.';

CREATE INDEX idx_learning_sessions_shared_grade
    ON edushift.learning_sessions (tenant_id, scheduled_date)
    WHERE shared_with_grade = true AND deleted = false;
