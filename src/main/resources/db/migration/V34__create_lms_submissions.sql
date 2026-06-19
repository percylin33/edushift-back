-- =====================================================================
-- V34 - Sprint 7a / BE-7a.2 - LMS submissions + revisions
--
-- Una entrega por (task, student). El student o un parent vinculado
-- puede entregar. Re-entregas sustituyen la fila actual pero el
-- payload anterior se conserva en lms_submission_revisions (audit).
--
-- Tablas:
--   1. lms_submissions            - Current submission per (task, student).
--   2. lms_submission_revisions   - Snapshot del payload previo en re-submit.
--
-- Decisiones (ver docs/modules/lms-tasks.md y sprint-07a):
--   * D-TSK-01 - UNIQUE (task_id, student_user_id) garantiza "one
--                current submission per (task, student)". Re-submit
--                update + insert revision en la misma transaccion.
--   * D-TSK-02 - submitter_user_id y student_user_id son distintos
--                en el parent flow (submitter=parent, student=hijo).
--   * D-TSK-03 - attachment_public_uuid soft FK a lms_file_objects.
--   * D-TSK-05 - Submissions sobreviven el soft-delete del task
--                padre (orphan pattern). FK a lms_tasks(id) con
--                ON DELETE RESTRICT (porque submissions son rows
--                tenant-scoped con su propio soft-delete; el task
--                padre se soft-deletea sin tocar esta tabla).
--
-- Multi-tenant: tenant_id + Hibernate @TenantId discriminator.
-- Soft-delete: flag `deleted` + `deleted_at` con CHECK de coherencia.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. lms_submissions
-- ---------------------------------------------------------------------
CREATE TABLE edushift.lms_submissions (
    id                          uuid          PRIMARY KEY,
    tenant_id                   uuid          NOT NULL,
    public_uuid                 uuid          NOT NULL,
    created_at                  timestamptz   NOT NULL,
    updated_at                  timestamptz   NOT NULL,
    created_by                  uuid,
    updated_by                  uuid,
    deleted                     boolean       NOT NULL DEFAULT false,
    deleted_at                  timestamptz,

    task_id                     uuid          NOT NULL,
    student_user_id             uuid          NOT NULL,
    submitter_user_id           uuid          NOT NULL,

    text_body                   text,
    attachment_public_uuid      uuid,

    status                      varchar(16)   NOT NULL,   -- SUBMITTED, GRADED, SOFT_DELETED
    grade                       smallint,
    feedback                    varchar(2000),
    graded_by_user_id           uuid,
    graded_at                   timestamptz,

    CONSTRAINT uk_lms_submissions_public_uuid
        UNIQUE (public_uuid),

    -- (D-TSK-01) At most one current (non-deleted) submission per
    -- (task, student). Re-submissions update the existing row in the
    -- same transaction that inserts the revision.
    CONSTRAINT uq_lms_submissions_task_student
        UNIQUE (task_id, student_user_id),

    CONSTRAINT chk_lms_submissions_status
        CHECK (status IN ('SUBMITTED', 'GRADED', 'SOFT_DELETED')),

    -- grade range 0..100; NULL until graded.
    CONSTRAINT chk_lms_submissions_grade_range
        CHECK (grade IS NULL OR (grade >= 0 AND grade <= 100)),

    -- GRADED requiere grade y graded_by/graded_at consistentes.
    CONSTRAINT chk_lms_submissions_graded_consistent
        CHECK (
            (status = 'GRADED' AND grade IS NOT NULL AND graded_by_user_id IS NOT NULL AND graded_at IS NOT NULL)
         OR (status <> 'GRADED' AND graded_by_user_id IS NULL AND graded_at IS NULL)
        ),

    -- text_body O attachment_public_uuid (al menos uno). No se
    -- permite una entrega completamente vacia (UX: la entrega debe
    -- tener contenido). El CHECK bloquea ambas NULL.
    CONSTRAINT chk_lms_submissions_payload_not_empty
        CHECK (
            text_body IS NOT NULL
         OR attachment_public_uuid IS NOT NULL
        ),

    CONSTRAINT chk_lms_submissions_deleted_at_consistent
        CHECK (
            (deleted = false AND deleted_at IS NULL)
         OR (deleted = true  AND deleted_at IS NOT NULL)
        ),

    -- FK a tasks. (D-TSK-05) ON DELETE RESTRICT — el soft-delete del
    -- task padre no remueve las submissions (orphan pattern).
    CONSTRAINT fk_lms_submissions_task
        FOREIGN KEY (task_id)
        REFERENCES edushift.lms_tasks (id)
        ON DELETE RESTRICT,

    -- student_user_id y submitter_user_id referencian users.public_uuid
    CONSTRAINT fk_lms_submissions_student
        FOREIGN KEY (student_user_id)
        REFERENCES edushift.users (public_uuid)
        ON DELETE RESTRICT,

    CONSTRAINT fk_lms_submissions_submitter
        FOREIGN KEY (submitter_user_id)
        REFERENCES edushift.users (public_uuid)
        ON DELETE RESTRICT,

    CONSTRAINT fk_lms_submissions_graded_by
        FOREIGN KEY (graded_by_user_id)
        REFERENCES edushift.users (public_uuid)
        ON DELETE RESTRICT,

    -- (D-TSK-03) attachment_public_uuid soft FK.
    CONSTRAINT fk_lms_submissions_attachment
        FOREIGN KEY (attachment_public_uuid)
        REFERENCES edushift.lms_file_objects (public_uuid)
        ON DELETE RESTRICT
);

-- Hot path: list submissions de un task (teacher view).
CREATE INDEX idx_lms_submissions_tenant_task
    ON edushift.lms_submissions (tenant_id, task_id)
    WHERE NOT deleted;

-- Hot path: "mis entregas" del student (parent flow usa el mismo
-- indice porque student_user_id es la columna lookup).
CREATE INDEX idx_lms_submissions_tenant_student
    ON edushift.lms_submissions (tenant_id, student_user_id, created_at DESC)
    WHERE NOT deleted;

-- Hot path: cleanup (DEBT-7A-18 future).
CREATE INDEX idx_lms_submissions_tenant_attachment
    ON edushift.lms_submissions (tenant_id, attachment_public_uuid)
    WHERE NOT deleted AND attachment_public_uuid IS NOT NULL;

CREATE TRIGGER set_updated_at_lms_submissions
    BEFORE UPDATE ON edushift.lms_submissions
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON TABLE  edushift.lms_submissions                        IS 'LMS current submission per (task, student). Sprint 7a / BE-7a.2. Re-submits snapshot to lms_submission_revisions.';
COMMENT ON COLUMN edushift.lms_submissions.task_id                 IS 'FK -> lms_tasks(id). ON DELETE RESTRICT (orphan pattern, D-TSK-05).';
COMMENT ON COLUMN edushift.lms_submissions.student_user_id         IS 'public_uuid of the target student (recipient of the grade). FK -> users.public_uuid. Differs from submitter_user_id in the parent flow.';
COMMENT ON COLUMN edushift.lms_submissions.submitter_user_id       IS 'public_uuid of the user that pressed "submit". Equal to student_user_id for self-submit; equal to the parent for parent-on-behalf.';
COMMENT ON COLUMN edushift.lms_submissions.status                  IS 'SUBMITTED (initial) | GRADED (teacher recorded a grade) | SOFT_DELETED (rare admin override).';
COMMENT ON COLUMN edushift.lms_submissions.grade                   IS '0..100. NULL until graded. CHECK chk_lms_submissions_grade_range.';
COMMENT ON COLUMN edushift.lms_submissions.feedback                IS 'Free text up to 2000 chars (DTO cap). Teacher-written.';
COMMENT ON COLUMN edushift.lms_submissions.graded_by_user_id       IS 'public_uuid of the user that graded. FK -> users.public_uuid. NULL until graded. CHECK chk_lms_submissions_graded_consistent.';


-- ---------------------------------------------------------------------
-- 2. lms_submission_revisions
-- ---------------------------------------------------------------------
-- Audit trail de re-submissions. Append-only: cada vez que un
-- (task, student) re-entrega, el payload actual de lms_submissions
-- se copia aqui ANTES del UPDATE.
CREATE TABLE edushift.lms_submission_revisions (
    id                          uuid          PRIMARY KEY,
    tenant_id                   uuid          NOT NULL,
    created_at                  timestamptz   NOT NULL,
    updated_at                  timestamptz   NOT NULL,
    -- Sin deleted: append-only. Una revision no se borra nunca.
    deleted                     boolean       NOT NULL DEFAULT false,
    deleted_at                  timestamptz,

    submission_id               uuid          NOT NULL,
    revision_number             smallint      NOT NULL,
    text_body                   text,
    attachment_public_uuid      uuid,
    created_by_user_id          uuid          NOT NULL,

    CONSTRAINT chk_lms_submission_revisions_number_positive
        CHECK (revision_number > 0),

    -- Mantenemos UNIQUE (submission_id, revision_number) para que la
    -- numeracion sea monotona por submission. La numeracion la
    -- calcula el service (MAX(revision_number) + 1) en una sola
    -- transaccion.
    CONSTRAINT uq_lms_submission_revisions_submission_number
        UNIQUE (submission_id, revision_number),

    CONSTRAINT chk_lms_submission_revisions_payload_not_empty
        CHECK (
            text_body IS NOT NULL
         OR attachment_public_uuid IS NOT NULL
        ),

    -- ON DELETE CASCADE: si el padre (submission) se hard-deletea,
    -- las revisions tambien (raro: nunca se hard-deletea en v1, el
    -- SOFT_DELETED flag lo cubre). Mantenemos la cascada por
    -- seguridad.
    CONSTRAINT fk_lms_submission_revisions_submission
        FOREIGN KEY (submission_id)
        REFERENCES edushift.lms_submissions (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_lms_submission_revisions_created_by
        FOREIGN KEY (created_by_user_id)
        REFERENCES edushift.users (public_uuid)
        ON DELETE RESTRICT
);

-- Hot path: listar revisions de una submission (FE history view).
CREATE INDEX idx_lms_submission_revisions_tenant_submission
    ON edushift.lms_submission_revisions (tenant_id, submission_id, revision_number DESC);

CREATE TRIGGER set_updated_at_lms_submission_revisions
    BEFORE UPDATE ON edushift.lms_submission_revisions
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON TABLE  edushift.lms_submission_revisions                       IS 'Audit trail of LMS re-submissions. Append-only. Sprint 7a / BE-7a.2.';
COMMENT ON COLUMN edushift.lms_submission_revisions.revision_number       IS '1-based monotonic per submission_id. Computed by the service as MAX(revision_number)+1 inside the same transaction as the re-submit.';
COMMENT ON COLUMN edushift.lms_submission_revisions.text_body              IS 'Snapshot of lms_submissions.text_body BEFORE the re-submit. NULL allowed when the original was attachment-only.';
COMMENT ON COLUMN edushift.lms_submission_revisions.attachment_public_uuid IS 'Snapshot of lms_submissions.attachment_public_uuid BEFORE the re-submit. NULL allowed when the original was text-only.';
