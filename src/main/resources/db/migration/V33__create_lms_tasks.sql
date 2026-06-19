-- =====================================================================
-- V33 - Sprint 7a / BE-7a.2 - LMS tasks module (tabla principal)
--
-- Asignaciones (assignments) que un teacher crea para una seccion.
-- Un task es un title + description + dueAt + un attachment opcional
-- y un flag allow_resubmission que controla si el student puede
-- re-entregar (ver V34 para submissions).
--
-- Tabla:
--   lms_tasks - Per-section assignment (header).
--
-- Decisiones (ver docs/modules/lms-tasks.md y sprint-07a):
--   * D-TSK-03 - attachment_public_uuid es un soft FK a
--                lms_file_objects(public_uuid) (no FK fisica: simplifica
--                cascade via reference_count).
--   * D-TSK-05 - Submissions sobreviven el soft-delete del task
--                (D-TSK-05). El task soft-delete NO elimina las
--                submissions, quedan "orphan".
--   * D-TSK-07 - Coarse @PreAuthorize + service-level enrollment
--                check. La columna section_id es el pivot para el
--                service-level check.
--   * Lifecycle: v1 colapsa DRAFT/PUBLISHED/CLOSED en un solo
--                "always published" con soft-delete (D-TSK-04 en
--                materials.md explica el trade-off).
--
-- Multi-tenant: tenant_id + Hibernate @TenantId discriminator.
-- Soft-delete: flag `deleted` + `deleted_at` con CHECK de coherencia.
-- =====================================================================

CREATE TABLE edushift.lms_tasks (
    id                          uuid          PRIMARY KEY,
    tenant_id                   uuid          NOT NULL,
    public_uuid                 uuid          NOT NULL,
    created_at                  timestamptz   NOT NULL,
    updated_at                  timestamptz   NOT NULL,
    created_by                  uuid,
    updated_by                  uuid,
    deleted                     boolean       NOT NULL DEFAULT false,
    deleted_at                  timestamptz,

    section_id                  uuid          NOT NULL,
    title                       varchar(200)  NOT NULL,
    description                 text,
    due_at                      timestamptz,
    attachment_public_uuid      uuid,
    owner_user_id               uuid          NOT NULL,
    allow_resubmission          boolean       NOT NULL DEFAULT true,

    CONSTRAINT uk_lms_tasks_public_uuid
        UNIQUE (public_uuid),

    -- title no puede quedar en blanco; CHECK redundante con el
    -- @NotBlank del DTO pero DB-enforced.
    CONSTRAINT chk_lms_tasks_title_not_blank
        CHECK (length(trim(title)) > 0),

    -- description libre, sin limite duro (text). El DTO limita a 10000.
    CONSTRAINT chk_lms_tasks_deleted_at_consistent
        CHECK (
            (deleted = false AND deleted_at IS NULL)
         OR (deleted = true  AND deleted_at IS NOT NULL)
        ),

    -- FK a sections (RESTRICT: no se borra una seccion con tasks).
    CONSTRAINT fk_lms_tasks_section
        FOREIGN KEY (section_id)
        REFERENCES edushift.sections (id)
        ON DELETE RESTRICT,

    -- owner_user_id referencia users.public_uuid.
    CONSTRAINT fk_lms_tasks_owner
        FOREIGN KEY (owner_user_id)
        REFERENCES edushift.users (public_uuid)
        ON DELETE RESTRICT,

    -- (D-TSK-03) attachment_public_uuid soft FK. No creamos la FK
    -- fisica para mantener el orden de migracion limpio: la tabla
    -- lms_file_objects existe (V31) antes de lms_tasks, pero la
    -- FK fisica obligaria a validar contra una vista
    -- tenant-scoped, lo que se resuelve en DEBT-7A-3.
    CONSTRAINT fk_lms_tasks_attachment
        FOREIGN KEY (attachment_public_uuid)
        REFERENCES edushift.lms_file_objects (public_uuid)
        ON DELETE RESTRICT
);

-- Hot path: listar tasks de una seccion ordenados por due_at desc
-- (calendario del teacher: "vencimientos proximos"). El include de
-- NULL due_at lo cubre el ORDER BY (los NULL van al final).
CREATE INDEX idx_lms_tasks_tenant_section_due
    ON edushift.lms_tasks (tenant_id, section_id, due_at DESC NULLS LAST)
    WHERE NOT deleted;

-- Hot path: "mis tasks creados" (dashboard del teacher).
CREATE INDEX idx_lms_tasks_tenant_owner_created
    ON edushift.lms_tasks (tenant_id, owner_user_id, created_at DESC)
    WHERE NOT deleted;

-- Hot path: tasks con attachment (cleanup jobs futuros, DEBT-7A-18).
CREATE INDEX idx_lms_tasks_tenant_attachment
    ON edushift.lms_tasks (tenant_id, attachment_public_uuid)
    WHERE NOT deleted AND attachment_public_uuid IS NOT NULL;

CREATE TRIGGER set_updated_at_lms_tasks
    BEFORE UPDATE ON edushift.lms_tasks
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON TABLE  edushift.lms_tasks                            IS 'LMS assignment (header). Sprint 7a / BE-7a.2. Submissions live in lms_submissions (V34).';
COMMENT ON COLUMN edushift.lms_tasks.section_id                 IS 'FK -> sections(id). One task belongs to exactly one section.';
COMMENT ON COLUMN edushift.lms_tasks.title                       IS 'Required. CHECK chk_lms_tasks_title_not_blank DB-enforced; @NotBlank in DTO.';
COMMENT ON COLUMN edushift.lms_tasks.description                 IS 'Free-form text up to 10000 chars (DTO cap). Stored as TEXT in DB.';
COMMENT ON COLUMN edushift.lms_tasks.due_at                      IS 'Optional deadline. Server-side check in SubmissionService against this column. NULL = no deadline.';
COMMENT ON COLUMN edushift.lms_tasks.attachment_public_uuid      IS 'Soft FK -> lms_file_objects(public_uuid). Optional binary attached to the assignment itself (the prompt/handout).';
COMMENT ON COLUMN edushift.lms_tasks.owner_user_id               IS 'public_uuid of the user that created the task (TEACHER or TENANT_ADMIN). FK -> users.public_uuid.';
COMMENT ON COLUMN edushift.lms_tasks.allow_resubmission          IS 'When false, POST /submissions on an existing current submission returns 409 RESUBMISSION_NOT_ALLOWED. Default true.';
