-- =====================================================================
-- V24 - Sprint 5A / BE-5A.4 - Sessions.learning_sessions
--
-- Concrete daily occurrence of a TeacherAssignment + Unit on a given
-- scheduled_date. Materialises the weekly TimeSlot pattern (BE-5A.3)
-- into a real class with content, lifecycle, and the competencies +
-- capacities being worked on.
--
-- Lifecycle:
--   PLANNED -> IN_PROGRESS -> COMPLETED
--   PLANNED -> CANCELLED
--   IN_PROGRESS -> CANCELLED
-- (COMPLETED and CANCELLED are terminal.)
--
-- Concurrency: a long-running OPTLOCK column ('version') prevents two
-- admins from racing each other on lifecycle transitions (the FE keeps
-- the version it loaded with and includes it in the start/complete/
-- cancel payload).
-- =====================================================================

CREATE TABLE edushift.learning_sessions (
    id                      uuid          PRIMARY KEY,
    tenant_id               uuid          NOT NULL,
    public_uuid             uuid          NOT NULL,
    created_at              timestamptz   NOT NULL,
    updated_at              timestamptz   NOT NULL,
    created_by              uuid,
    updated_by              uuid,
    deleted                 boolean       NOT NULL DEFAULT false,
    deleted_at              timestamptz,
    version                 bigint        NOT NULL DEFAULT 0,

    teacher_assignment_id   uuid          NOT NULL,
    unit_id                 uuid          NOT NULL,

    title                   varchar(200)  NOT NULL,
    objective               text,
    scheduled_date          date          NOT NULL,
    duration_minutes        integer       NOT NULL,
    content                 jsonb,

    -- Lifecycle.
    status                  varchar(16)   NOT NULL DEFAULT 'PLANNED',
    started_at              timestamptz,
    ended_at                timestamptz,
    cancelled_at            timestamptz,

    CONSTRAINT uk_learning_sessions_public_uuid
        UNIQUE (public_uuid),

    CONSTRAINT chk_learning_sessions_status
        CHECK (status IN ('PLANNED','IN_PROGRESS','COMPLETED','CANCELLED')),

    -- Duration sanity. 480 min = 8h, generous upper bound for an MVP
    -- "double session" or a project-day. Anything longer should be
    -- modelled as multiple sessions.
    CONSTRAINT chk_learning_sessions_duration
        CHECK (duration_minutes BETWEEN 1 AND 480),

    -- ended_at must be after started_at when both are present.
    CONSTRAINT chk_learning_sessions_lifecycle_window
        CHECK (started_at IS NULL OR ended_at IS NULL OR ended_at >= started_at),

    -- Lifecycle timestamp coherence with status.
    CONSTRAINT chk_learning_sessions_status_timestamps
        CHECK (
            (status = 'PLANNED'
             AND started_at IS NULL AND ended_at IS NULL AND cancelled_at IS NULL)
         OR (status = 'IN_PROGRESS'
             AND started_at IS NOT NULL AND ended_at IS NULL AND cancelled_at IS NULL)
         OR (status = 'COMPLETED'
             AND started_at IS NOT NULL AND ended_at IS NOT NULL AND cancelled_at IS NULL)
         OR (status = 'CANCELLED'
             AND cancelled_at IS NOT NULL)
        ),

    CONSTRAINT fk_learning_sessions_assignment
        FOREIGN KEY (teacher_assignment_id)
        REFERENCES edushift.teacher_assignments (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_learning_sessions_unit
        FOREIGN KEY (unit_id)
        REFERENCES edushift.academic_units (id)
        ON DELETE RESTRICT
);

-- Hot path: list sessions of an assignment ordered by scheduled_date.
-- Used by the assignment-scoped reverse view and by the lifecycle UI.
CREATE INDEX idx_learning_sessions_tenant_assignment_date
    ON edushift.learning_sessions (tenant_id, teacher_assignment_id, scheduled_date)
    WHERE NOT deleted;

-- Hot path: list sessions per unit ordered by date. Drives the
-- unit-scoped reverse view and the "is this unit in use?" check from
-- BE-5A.1 (UnitService.delete -> UNIT_HAS_SESSIONS).
CREATE INDEX idx_learning_sessions_tenant_unit_date
    ON edushift.learning_sessions (tenant_id, unit_id, scheduled_date)
    WHERE NOT deleted;

-- Hot path: filter by date range + status across the whole tenant
-- (e.g. "today's sessions in IN_PROGRESS"). Leading column is tenant_id
-- so Hibernate's discriminator filter aligns with the index.
CREATE INDEX idx_learning_sessions_tenant_status_date
    ON edushift.learning_sessions (tenant_id, status, scheduled_date)
    WHERE NOT deleted;

CREATE TRIGGER set_updated_at_learning_sessions
    BEFORE UPDATE ON edushift.learning_sessions
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON TABLE  edushift.learning_sessions                  IS 'Daily occurrence of a TeacherAssignment + Unit on a scheduled_date (Sprint 5A / BE-5A.4).';
COMMENT ON COLUMN edushift.learning_sessions.content          IS 'JSONB blob: { objective, activities[], materials[], observations }. Free-form to allow editor evolution in Sprint 7.';
COMMENT ON COLUMN edushift.learning_sessions.version          IS 'Optimistic-lock version managed by JPA @Version. Lifecycle transitions check this to prevent races.';
COMMENT ON COLUMN edushift.learning_sessions.status           IS 'PLANNED -> IN_PROGRESS -> COMPLETED or PLANNED/IN_PROGRESS -> CANCELLED.';

-- =====================================================================
-- Many-to-many: session <-> competency.
-- A session can target several competencies, all from the same course
-- as the assignment. Validation lives in the service layer
-- (COMPETENCY_NOT_IN_COURSE, 400).
-- =====================================================================
CREATE TABLE edushift.learning_session_competencies (
    learning_session_id     uuid NOT NULL,
    competency_id           uuid NOT NULL,

    CONSTRAINT pk_learning_session_competencies
        PRIMARY KEY (learning_session_id, competency_id),

    CONSTRAINT fk_lsc_session
        FOREIGN KEY (learning_session_id)
        REFERENCES edushift.learning_sessions (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_lsc_competency
        FOREIGN KEY (competency_id)
        REFERENCES edushift.competencies (id)
        ON DELETE RESTRICT
);

-- Reverse lookup: "what sessions reference this competency" — drives
-- COMPETENCY_IN_USE_BY_SESSIONS (BE-5A.2 placeholder now becomes real).
CREATE INDEX idx_lsc_competency
    ON edushift.learning_session_competencies (competency_id);

COMMENT ON TABLE edushift.learning_session_competencies IS 'M:N pivot between learning_sessions and competencies (Sprint 5A / BE-5A.4).';

-- =====================================================================
-- Many-to-many: session <-> capacity.
-- Same shape as competencies. Capacities must belong to a competency
-- of the same course as the assignment (CAPACITY_NOT_IN_COURSE, 400).
-- =====================================================================
CREATE TABLE edushift.learning_session_capacities (
    learning_session_id     uuid NOT NULL,
    capacity_id             uuid NOT NULL,

    CONSTRAINT pk_learning_session_capacities
        PRIMARY KEY (learning_session_id, capacity_id),

    CONSTRAINT fk_lscap_session
        FOREIGN KEY (learning_session_id)
        REFERENCES edushift.learning_sessions (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_lscap_capacity
        FOREIGN KEY (capacity_id)
        REFERENCES edushift.capacities (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_lscap_capacity
    ON edushift.learning_session_capacities (capacity_id);

COMMENT ON TABLE edushift.learning_session_capacities IS 'M:N pivot between learning_sessions and capacities (Sprint 5A / BE-5A.4).';
