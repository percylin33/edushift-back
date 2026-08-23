-- =====================================================================
-- V95 - BUG-2026-07-31-04 fix - LMS tasks lifecycle (DRAFT/PUBLISHED/ARCHIVED)
--
-- Bug: TaskController no exponia endpoints para publicar ni archivar
-- tareas, por lo que toda tarea quedaba en estado "siempre visible"
-- (D-TSK-04). El FE tiene UI para publicar y archivar pero los
-- endpoints no existian, devolviendo 404.
--
-- Solucion: agregar columna `status` con ciclo DRAFT -> PUBLISHED ->
-- ARCHIVED, junto con published_at y archived_at. CHECK constraints
-- replican el patron de V35 (lms_quizzes).
--
-- Migracion no destructiva: las filas existentes arrancan en
-- PUBLISHED para preservar la semantica anterior ("siempre
-- visible"), con published_at = created_at.
-- =====================================================================

ALTER TABLE edushift.lms_tasks
    ADD COLUMN IF NOT EXISTS status          varchar(16),
    ADD COLUMN IF NOT EXISTS published_at    timestamptz,
    ADD COLUMN IF NOT EXISTS archived_at     timestamptz;

-- Backfill: las tareas existentes ya estaban visibles (D-TSK-04),
-- asi que arrancan en PUBLISHED. published_at = created_at para
-- satisfacer el CHECK de consistencia.
UPDATE edushift.lms_tasks
SET status = 'PUBLISHED',
    published_at = created_at
WHERE status IS NULL;

ALTER TABLE edushift.lms_tasks
    ALTER COLUMN status SET NOT NULL;

-- CHECK de valores permitidos.
ALTER TABLE edushift.lms_tasks
    ADD CONSTRAINT chk_lms_tasks_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'));

-- published_at no-NULL iff status >= PUBLISHED.
ALTER TABLE edushift.lms_tasks
    ADD CONSTRAINT chk_lms_tasks_published_at_consistent
        CHECK (
            (status = 'DRAFT'     AND published_at IS NULL)
         OR (status IN ('PUBLISHED', 'ARCHIVED') AND published_at IS NOT NULL)
        );

-- archived_at no-NULL iff status = ARCHIVED.
ALTER TABLE edushift.lms_tasks
    ADD CONSTRAINT chk_lms_tasks_archived_at_consistent
        CHECK (
            (status <> 'ARCHIVED' AND archived_at IS NULL)
         OR (status =  'ARCHIVED' AND archived_at IS NOT NULL)
        );

-- Hot path: filtrar tasks visibles por estado (calendario del student).
CREATE INDEX IF NOT EXISTS idx_lms_tasks_tenant_section_status_due
    ON edushift.lms_tasks (tenant_id, section_id, status, due_at DESC NULLS LAST)
    WHERE NOT deleted;

COMMENT ON COLUMN edushift.lms_tasks.status       IS 'Lifecycle. DRAFT (editable), PUBLISHED (visible to students), ARCHIVED (closed, read-only history).';
COMMENT ON COLUMN edushift.lms_tasks.published_at IS 'Timestamp of the DRAFT -> PUBLISHED transition. Non-NULL iff status >= PUBLISHED.';
COMMENT ON COLUMN edushift.lms_tasks.archived_at  IS 'Timestamp of the PUBLISHED -> ARCHIVED transition. Non-NULL iff status = ARCHIVED.';
