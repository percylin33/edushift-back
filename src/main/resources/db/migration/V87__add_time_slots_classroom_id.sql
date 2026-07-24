-- =============================================================================
-- V87__add_time_slots_classroom_id.sql
--
-- Cierre-C / B4 -- wire `classrooms` into `time_slots` (nullable FK).
--
-- El campo libre `time_slots.classroom` (introducido en BE-5A.3)
-- sigue siendo la fuente de verdad para los slots pre-B4 (string que
-- las secciones usan como etiqueta informal, por ejemplo "Lab 2" sin
-- que exista un aula fisica configurada).
--
-- Los slots NUEVOS pasan a apuntar a `classroom_id` (nullable para no
-- romper la importacion en bulk). En el siguiente sprint (cierre-D) el
-- campo `classroom` legacy se eliminara y `classroom_id` sera NOT NULL.
--
-- Decisiones:
--   * ADR-Cierre-C.4 -- FK sin ON DELETE CASCADE (los slots son audit-
--     trail; el classroom no debe borrar slots al borrarse).
--   * ADR-Cierre-C.4 -- indice hot-path para la deteccion de conflictos
--     de aula en `idx_time_slots_conflict_check`.
-- =============================================================================

ALTER TABLE edushift.time_slots
    ADD COLUMN IF NOT EXISTS classroom_id uuid;

ALTER TABLE edushift.time_slots
    ADD CONSTRAINT fk_time_slots_classroom
        FOREIGN KEY (classroom_id)
        REFERENCES edushift.classrooms (id)
        ON DELETE RESTRICT
        NOT VALID;

-- Hot path: conflict detection (B4 / Cierre-C).
--   SELECT * FROM edushift.time_slots
--   WHERE tenant_id = :tenantId
--     AND classroom_id = :classroomId
--     AND day_of_week = :day
--     AND start_time < :newEnd AND end_time > :newStart;
CREATE INDEX idx_time_slots_conflict_check
    ON edushift.time_slots (tenant_id, classroom_id, day_of_week, start_time, end_time)
    WHERE classroom_id IS NOT NULL AND deleted = false;

COMMENT ON COLUMN edushift.time_slots.classroom_id
    IS 'Sprint cierre-C / B4 -- nullable FK to edushift.classrooms. Coexists with the legacy string `classroom` column during the B4 transition.';