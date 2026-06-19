-- =====================================================================
-- V35 - Sprint 7b / BE-7b.0 - LMS Quizzes module (5 tablas)
--
-- Quizzes automaticos (multiple choice, true/false, short answer)
-- con auto-grading de MC/TF y grading manual de short-answers.
-- Sigue el mismo patron que lms_tasks + lms_submissions: una
-- tabla "header" (lms_quizzes), una tabla de detalle (lms_quiz_questions)
-- y otra de intentos + respuestas (lms_quiz_attempts + lms_quiz_answers).
--
-- Tablas:
--   1. lms_quizzes           - Quiz header (per-section).
--   2. lms_quiz_questions    - Banco de preguntas del quiz (orden + tipo).
--   3. lms_quiz_options      - Opciones de respuesta (solo MC). Para TF
--                              las opciones son implicitas (true/false).
--   4. lms_quiz_attempts     - Intento del alumno (per (quiz, student)).
--   5. lms_quiz_answers      - Respuestas del intento (per (attempt, question)).
--
-- Decisiones (ver sprint-07b-lms-intelligence.md y los ADRs 7B.1-7B.4
-- que se resuelven al arrancar el sprint real):
--   * D-QUIZ-01 - Quiz pertenece a una section. FK a sections(id) con
--                 ON DELETE RESTRICT (mismo patron que lms_tasks).
--   * D-QUIZ-02 - Quiz tiene lifecycle: DRAFT → PUBLISHED → CLOSED.
--                 Estado persistido en `status` (string + CHECK).
--                 DRAFT es editable; PUBLISHED es inmutable (las preguntas
--                 se "congelan" — ver D-QUIZ-04); CLOSED bloquea nuevos
--                 attempts.
--   * D-QUIZ-03 - (quiz, student) tiene UN solo attempt row activo.
--                 Re-attempts crean nueva fila (incrementan `attempt_number`)
--                 según `attempts_allowed`. D-QUIZ-03-MVP: el limit se
--                 enforce en service layer, no en DB (DEBT-7B-1).
--   * D-QUIZ-04 - Las preguntas y opciones se freezan en PUBLISHED:
--                 crear un `lms_quiz_questions_snapshot` esta fuera de
--                 scope MVP (DEBT-7B-2). Si el docente edita una pregunta
--                 despues de PUBLISHED, los attempts existentes leen la
--                 pregunta ACTUAL (no la original). Documentado.
--   * D-QUIZ-05 - QuestionType enum: MC (multiple choice, 2-6 options),
--                 TF (true/false, opciones implicitas), SHORT_ANSWER
--                 (texto libre, graded manual). Persistido como string.
--   * D-QUIZ-06 - Para MC, la respuesta correcta es `is_correct=true` en
--                 `lms_quiz_options`. Para TF, el backend deriva
--                 isCorrect de `correct_boolean` en `lms_quiz_questions`.
--   * D-QUIZ-07 - SHORT_ANSWER no tiene opciones; el campo
--                 `expected_keywords` (text[]) permite matching simple
--                 de palabras clave (DEBT-7B-3 si se quiere fuzzy match).
--   * D-QUIZ-08 - `score` en attempt se calcula al submit (BE-7b.1);
--                 el campo existe desde ya para que la columna este
--                 estable y los reports de Sprint 10 la puedan leer.
--   * D-QUIZ-09 - Auto-grading de MC/TF se hace en BE-7b.1; las tablas
--                 estan listas para que el engine persista resultados
--                 en `lms_quiz_answers.points_awarded`.
--   * D-QUIZ-10 - Soft-delete en header (lms_quizzes) NO cascadea a
--                 attempts: orphan pattern (matches lms_tasks).
--   * D-QUIZ-11 - `time_limit_minutes` es opcional. Si se implementa
--                 enforcement honesto (ADR-7B.3), el backend usara un
--                 job scheduler para cerrar attempts expirados.
--                 MVP: el timer vive solo en el FE.
--
-- Multi-tenant: tenant_id + Hibernate @TenantId discriminator.
-- Soft-delete: flag `deleted` + `deleted_at` con CHECK de coherencia
-- (heredado del patron lms_tasks/lms_submissions).
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. lms_quizzes (header)
-- ---------------------------------------------------------------------
CREATE TABLE edushift.lms_quizzes (
    id                      uuid          PRIMARY KEY,
    tenant_id               uuid          NOT NULL,
    public_uuid             uuid          NOT NULL,
    created_at              timestamptz   NOT NULL,
    updated_at              timestamptz   NOT NULL,
    created_by              uuid,
    updated_by              uuid,
    deleted                 boolean       NOT NULL DEFAULT false,
    deleted_at              timestamptz,

    section_id              uuid          NOT NULL,
    title                   varchar(200)  NOT NULL,
    description             text,
    status                  varchar(16)   NOT NULL,   -- DRAFT, PUBLISHED, CLOSED
    max_score               smallint      NOT NULL DEFAULT 100,
    due_at                  timestamptz,
    time_limit_minutes      smallint,
    attempts_allowed        smallint      NOT NULL DEFAULT 1,
    published_at            timestamptz,
    closed_at               timestamptz,
    owner_user_id           uuid          NOT NULL,

    CONSTRAINT uk_lms_quizzes_public_uuid
        UNIQUE (public_uuid),

    -- title no puede quedar en blanco (CHECK redundante con @NotBlank del DTO).
    CONSTRAINT chk_lms_quizzes_title_not_blank
        CHECK (length(trim(title)) > 0),

    -- status solo permite los 3 valores del lifecycle.
    CONSTRAINT chk_lms_quizzes_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'CLOSED')),

    -- max_score 0..1000 (rango razonable: 0 = quiz de asistencia sin puntaje,
    -- 1000 = quiz con bonus points).
    CONSTRAINT chk_lms_quizzes_max_score_range
        CHECK (max_score >= 0 AND max_score <= 1000),

    -- time_limit_minutes NULL = sin limite; si no, 1..480 (8h max razonable).
    CONSTRAINT chk_lms_quizzes_time_limit_range
        CHECK (time_limit_minutes IS NULL
            OR (time_limit_minutes >= 1 AND time_limit_minutes <= 480)),

    -- attempts_allowed 1..10 (mas que 10 intentos no tiene sentido academico).
    CONSTRAINT chk_lms_quizzes_attempts_allowed_range
        CHECK (attempts_allowed >= 1 AND attempts_allowed <= 10),

    -- published_at solo es no-NULL si status >= PUBLISHED.
    CONSTRAINT chk_lms_quizzes_published_at_consistent
        CHECK (
            (status = 'DRAFT'     AND published_at IS NULL)
         OR (status IN ('PUBLISHED', 'CLOSED') AND published_at IS NOT NULL)
        ),

    -- closed_at solo es no-NULL si status = CLOSED.
    CONSTRAINT chk_lms_quizzes_closed_at_consistent
        CHECK (
            (status <> 'CLOSED' AND closed_at IS NULL)
         OR (status =  'CLOSED' AND closed_at IS NOT NULL)
        ),

    -- soft-delete coherente (patron estandar del proyecto).
    CONSTRAINT chk_lms_quizzes_deleted_at_consistent
        CHECK (
            (deleted = false AND deleted_at IS NULL)
         OR (deleted = true  AND deleted_at IS NOT NULL)
        ),

    -- FK a sections (RESTRICT, mismo patron que lms_tasks).
    CONSTRAINT fk_lms_quizzes_section
        FOREIGN KEY (section_id)
        REFERENCES edushift.sections (id)
        ON DELETE RESTRICT,

    -- FK a users (owner).
    CONSTRAINT fk_lms_quizzes_owner
        FOREIGN KEY (owner_user_id)
        REFERENCES edushift.users (public_uuid)
        ON DELETE RESTRICT
);

-- Hot path: listar quizzes de una seccion ordenados por due_at desc.
CREATE INDEX idx_lms_quizzes_tenant_section_due
    ON edushift.lms_quizzes (tenant_id, section_id, due_at DESC NULLS LAST)
    WHERE NOT deleted;

-- Hot path: quizzes del docente (dashboard).
CREATE INDEX idx_lms_quizzes_tenant_owner_created
    ON edushift.lms_quizzes (tenant_id, owner_user_id, created_at DESC)
    WHERE NOT deleted;

-- Hot path: quizzes por status (filtro "solo published" en la home del alumno).
CREATE INDEX idx_lms_quizzes_tenant_status_due
    ON edushift.lms_quizzes (tenant_id, status, due_at DESC NULLS LAST)
    WHERE NOT deleted;

CREATE TRIGGER set_updated_at_lms_quizzes
    BEFORE UPDATE ON edushift.lms_quizzes
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON TABLE  edushift.lms_quizzes                            IS 'LMS quiz header (Sprint 7b / BE-7b.0). Questions live in lms_quiz_questions, attempts in lms_quiz_attempts.';
COMMENT ON COLUMN edushift.lms_quizzes.section_id                 IS 'FK -> sections(id). One quiz belongs to exactly one section.';
COMMENT ON COLUMN edushift.lms_quizzes.title                       IS 'Required. CHECK chk_lms_quizzes_title_not_blank DB-enforced; @NotBlank in DTO.';
COMMENT ON COLUMN edushift.lms_quizzes.description                 IS 'Free-form text up to 10000 chars (DTO cap). Optional.';
COMMENT ON COLUMN edushift.lms_quizzes.status                      IS 'Lifecycle: DRAFT (editable) → PUBLISHED (frozen, accepts attempts) → CLOSED (no new attempts). Enforced by service layer; CHECK constrains valid values.';
COMMENT ON COLUMN edushift.lms_quizzes.max_score                   IS 'Max points for the quiz. Range 0..1000. Default 100. Used by BE-7b.1 grading engine to normalise percent.';
COMMENT ON COLUMN edushift.lms_quizzes.due_at                      IS 'Optional deadline. Students cannot start attempts past this. NULL = no deadline.';
COMMENT ON COLUMN edushift.lms_quizzes.time_limit_minutes          IS 'Optional per-attempt time limit in minutes (1..480). MVP: enforced only in FE (timer); backend enforcement via job scheduler is ADR-7B.3.';
COMMENT ON COLUMN edushift.lms_quizzes.attempts_allowed            IS 'Max number of attempts per (quiz, student). Range 1..10. Default 1. Enforced in service layer (DEBT-7B-1).';
COMMENT ON COLUMN edushift.lms_quizzes.published_at                IS 'Timestamp of DRAFT→PUBLISHED transition. NULL while in DRAFT.';
COMMENT ON COLUMN edushift.lms_quizzes.closed_at                   IS 'Timestamp of PUBLISHED→CLOSED transition. NULL unless status=CLOSED.';
COMMENT ON COLUMN edushift.lms_quizzes.owner_user_id               IS 'public_uuid of the user that created the quiz (TEACHER or TENANT_ADMIN). FK -> users.public_uuid.';


-- ---------------------------------------------------------------------
-- 2. lms_quiz_questions (banco de preguntas del quiz)
-- ---------------------------------------------------------------------
CREATE TABLE edushift.lms_quiz_questions (
    id                      uuid          PRIMARY KEY,
    tenant_id               uuid          NOT NULL,
    public_uuid             uuid          NOT NULL,
    created_at              timestamptz   NOT NULL,
    updated_at              timestamptz   NOT NULL,
    created_by              uuid,
    updated_by              uuid,
    deleted                 boolean       NOT NULL DEFAULT false,
    deleted_at              timestamptz,

    quiz_id                 uuid          NOT NULL,
    position                smallint      NOT NULL,   -- 1-based ordering
    question_type           varchar(16)   NOT NULL,   -- MC, TF, SHORT_ANSWER
    prompt                  text          NOT NULL,
    points                  smallint      NOT NULL DEFAULT 1,
    -- SHORT_ANSWER: palabras clave opcionales (lowercased). Match es
    -- substring case-insensitive (DEBT-7B-3: fuzzy match con LLM).
    expected_keywords       text[],
    -- TF: respuesta esperada (true/false). MC/SHORT_ANSWER: NULL.
    correct_boolean         boolean,

    CONSTRAINT uk_lms_quiz_questions_public_uuid
        UNIQUE (public_uuid),

    -- position unica por quiz (no se puede tener dos preguntas en la misma
    -- posicion). UNIQUE incluye `deleted` para que un re-create de la
    -- misma posicion no choque con soft-deleted rows.
    CONSTRAINT uq_lms_quiz_questions_quiz_position
        UNIQUE (quiz_id, position),

    -- question_type solo permite los 3 valores (D-QUIZ-05).
    CONSTRAINT chk_lms_quiz_questions_type
        CHECK (question_type IN ('MC', 'TF', 'SHORT_ANSWER')),

    -- position >= 1.
    CONSTRAINT chk_lms_quiz_questions_position_positive
        CHECK (position >= 1),

    -- points 1..100 (preguntas normales).
    CONSTRAINT chk_lms_quiz_questions_points_range
        CHECK (points >= 1 AND points <= 100),

    -- prompt no vacio.
    CONSTRAINT chk_lms_quiz_questions_prompt_not_blank
        CHECK (length(trim(prompt)) > 0),

    -- TF: correct_boolean NOT NULL. MC: NULL. SHORT_ANSWER: NULL
    -- (espera match contra expected_keywords).
    CONSTRAINT chk_lms_quiz_questions_correct_boolean_tf_only
        CHECK (
            (question_type = 'TF'           AND correct_boolean IS NOT NULL)
         OR (question_type IN ('MC', 'SHORT_ANSWER') AND correct_boolean IS NULL)
        ),

    -- expected_keywords: solo para SHORT_ANSWER. MC/TF usan otros
    -- mecanismos (options / correct_boolean).
    CONSTRAINT chk_lms_quiz_questions_expected_keywords_sa_only
        CHECK (
            (question_type = 'SHORT_ANSWER')
         OR (question_type IN ('MC', 'TF') AND expected_keywords IS NULL)
        ),

    CONSTRAINT chk_lms_quiz_questions_deleted_at_consistent
        CHECK (
            (deleted = false AND deleted_at IS NULL)
         OR (deleted = true  AND deleted_at IS NOT NULL)
        ),

    -- FK al quiz padre. ON DELETE RESTRICT (orphan pattern, mismo
    -- razonamiento que lms_submissions → lms_tasks).
    CONSTRAINT fk_lms_quiz_questions_quiz
        FOREIGN KEY (quiz_id)
        REFERENCES edushift.lms_quizzes (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_lms_quiz_questions_tenant_quiz_position
    ON edushift.lms_quiz_questions (tenant_id, quiz_id, position)
    WHERE NOT deleted;

CREATE TRIGGER set_updated_at_lms_quiz_questions
    BEFORE UPDATE ON edushift.lms_quiz_questions
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON TABLE  edushift.lms_quiz_questions                     IS 'Question bank rows for a quiz (Sprint 7b / BE-7b.0). Ordered by `position`. Options for MC live in lms_quiz_options; TF uses `correct_boolean`; SHORT_ANSWER uses `expected_keywords`.';
COMMENT ON COLUMN edushift.lms_quiz_questions.quiz_id              IS 'FK -> lms_quizzes(id). One question belongs to exactly one quiz.';
COMMENT ON COLUMN edushift.lms_quiz_questions.position              IS '1-based ordering. UNIQUE (quiz_id, position).';
COMMENT ON COLUMN edushift.lms_quiz_questions.question_type         IS 'MC (multiple choice, 2-6 options in lms_quiz_options), TF (true/false, correct_boolean), SHORT_ANSWER (text, manual grading).';
COMMENT ON COLUMN edushift.lms_quiz_questions.prompt                IS 'Question text. CHECK enforces non-blank.';
COMMENT ON COLUMN edushift.lms_quiz_questions.points                IS 'Points awarded for a correct answer. Range 1..100. Default 1.';
COMMENT ON COLUMN edushift.lms_quiz_questions.expected_keywords     IS 'SHORT_ANSWER only. Array of substrings to match (case-insensitive). NULL for MC/TF.';
COMMENT ON COLUMN edushift.lms_quiz_questions.correct_boolean       IS 'TF only. Expected true/false. NULL for MC/SHORT_ANSWER.';


-- ---------------------------------------------------------------------
-- 3. lms_quiz_options (opciones para MC)
-- ---------------------------------------------------------------------
CREATE TABLE edushift.lms_quiz_options (
    id                      uuid          PRIMARY KEY,
    tenant_id               uuid          NOT NULL,
    public_uuid             uuid          NOT NULL,
    created_at              timestamptz   NOT NULL,
    updated_at              timestamptz   NOT NULL,
    created_by              uuid,
    updated_by              uuid,
    deleted                 boolean       NOT NULL DEFAULT false,
    deleted_at              timestamptz,

    question_id             uuid          NOT NULL,
    position                smallint      NOT NULL,   -- 1-based ordering dentro de la pregunta
    label                   varchar(500)  NOT NULL,
    is_correct              boolean       NOT NULL DEFAULT false,

    CONSTRAINT uk_lms_quiz_options_public_uuid
        UNIQUE (public_uuid),

    CONSTRAINT uq_lms_quiz_options_question_position
        UNIQUE (question_id, position),

    CONSTRAINT chk_lms_quiz_options_position_positive
        CHECK (position >= 1),

    CONSTRAINT chk_lms_quiz_options_label_not_blank
        CHECK (length(trim(label)) > 0),

    CONSTRAINT chk_lms_quiz_options_deleted_at_consistent
        CHECK (
            (deleted = false AND deleted_at IS NULL)
         OR (deleted = true  AND deleted_at IS NOT NULL)
        ),

    -- FK a la pregunta. ON DELETE RESTRICT.
    CONSTRAINT fk_lms_quiz_options_question
        FOREIGN KEY (question_id)
        REFERENCES edushift.lms_quiz_questions (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_lms_quiz_options_tenant_question_position
    ON edushift.lms_quiz_options (tenant_id, question_id, position)
    WHERE NOT deleted;

CREATE TRIGGER set_updated_at_lms_quiz_options
    BEFORE UPDATE ON edushift.lms_quiz_options
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

-- Garantia de "exactly one correct option" en MC. El service layer
-- es responsable de mantener esto; este trigger defensivo lo enforce
-- a nivel DB y produce un error claro si alguien crea/edita una
-- pregunta MC que rompa la invariante.
CREATE OR REPLACE FUNCTION edushift.enforce_one_correct_mc_option()
RETURNS TRIGGER AS $$
DECLARE
    v_question_type  varchar(16);
    v_correct_count  integer;
    v_question_id    uuid;
BEGIN
    -- Tomamos question_id del NEW o OLD segun operacion.
    IF (TG_OP = 'DELETE') THEN
        v_question_id := OLD.question_id;
    ELSE
        v_question_id := NEW.question_id;
    END IF;

    SELECT question_type INTO v_question_type
        FROM edushift.lms_quiz_questions
        WHERE id = v_question_id AND NOT deleted;

    -- Solo enforced para MC. TF y SHORT_ANSWER se saltan el check.
    IF v_question_type = 'MC' THEN
        -- Contamos options correctas (no soft-deleted) para esa pregunta.
        -- Esto cubre los 3 casos: INSERT, UPDATE (cambia is_correct),
        -- DELETE (cascadea el check al padre).
        SELECT COUNT(*) INTO v_correct_count
            FROM edushift.lms_quiz_options
            WHERE question_id = v_question_id
              AND NOT deleted
              AND is_correct = true;

        -- El AFTER DELETE/INSERT/UPDATE compara 1 vs != 1.
        IF TG_OP = 'INSERT' THEN
            IF NEW.is_correct AND v_correct_count <> 1 THEN
                RAISE EXCEPTION 'MC question must have exactly one correct option (currently %)', v_correct_count
                    USING ERRCODE = '23514';
            END IF;
        ELSIF TG_OP = 'UPDATE' THEN
            -- v_correct_count incluye el estado NEW (after update). Si
            -- hay exactamente 1 OK. Si 0 (despues de poner is_correct=false)
            -- o >1 (duplicado) → error.
            IF v_correct_count <> 1 THEN
                RAISE EXCEPTION 'MC question must have exactly one correct option (currently %)', v_correct_count
                    USING ERRCODE = '23514';
            END IF;
        ELSIF TG_OP = 'DELETE' THEN
            -- Despues del delete, si la pregunta sigue existiendo y
            -- es MC, debe tener exactamente 1 correcta.
            IF NOT EXISTS (
                SELECT 1 FROM edushift.lms_quiz_questions
                WHERE id = v_question_id AND NOT deleted
            ) THEN
                RETURN NULL; -- la pregunta tambien se borro, no enforced.
            END IF;
            IF v_correct_count <> 1 THEN
                RAISE EXCEPTION 'MC question must have exactly one correct option (currently %)', v_correct_count
                    USING ERRCODE = '23514';
            END IF;
        END IF;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_lms_quiz_options_one_correct
    AFTER INSERT OR UPDATE OR DELETE ON edushift.lms_quiz_options
    FOR EACH ROW EXECUTE FUNCTION edushift.enforce_one_correct_mc_option();

COMMENT ON TABLE  edushift.lms_quiz_options                        IS 'Answer options for MC questions (Sprint 7b / BE-7b.0). TF and SHORT_ANSWER questions have zero rows here. Exactly one row per MC question must have is_correct=true (enforced by trigger enforce_one_correct_mc_option).';
COMMENT ON COLUMN edushift.lms_quiz_options.question_id            IS 'FK -> lms_quiz_questions(id). One option belongs to exactly one question.';
COMMENT ON COLUMN edushift.lms_quiz_options.position               IS '1-based ordering within the question. UNIQUE (question_id, position).';
COMMENT ON COLUMN edushift.lms_quiz_options.label                  IS 'Option text shown to the student. CHECK enforces non-blank. Max 500 chars.';
COMMENT ON COLUMN edushift.lms_quiz_options.is_correct             IS 'True if this is the correct option. Enforced: each MC question has exactly one is_correct=true (trigger enforce_one_correct_mc_option).';


-- ---------------------------------------------------------------------
-- 4. lms_quiz_attempts (intento del alumno)
-- ---------------------------------------------------------------------
CREATE TABLE edushift.lms_quiz_attempts (
    id                      uuid          PRIMARY KEY,
    tenant_id               uuid          NOT NULL,
    public_uuid             uuid          NOT NULL,
    created_at              timestamptz   NOT NULL,
    updated_at              timestamptz   NOT NULL,
    created_by              uuid,
    updated_by              uuid,
    deleted                 boolean       NOT NULL DEFAULT false,
    deleted_at              timestamptz,

    quiz_id                 uuid          NOT NULL,
    student_user_id         uuid          NOT NULL,
    submitter_user_id       uuid          NOT NULL,  -- distinto del student en parent-on-behalf
    attempt_number          smallint      NOT NULL,  -- 1-based, limitado por attempts_allowed
    status                  varchar(16)   NOT NULL,  -- IN_PROGRESS, SUBMITTED, AUTO_GRADED, GRADED, EXPIRED
    started_at              timestamptz   NOT NULL,
    submitted_at            timestamptz,
    expires_at              timestamptz,             -- started_at + time_limit_minutes
    auto_score              smallint,                -- 0..max_score (MC + TF auto-graded)
    manual_score            smallint,                -- 0..max_score (short-answer graded by teacher)
    score                   smallint,                -- auto_score + manual_score, persisted
    graded_by_user_id       uuid,
    graded_at               timestamptz,
    feedback                varchar(2000),

    CONSTRAINT uk_lms_quiz_attempts_public_uuid
        UNIQUE (public_uuid),

    -- (D-QUIZ-03) un row activo por (quiz, student, attempt_number).
    CONSTRAINT uq_lms_quiz_attempts_quiz_student_num
        UNIQUE (quiz_id, student_user_id, attempt_number),

    -- attempt_number >= 1.
    CONSTRAINT chk_lms_quiz_attempts_num_positive
        CHECK (attempt_number >= 1),

    -- status solo permite los 5 valores.
    CONSTRAINT chk_lms_quiz_attempts_status
        CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'AUTO_GRADED', 'GRADED', 'EXPIRED')),

    -- score 0..max_score del quiz padre. Se enforce en service layer
    -- (no podemos CHECK contra otra tabla sin triggers costosos).
    -- Constraint basico: score IS NULL o 0..1000 (rango generoso).
    CONSTRAINT chk_lms_quiz_attempts_score_range
        CHECK (score IS NULL OR (score >= 0 AND score <= 1000)),

    -- auto_score rango igual.
    CONSTRAINT chk_lms_quiz_attempts_auto_score_range
        CHECK (auto_score IS NULL OR (auto_score >= 0 AND auto_score <= 1000)),

    -- manual_score rango igual.
    CONSTRAINT chk_lms_quiz_attempts_manual_score_range
        CHECK (manual_score IS NULL OR (manual_score >= 0 AND manual_score <= 1000)),

    -- GRADED requiere graded_by_user_id y graded_at.
    CONSTRAINT chk_lms_quiz_attempts_graded_consistent
        CHECK (
            (status = 'GRADED' AND graded_by_user_id IS NOT NULL AND graded_at IS NOT NULL)
         OR (status <> 'GRADED' AND graded_by_user_id IS NULL AND graded_at IS NULL)
        ),

    -- submitted_at solo si status >= SUBMITTED.
    CONSTRAINT chk_lms_quiz_attempts_submitted_at_consistent
        CHECK (
            (status = 'IN_PROGRESS' AND submitted_at IS NULL)
         OR (status <> 'IN_PROGRESS' AND submitted_at IS NOT NULL)
        ),

    -- expires_at >= started_at (sanidad).
    CONSTRAINT chk_lms_quiz_attempts_expires_after_started
        CHECK (expires_at IS NULL OR expires_at > started_at),

    CONSTRAINT chk_lms_quiz_attempts_deleted_at_consistent
        CHECK (
            (deleted = false AND deleted_at IS NULL)
         OR (deleted = true  AND deleted_at IS NOT NULL)
        ),

    -- FK al quiz padre (RESTRICT, orphan pattern).
    CONSTRAINT fk_lms_quiz_attempts_quiz
        FOREIGN KEY (quiz_id)
        REFERENCES edushift.lms_quizzes (id)
        ON DELETE RESTRICT,

    -- FK al student (users.public_uuid).
    CONSTRAINT fk_lms_quiz_attempts_student
        FOREIGN KEY (student_user_id)
        REFERENCES edushift.users (public_uuid)
        ON DELETE RESTRICT,

    -- FK al submitter (puede ser el mismo student o un parent).
    CONSTRAINT fk_lms_quiz_attempts_submitter
        FOREIGN KEY (submitter_user_id)
        REFERENCES edushift.users (public_uuid)
        ON DELETE RESTRICT
);

-- Hot path: "mis quizzes" del alumno (tenant + student + created desc).
CREATE INDEX idx_lms_quiz_attempts_tenant_student_created
    ON edushift.lms_quiz_attempts (tenant_id, student_user_id, created_at DESC)
    WHERE NOT deleted;

-- Hot path: "ranking de submissions" del docente (por quiz).
CREATE INDEX idx_lms_quiz_attempts_tenant_quiz_status
    ON edushift.lms_quiz_attempts (tenant_id, quiz_id, status)
    WHERE NOT deleted;

-- Hot path: cleanup de attempts EXPIRED por job scheduler.
CREATE INDEX idx_lms_quiz_attempts_tenant_expires
    ON edushift.lms_quiz_attempts (tenant_id, expires_at)
    WHERE NOT deleted AND status = 'IN_PROGRESS';

CREATE TRIGGER set_updated_at_lms_quiz_attempts
    BEFORE UPDATE ON edushift.lms_quiz_attempts
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON TABLE  edushift.lms_quiz_attempts                       IS 'Student attempts at a quiz (Sprint 7b / BE-7b.0). One row per (quiz, student, attempt_number). Re-attempts create new rows, not updates.';
COMMENT ON COLUMN edushift.lms_quiz_attempts.quiz_id                IS 'FK -> lms_quizzes(id). One attempt is for exactly one quiz.';
COMMENT ON COLUMN edushift.lms_quiz_attempts.student_user_id        IS 'public_uuid of the student being graded. May differ from submitter (parent on-behalf flow).';
COMMENT ON COLUMN edushift.lms_quiz_attempts.submitter_user_id      IS 'public_uuid of the user that actually submitted (student or parent). FK -> users.public_uuid.';
COMMENT ON COLUMN edushift.lms_quiz_attempts.attempt_number         IS '1-based attempt number for this (quiz, student). UNIQUE. Enforced <= attempts_allowed in service layer (DEBT-7B-1).';
COMMENT ON COLUMN edushift.lms_quiz_attempts.status                 IS 'IN_PROGRESS (started, not submitted) → SUBMITTED (waiting for grading) → AUTO_GRADED (MC/TF done, waiting for short-answer manual grade) → GRADED (final score + feedback) → EXPIRED (time limit hit, BE-7b.4).';
COMMENT ON COLUMN edushift.lms_quiz_attempts.started_at             IS 'When the student opened the attempt. Drives expires_at calculation.';
COMMENT ON COLUMN edushift.lms_quiz_attempts.submitted_at           IS 'When the student clicked "Submit final". NULL while IN_PROGRESS.';
COMMENT ON COLUMN edushift.lms_quiz_attempts.expires_at             IS 'started_at + quiz.time_limit_minutes. NULL if the quiz has no time limit. Index supports cleanup job (BE-7b.4).';
COMMENT ON COLUMN edushift.lms_quiz_attempts.auto_score             IS 'Sum of points_awarded for MC + TF questions (auto-graded by BE-7b.1). NULL until first auto-grade pass.';
COMMENT ON COLUMN edushift.lms_quiz_attempts.manual_score           IS 'Sum of points_awarded for SHORT_ANSWER questions (manually graded by teacher). NULL until GRADED.';
COMMENT ON COLUMN edushift.lms_quiz_attempts.score                  IS 'auto_score + manual_score. Persisted so reports (Sprint 10) can query without recomputing. NULL until first grade.';
COMMENT ON COLUMN edushift.lms_quiz_attempts.graded_by_user_id      IS 'Teacher who closed the grading (set when status=GRADED). FK -> users.public_uuid.';
COMMENT ON COLUMN edushift.lms_quiz_attempts.graded_at              IS 'When the teacher finished grading (set when status=GRADED).';
COMMENT ON COLUMN edushift.lms_quiz_attempts.feedback               IS 'Teacher feedback (optional, up to 2000 chars). Visible to student + parent.';


-- ---------------------------------------------------------------------
-- 5. lms_quiz_answers (respuestas del intento)
-- ---------------------------------------------------------------------
CREATE TABLE edushift.lms_quiz_answers (
    id                      uuid          PRIMARY KEY,
    tenant_id               uuid          NOT NULL,
    public_uuid             uuid          NOT NULL,
    created_at              timestamptz   NOT NULL,
    updated_at              timestamptz   NOT NULL,
    created_by              uuid,
    updated_by              uuid,
    deleted                 boolean       NOT NULL DEFAULT false,
    deleted_at              timestamptz,

    attempt_id              uuid          NOT NULL,
    question_id             uuid          NOT NULL,
    -- MC: public_uuid de la opcion elegida. NULL para TF/SHORT_ANSWER.
    selected_option_id      uuid,
    -- TF: booleano respondido. NULL para MC/SHORT_ANSWER.
    selected_boolean        boolean,
    -- SHORT_ANSWER: texto libre. NULL para MC/TF.
    text_answer             text,
    -- null hasta grading; >=0 si graded. Para SHORT_ANSWER el valor
    -- es 0 (no match) o question.points (full match). El matching es
    -- substring case-insensitive contra `expected_keywords` (D-QUIZ-07).
    points_awarded          smallint,
    is_correct              boolean,
    graded_by_user_id       uuid,
    graded_at               timestamptz,

    CONSTRAINT uk_lms_quiz_answers_public_uuid
        UNIQUE (public_uuid),

    -- Una respuesta por (attempt, question). Re-submit del mismo attempt
    -- hace UPDATE in-place (no nueva fila). El snapshot se conserva en
    -- la fila misma via audit cols (created_at/updated_at).
    CONSTRAINT uq_lms_quiz_answers_attempt_question
        UNIQUE (attempt_id, question_id),

    -- points_awarded 0..100 (rango generoso para questions.points).
    CONSTRAINT chk_lms_quiz_answers_points_awarded_range
        CHECK (points_awarded IS NULL OR (points_awarded >= 0 AND points_awarded <= 100)),

    -- MC: selected_option_id NOT NULL. TF/SHORT_ANSWER: NULL.
    CONSTRAINT chk_lms_quiz_answers_selected_option_mc_only
        CHECK (
            (selected_option_id IS NOT NULL AND selected_boolean IS NULL AND text_answer IS NULL)
         OR (selected_option_id IS NULL)
        ),

    -- TF: selected_boolean NOT NULL. MC/SHORT_ANSWER: NULL.
    CONSTRAINT chk_lms_quiz_answers_selected_boolean_tf_only
        CHECK (
            (selected_boolean IS NOT NULL AND selected_option_id IS NULL AND text_answer IS NULL)
         OR (selected_boolean IS NULL)
        ),

    -- SHORT_ANSWER: text_answer NOT NULL. MC/TF: NULL.
    CONSTRAINT chk_lms_quiz_answers_text_answer_sa_only
        CHECK (
            (text_answer IS NOT NULL AND selected_option_id IS NULL AND selected_boolean IS NULL)
         OR (text_answer IS NULL)
        ),

    CONSTRAINT chk_lms_quiz_answers_deleted_at_consistent
        CHECK (
            (deleted = false AND deleted_at IS NULL)
         OR (deleted = true  AND deleted_at IS NOT NULL)
        ),

    -- FK al attempt. ON DELETE RESTRICT (orphan pattern).
    CONSTRAINT fk_lms_quiz_answers_attempt
        FOREIGN KEY (attempt_id)
        REFERENCES edushift.lms_quiz_attempts (id)
        ON DELETE RESTRICT,

    -- FK a la pregunta.
    CONSTRAINT fk_lms_quiz_answers_question
        FOREIGN KEY (question_id)
        REFERENCES edushift.lms_quiz_questions (id)
        ON DELETE RESTRICT,

    -- FK a la opcion elegida (solo MC, pero el FK es NOT NULL cuando
    -- la columna no es NULL — enforced por el FK parcial en el CHECK).
    -- (Esta FK se valida solo si selected_option_id IS NOT NULL, lo que
    -- el CHECK ya garantiza para MC.)
    CONSTRAINT fk_lms_quiz_answers_selected_option
        FOREIGN KEY (selected_option_id)
        REFERENCES edushift.lms_quiz_options (public_uuid)
        ON DELETE RESTRICT
);

-- Hot path: "todas las respuestas de un attempt" (grade detail view).
CREATE INDEX idx_lms_quiz_answers_tenant_attempt
    ON edushift.lms_quiz_answers (tenant_id, attempt_id)
    WHERE NOT deleted;

-- Hot path: "respuestas pendientes de grading manual" (teacher queue).
CREATE INDEX idx_lms_quiz_answers_tenant_pending
    ON edushift.lms_quiz_answers (tenant_id, graded_at)
    WHERE NOT deleted AND graded_at IS NULL AND text_answer IS NOT NULL;

CREATE TRIGGER set_updated_at_lms_quiz_answers
    BEFORE UPDATE ON edushift.lms_quiz_answers
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON TABLE  edushift.lms_quiz_answers                       IS 'Per-question answers for a quiz attempt (Sprint 7b / BE-7b.0). One row per (attempt, question). The `*_id`/`text_answer` columns are mutually exclusive (enforced by CHECK): MC uses selected_option_id, TF uses selected_boolean, SHORT_ANSWER uses text_answer.';
COMMENT ON COLUMN edushift.lms_quiz_answers.attempt_id              IS 'FK -> lms_quiz_attempts(id). One answer belongs to exactly one attempt.';
COMMENT ON COLUMN edushift.lms_quiz_answers.question_id             IS 'FK -> lms_quiz_questions(id). The question being answered. Soft FK to current row (D-QUIZ-04: edits to published questions are reflected here).';
COMMENT ON COLUMN edushift.lms_quiz_answers.selected_option_id      IS 'MC only. public_uuid of the chosen option. FK -> lms_quiz_options(public_uuid). NULL for TF/SHORT_ANSWER.';
COMMENT ON COLUMN edushift.lms_quiz_answers.selected_boolean        IS 'TF only. Student''s true/false answer. NULL for MC/SHORT_ANSWER.';
COMMENT ON COLUMN edushift.lms_quiz_answers.text_answer             IS 'SHORT_ANSWER only. Free-form text. NULL for MC/TF. Max 5000 chars (DTO cap).';
COMMENT ON COLUMN edushift.lms_quiz_answers.points_awarded          IS 'Graded points (0..100). NULL until first grade. For MC/TF set by BE-7b.1 auto-grader. For SHORT_ANSWER set by teacher.';
COMMENT ON COLUMN edushift.lms_quiz_answers.is_correct              IS 'Grading outcome flag. NULL until first grade. True if fully correct, false otherwise (partial credit not modeled in MVP).';
COMMENT ON COLUMN edushift.lms_quiz_answers.graded_by_user_id       IS 'Teacher who graded this answer (for SHORT_ANSWER). NULL for auto-graded MC/TF (graded_by=system). FK -> users.public_uuid.';
COMMENT ON COLUMN edushift.lms_quiz_answers.graded_at               IS 'Timestamp of grading. NULL until first grade. Index supports "pending grading" queue (idx_lms_quiz_answers_tenant_pending).';
