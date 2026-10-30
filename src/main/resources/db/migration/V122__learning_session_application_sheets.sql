-- =============================================================================
-- V122 — Ficha de aplicación por sesión y grado
-- One printable worksheet per learning session, kind and grade.
-- 3rd and 4th grade never share a row or a printed page.
-- =============================================================================

CREATE TABLE edushift.learning_session_application_sheets (
    id                      uuid          PRIMARY KEY,
    tenant_id               uuid          NOT NULL,
    public_uuid             uuid          NOT NULL,
    created_at              timestamptz   NOT NULL,
    updated_at              timestamptz   NOT NULL,
    created_by              uuid,
    updated_by              uuid,
    deleted                 boolean       NOT NULL DEFAULT false,
    deleted_at              timestamptz,

    learning_session_id     uuid          NOT NULL,
    kind                    varchar(16)   NOT NULL,
    grade                   varchar(80)   NOT NULL,
    sheet_number            integer       NOT NULL,
    title                   varchar(200)  NOT NULL,
    content                 jsonb         NOT NULL,

    CONSTRAINT uk_ls_application_sheets_public_uuid
        UNIQUE (public_uuid),

    CONSTRAINT chk_ls_application_sheets_deleted_at_consistent CHECK (
        (deleted = false AND deleted_at IS NULL)
        OR (deleted = true AND deleted_at IS NOT NULL)
    ),

    CONSTRAINT chk_ls_application_sheets_kind
        CHECK (kind IN ('LEARNING', 'DIAGNOSTIC')),

    CONSTRAINT chk_ls_application_sheets_number
        CHECK (sheet_number >= 1),

    CONSTRAINT fk_ls_application_sheets_session
        FOREIGN KEY (learning_session_id)
        REFERENCES edushift.learning_sessions (id)
        ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uk_ls_application_sheets_session_kind_grade
    ON edushift.learning_session_application_sheets (learning_session_id, kind, grade)
    WHERE deleted = false;

CREATE INDEX idx_ls_application_sheets_tenant_session
    ON edushift.learning_session_application_sheets (tenant_id, learning_session_id)
    WHERE deleted = false;

CREATE TRIGGER set_updated_at_ls_application_sheets
    BEFORE UPDATE ON edushift.learning_session_application_sheets
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON TABLE edushift.learning_session_application_sheets IS
    'Printable student worksheet for one learning session and one grade. Not filled in by the student inside the app.';
COMMENT ON COLUMN edushift.learning_session_application_sheets.kind IS
    'LEARNING = ficha de aprendizaje. DIAGNOSTIC = evaluación diagnóstica del docente.';
COMMENT ON COLUMN edushift.learning_session_application_sheets.grade IS
    'Single grade label, e.g. 3.er Grado. Another grade is another row.';
COMMENT ON COLUMN edushift.learning_session_application_sheets.content IS
    'JSONB written for this grade only: warmup, purpose, activities with LINE/CHECKBOX/CHOICE items, or the diagnostic instrument.';

ALTER TABLE edushift.ai_generations
    DROP CONSTRAINT IF EXISTS chk_ai_generations_feature;

ALTER TABLE edushift.ai_generations
    ADD CONSTRAINT chk_ai_generations_feature
        CHECK (feature IN (
            'QUIZ_QUESTION_SUGGEST',
            'RUBRIC_SUGGEST',
            'SESSION_OUTLINE_SUGGEST',
            'APPLICATION_SHEET_SUGGEST',
            'OTHER'
        ));
