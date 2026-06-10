-- =====================================================================
-- V30 - Sprint 6 / BE-6.1 - Attendance module (3 tablas)
--
-- Modulo de asistencia escolar via QR estatico (credencial impresa).
-- El docente, autenticado en su PWA, abre una sesion de asistencia para
-- una seccion + fecha + slot, escanea el QR del alumno y queda registrado
-- como PRESENT (o LATE segun ventana configurable por tenant).
--
-- Tablas:
--   1. attendance_sessions   - Sesion abierta de asistencia.
--   2. attendance_records    - Registro individual (session, student).
--   3. student_attendance_qr - Lifecycle del QR del alumno (issued/revoked).
--
-- Decisiones (ver docs/modules/attendance.md y sprint-06):
--   * ADR-6.1 - Payload del QR es un JWT firmado HS256 con typ=attendance.
--               Persistimos token_hash (SHA-256 hex) para anti-forging.
--   * ADR-6.3 - Idempotencia por (session, student): partial unique index.
--   * ADR-6.6 - ABSENT virtuales en sesiones ACTIVE; al cerrar la sesion,
--               el servicio materializa ABSENT para los alumnos sin scan.
--   * Audit FKs (created_by/scanned_by/edited_by) referencian
--               users(public_uuid) y NO users(id), igual que el hotfix V29
--               de grade_records.recorded_by_user_id (mismo bug evitado).
--
-- Multi-tenant: tenant_id + Hibernate @TenantId discriminator.
-- Soft-delete: flag `deleted` + `deleted_at` con CHECK de coherencia.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. attendance_sessions
-- ---------------------------------------------------------------------
CREATE TABLE edushift.attendance_sessions (
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
    occurred_on                 date          NOT NULL,
    slot                        varchar(16)   NOT NULL,
    starts_at                   timestamptz   NOT NULL,
    closed_at                   timestamptz,
    status                      varchar(16)   NOT NULL DEFAULT 'ACTIVE',
    closed_by_user_id           uuid,
    notes                       varchar(500),

    CONSTRAINT uk_attendance_sessions_public_uuid
        UNIQUE (public_uuid),

    CONSTRAINT chk_attendance_sessions_slot
        CHECK (slot IN ('MORNING','AFTERNOON','FULL_DAY')),

    CONSTRAINT chk_attendance_sessions_status
        CHECK (status IN ('ACTIVE','CLOSED')),

    -- closed_at is consistent with status: CLOSED requires closed_at,
    -- ACTIVE forbids it.
    CONSTRAINT chk_attendance_sessions_closed_consistent
        CHECK (
            (status = 'ACTIVE' AND closed_at IS NULL AND closed_by_user_id IS NULL)
         OR (status = 'CLOSED' AND closed_at IS NOT NULL)
        ),

    CONSTRAINT chk_attendance_sessions_deleted_at_consistent
        CHECK (
            (deleted = false AND deleted_at IS NULL)
         OR (deleted = true  AND deleted_at IS NOT NULL)
        ),

    CONSTRAINT fk_attendance_sessions_section
        FOREIGN KEY (section_id)
        REFERENCES edushift.sections (id)
        ON DELETE RESTRICT,

    -- created_by / updated_by / closed_by reference users.public_uuid
    -- (consistent with V29 hotfix on grade_records.recorded_by_user_id).
    CONSTRAINT fk_attendance_sessions_closed_by
        FOREIGN KEY (closed_by_user_id)
        REFERENCES edushift.users (public_uuid)
        ON DELETE RESTRICT
);

-- Idempotency for (section, day, slot) on ACTIVE sessions: a section
-- cannot have two ACTIVE sessions for the same date+slot. Once CLOSED,
-- the partial filter releases the slot and a new session can be opened.
CREATE UNIQUE INDEX uk_attendance_sessions_section_day_slot_active
    ON edushift.attendance_sessions (section_id, occurred_on, slot)
    WHERE NOT deleted AND status = 'ACTIVE';

-- Hot path: list sessions per section ordered by date desc (calendar
-- views, reports, dashboard "ultimas sesiones cerradas").
CREATE INDEX idx_attendance_sessions_tenant_section_date
    ON edushift.attendance_sessions (tenant_id, section_id, occurred_on DESC)
    WHERE NOT deleted;

-- Dashboard "today overview" + filters by status across the tenant.
CREATE INDEX idx_attendance_sessions_tenant_status_date
    ON edushift.attendance_sessions (tenant_id, status, occurred_on DESC)
    WHERE NOT deleted;

CREATE TRIGGER set_updated_at_attendance_sessions
    BEFORE UPDATE ON edushift.attendance_sessions
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON TABLE  edushift.attendance_sessions                  IS 'Attendance session opened for a (section, day, slot). Sprint 6 / BE-6.1.';
COMMENT ON COLUMN edushift.attendance_sessions.slot             IS 'Time-of-day slot: MORNING | AFTERNOON | FULL_DAY. Tenants without dual shift use FULL_DAY.';
COMMENT ON COLUMN edushift.attendance_sessions.starts_at        IS 'Reference timestamp used to compute LATE: scan after starts_at + tenant.lateAfterMinutes is LATE.';
COMMENT ON COLUMN edushift.attendance_sessions.closed_at        IS 'Set when status transitions to CLOSED. After close, the service materializes ABSENT records for non-scanned enrolled students (ADR-6.6).';
COMMENT ON COLUMN edushift.attendance_sessions.closed_by_user_id IS 'public_uuid of the user that closed the session. FK -> users.public_uuid (consistent with V29 fix).';


-- ---------------------------------------------------------------------
-- 2. attendance_records
-- ---------------------------------------------------------------------
CREATE TABLE edushift.attendance_records (
    id                          uuid          PRIMARY KEY,
    tenant_id                   uuid          NOT NULL,
    public_uuid                 uuid          NOT NULL,
    created_at                  timestamptz   NOT NULL,
    updated_at                  timestamptz   NOT NULL,
    created_by                  uuid,
    updated_by                  uuid,
    deleted                     boolean       NOT NULL DEFAULT false,
    deleted_at                  timestamptz,

    session_id                  uuid          NOT NULL,
    student_id                  uuid          NOT NULL,

    status                      varchar(16)   NOT NULL,
    occurred_at                 timestamptz   NOT NULL,

    -- scanned_by is NULL for ABSENT records materialized at session close.
    scanned_by_user_id          uuid,

    -- edited_by + edited_at populated only when the record was patched
    -- via PUT /attendance/records/{id} (manual correction).
    edited_by_user_id           uuid,
    edited_at                   timestamptz,

    notes                       varchar(500),

    CONSTRAINT uk_attendance_records_public_uuid
        UNIQUE (public_uuid),

    CONSTRAINT chk_attendance_records_status
        CHECK (status IN ('PRESENT','LATE','ABSENT','EXCUSED')),

    -- PRESENT/LATE require scanned_by; ABSENT/EXCUSED tolerate NULL
    -- (ABSENT is materialized server-side; EXCUSED can be set via PUT
    -- without a scan).
    CONSTRAINT chk_attendance_records_scanned_by_present
        CHECK (
            (status IN ('PRESENT','LATE') AND scanned_by_user_id IS NOT NULL)
         OR (status IN ('ABSENT','EXCUSED'))
        ),

    -- edited_by / edited_at travel together.
    CONSTRAINT chk_attendance_records_edited_consistent
        CHECK (
            (edited_by_user_id IS NULL AND edited_at IS NULL)
         OR (edited_by_user_id IS NOT NULL AND edited_at IS NOT NULL)
        ),

    CONSTRAINT chk_attendance_records_deleted_at_consistent
        CHECK (
            (deleted = false AND deleted_at IS NULL)
         OR (deleted = true  AND deleted_at IS NOT NULL)
        ),

    -- Session goes away -> records go with it (ADR: closing a session
    -- materializes records; deleting an entire session in audit-revisits
    -- is rare and should drop its records too).
    CONSTRAINT fk_attendance_records_session
        FOREIGN KEY (session_id)
        REFERENCES edushift.attendance_sessions (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_attendance_records_student
        FOREIGN KEY (student_id)
        REFERENCES edushift.students (id)
        ON DELETE RESTRICT,

    -- Audit FKs reference users.public_uuid (consistent with V29).
    CONSTRAINT fk_attendance_records_scanned_by
        FOREIGN KEY (scanned_by_user_id)
        REFERENCES edushift.users (public_uuid)
        ON DELETE RESTRICT,

    CONSTRAINT fk_attendance_records_edited_by
        FOREIGN KEY (edited_by_user_id)
        REFERENCES edushift.users (public_uuid)
        ON DELETE RESTRICT
);

-- Idempotency: a single non-deleted record per (session, student).
-- Re-scanning the same student returns the existing row (ADR-6.3).
CREATE UNIQUE INDEX uk_attendance_records_session_student
    ON edushift.attendance_records (session_id, student_id)
    WHERE NOT deleted;

-- Hot path: list records of a session (REQ-ATT-04). Order by student
-- name happens via JOIN students at query time.
CREATE INDEX idx_attendance_records_session
    ON edushift.attendance_records (session_id)
    WHERE NOT deleted;

-- Hot path: per-student timeline (Sprint 9 reports + student-detail
-- attendance tab in FE-6.2).
CREATE INDEX idx_attendance_records_tenant_student_date
    ON edushift.attendance_records (tenant_id, student_id, occurred_at DESC)
    WHERE NOT deleted;

-- Dashboard "top absent sections last 7d" + reverse audit lookup.
CREATE INDEX idx_attendance_records_tenant_status_date
    ON edushift.attendance_records (tenant_id, status, occurred_at DESC)
    WHERE NOT deleted;

CREATE TRIGGER set_updated_at_attendance_records
    BEFORE UPDATE ON edushift.attendance_records
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON TABLE  edushift.attendance_records                       IS 'Per-(session, student) attendance record. Sprint 6 / BE-6.1.';
COMMENT ON COLUMN edushift.attendance_records.status                IS 'PRESENT (scan dentro de ventana) | LATE (scan tras lateAfterMinutes) | ABSENT (materializado al cerrar la sesion) | EXCUSED (correccion manual).';
COMMENT ON COLUMN edushift.attendance_records.occurred_at           IS 'Timestamp del evento: para PRESENT/LATE es el momento del scan; para ABSENT es session.closed_at; para EXCUSED es la fecha del PUT.';
COMMENT ON COLUMN edushift.attendance_records.scanned_by_user_id    IS 'public_uuid del docente que escaneo. NULL en ABSENT materializados. FK -> users.public_uuid.';
COMMENT ON COLUMN edushift.attendance_records.edited_by_user_id     IS 'public_uuid del autor del ultimo PUT manual (status/notes). FK -> users.public_uuid.';


-- ---------------------------------------------------------------------
-- 3. student_attendance_qr
-- ---------------------------------------------------------------------
-- No public_uuid: el QR no se expone como recurso REST con su propio UUID;
-- el cliente recibe el JWT renderizado como imagen, y solo el admin lo
-- rota via /students/{uuid}/attendance-qr/rotate. Mismo patron que
-- evaluation_rubric (V28).
CREATE TABLE edushift.student_attendance_qr (
    id                          uuid          PRIMARY KEY,
    tenant_id                   uuid          NOT NULL,
    created_at                  timestamptz   NOT NULL,
    updated_at                  timestamptz   NOT NULL,
    created_by                  uuid,
    updated_by                  uuid,
    deleted                     boolean       NOT NULL DEFAULT false,
    deleted_at                  timestamptz,

    student_id                  uuid          NOT NULL,

    -- SHA-256 hex digest del JWT crudo. Almacenamos el hash y NO el JWT
    -- para que una filtracion de DB no permita forjar QRs (mismo patron
    -- que refresh_tokens en auth.md).
    token_hash                  varchar(64)   NOT NULL,

    issued_at                   timestamptz   NOT NULL,
    revoked_at                  timestamptz,
    revoked_reason              varchar(32),

    CONSTRAINT chk_qr_token_hash_format
        CHECK (token_hash ~ '^[0-9a-f]{64}$'),

    CONSTRAINT chk_qr_revoked_reason
        CHECK (
            revoked_reason IS NULL
         OR revoked_reason IN ('ROTATED','LOST','ADMIN_REVOKE')
        ),

    -- revoked_at + revoked_reason travel together.
    CONSTRAINT chk_qr_revoked_consistent
        CHECK (
            (revoked_at IS NULL AND revoked_reason IS NULL)
         OR (revoked_at IS NOT NULL AND revoked_reason IS NOT NULL)
        ),

    CONSTRAINT chk_qr_deleted_at_consistent
        CHECK (
            (deleted = false AND deleted_at IS NULL)
         OR (deleted = true  AND deleted_at IS NOT NULL)
        ),

    CONSTRAINT fk_qr_student
        FOREIGN KEY (student_id)
        REFERENCES edushift.students (id)
        ON DELETE CASCADE
);

-- A student has at most ONE active QR (non-deleted, non-revoked).
-- Rotation soft-revokes the previous row and inserts a new one in the
-- same transaction (the partial filter releases the slot atomically).
CREATE UNIQUE INDEX uk_qr_student_active
    ON edushift.student_attendance_qr (student_id)
    WHERE NOT deleted AND revoked_at IS NULL;

-- Hot path of check-in: lookup by token_hash (mismo patron que
-- refresh_tokens en auth.md).
CREATE UNIQUE INDEX uk_qr_token_hash
    ON edushift.student_attendance_qr (token_hash)
    WHERE NOT deleted;

-- Tenant scoping: defense in depth even though @TenantId already filters.
CREATE INDEX idx_qr_tenant_student
    ON edushift.student_attendance_qr (tenant_id, student_id)
    WHERE NOT deleted;

CREATE TRIGGER set_updated_at_student_attendance_qr
    BEFORE UPDATE ON edushift.student_attendance_qr
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON TABLE  edushift.student_attendance_qr                IS 'QR JWT lifecycle (issued/revoked) per student. Sprint 6 / BE-6.1.';
COMMENT ON COLUMN edushift.student_attendance_qr.token_hash     IS 'SHA-256 hex digest of the raw JWT. We never persist the JWT itself (see auth.md refresh_tokens pattern).';
COMMENT ON COLUMN edushift.student_attendance_qr.revoked_reason IS 'ROTATED (admin POST /rotate) | LOST (alumno reporto perdida, ROTATED operacionalmente lo cubre) | ADMIN_REVOKE (incidente de seguridad).';
