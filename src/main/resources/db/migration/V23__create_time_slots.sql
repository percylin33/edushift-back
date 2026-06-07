-- =====================================================================
-- V23 — Sprint 5A / BE-5A.3 — Schedule.time_slots
--
-- Weekly recurring time slot for a TeacherAssignment.
-- One slot = "Profesor X dicta MAT en sección 1A los lunes de 08:00 a 09:00
-- en el aula 12".
--
-- Slots do NOT carry concrete dates: they are weekly patterns. The actual
-- LearningSession (BE-5A.4) materialises a slot on a given scheduled_date.
--
-- Overlap detection is enforced at the SERVICE layer (not via partial
-- unique index) because Postgres' tstzrange/timerange overlap operator
-- would require excluding the row being updated, which is awkward in
-- partial indexes. A simple JPQL query covers all cases.
-- =====================================================================

CREATE TABLE edushift.time_slots (
    id                      uuid          PRIMARY KEY,
    tenant_id               uuid          NOT NULL,
    public_uuid             uuid          NOT NULL,
    created_at              timestamptz   NOT NULL,
    updated_at              timestamptz   NOT NULL,
    created_by              uuid,
    updated_by              uuid,
    deleted                 boolean       NOT NULL DEFAULT false,

    teacher_assignment_id   uuid          NOT NULL,
    day_of_week             smallint      NOT NULL,
    start_time              time          NOT NULL,
    end_time                time          NOT NULL,
    classroom               varchar(80),

    CONSTRAINT uk_time_slots_public_uuid
        UNIQUE (public_uuid),

    -- ISO-8601 day-of-week (1 = MONDAY ... 7 = SUNDAY).
    CONSTRAINT chk_time_slots_day_of_week
        CHECK (day_of_week BETWEEN 1 AND 7),

    -- end_time must be strictly later than start_time. Slots cannot
    -- cross midnight (a Friday-night-into-Saturday slot is two slots).
    CONSTRAINT chk_time_slots_time_range
        CHECK (end_time > start_time),

    CONSTRAINT fk_time_slots_assignment
        FOREIGN KEY (teacher_assignment_id)
        REFERENCES edushift.teacher_assignments (id)
        ON DELETE RESTRICT
);

-- Hot path: list slots for an assignment, ordered by (day, start_time).
-- Composite key starts with tenant_id so Hibernate's discriminator
-- filter aligns with the leading column (DEBT-ACAD-7 standard).
CREATE INDEX idx_time_slots_tenant_assignment_day
    ON edushift.time_slots (tenant_id, teacher_assignment_id, day_of_week, start_time)
    WHERE NOT deleted;

-- Reverse view "teacher's schedule for a period": joins through
-- teacher_assignments. Indexing on (tenant_id, teacher_assignment_id)
-- already covers it, but keeping a dedicated lookup on day_of_week
-- helps the section view that aggregates "who teaches on Monday".
CREATE INDEX idx_time_slots_tenant_day
    ON edushift.time_slots (tenant_id, day_of_week, start_time)
    WHERE NOT deleted;

-- Standard updated_at trigger (consistent with V21/V22).
CREATE TRIGGER set_updated_at_time_slots
    BEFORE UPDATE ON edushift.time_slots
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON TABLE  edushift.time_slots                          IS 'Weekly recurring time slot per TeacherAssignment (Sprint 5A / BE-5A.3).';
COMMENT ON COLUMN edushift.time_slots.day_of_week              IS 'ISO-8601 day of week: 1=MON, 2=TUE, 3=WED, 4=THU, 5=FRI, 6=SAT, 7=SUN.';
COMMENT ON COLUMN edushift.time_slots.classroom                IS 'Optional free-text room/aula label (no FK to a classrooms table — DEBT-SCH-1).';
COMMENT ON COLUMN edushift.time_slots.teacher_assignment_id    IS 'FK ON DELETE RESTRICT — assignment soft-end is the only legal way to retire its slots.';
