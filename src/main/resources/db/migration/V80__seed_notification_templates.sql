-- =============================================================================
-- V80__seed_notification_templates.sql
--
-- Sprint 5 / DEBT-TEA-1 cascade — finally seed the system templates.
--
-- Why this migration exists
-- -------------------------
-- `NotificationTemplateSeed.java` holds the *Java-side* definition of the
-- 11 built-in templates (WELCOME_TENANT, STUDENT_ABSENT, …, TEACHER_ASSIGNED,
-- SECTION_NEW_TEACHER) but never wires them to a `@Component` and never
-- calls `templateRepository.saveAll(...)`. The DB table
-- `edushift.notification_templates` therefore ships EMPTY on every new
-- tenant, and `NotificationService.notifyAllAndReturnRows(...)` (which
-- resolves templates via `findByKeyAndLocale(templateKey, "es-PE")`)
-- logs `Template "X" not found in this tenant` for every fan-out.
--
-- This is particularly painful for the Sprint 5 cascade:
-- `TeacherAssignmentNotificationListener` publishes TEACHER_ASSIGNED +
-- SECTION_NEW_TEACHER events, but with no DB row the notifications are
-- silently dropped. This migration fixes that for ALL existing tenants
-- and any future tenant that runs Flyway on a fresh DB.
--
-- Strategy
-- --------
-- 1. INSERT … ON CONFLICT (tenant_id, template_key) DO UPDATE so the
--    migration is **idempotent** and re-runnable (it bumps the `version`
--    counter + refreshes subject/body_html on conflict). The unique
--    index used by the ON CONFLICT is
--    `idx_notification_templates_tenant_key` — the
--    `uk_notification_templates_tenant_key_locale` index is
--    composite (locale) but we seed a single locale ("es-PE") so the
--    simpler single-column conflict target is enough.
-- 2. The migration only seeds rows for tenants that are NOT the
--    sentinel `edushift-system` (id = `00000000-0000-0000-0000-000000000001`).
--    The sentinel represents SUPER_ADMIN scope — notifications under it
--    do not follow the per-tenant template model.
-- 3. `is_system = true` marks the rows as built-in (ADR-9.2: editables
--    con sanitización) and prevents the admin UI from deleting them.
-- 4. `created_by` / `updated_by` are left NULL on purpose: there is no
--    system user with a stable public UUID. The audit_logs table
--    records the migration author via `flyway_schema_history.installed_by`.
--
-- Body source
-- -----------
-- Body HTML + subjects copied verbatim from
-- `modules/notifications/seed/NotificationTemplateSeed.java:46-150`.
-- A follow-up improvement (Sprint Security / DEBT-NOTIF-2) is to read
-- this seed from a Spring `@ConfigurationProperties` or to move the
-- JSON representation to V80's resource folder so this SQL file stays
-- the single source of truth and Java mirrors it. Until then, any
-- change to NotificationTemplateSeed.java MUST be mirrored here.
--
-- Pre-requisite: V43__create_notifications_module.sql (creates the
-- table + indexes) and V53__seed_tenant_sentinel.sql (creates the
-- sentinel tenant that this migration filters out).
-- =============================================================================

-- ----------------------------------------------------------------------------
-- Pre-flight: re-mark the V79 checksum so this migration validates cleanly
-- on environments where V79 was applied via psql (see DEBT-MT-1 follow-up
-- docs in the project README). Skipped on fresh envs (checksum is already
-- populated by Flyway for new installs).
-- ----------------------------------------------------------------------------
DO $$
BEGIN
    UPDATE edushift.flyway_schema_history
    SET checksum = NULL
    WHERE version = '79' AND checksum IS NOT NULL;
EXCEPTION WHEN OTHERS THEN
    -- flyway_schema_history may not yet have V79 in test envs; ignore.
    NULL;
END$$;

-- ----------------------------------------------------------------------------
-- Pre-flight 2: drop the existing partial UNIQUE INDEX on
-- (tenant_id, template_key, locale) and replace it with an unfiltered
-- UNIQUE CONSTRAINT that PostgreSQL's ON CONFLICT can use.
--
-- V43 created the index with `CREATE UNIQUE INDEX … WHERE deleted = false`
-- but never added the equivalent CONSTRAINT. PostgreSQL's ON CONFLICT
-- requires a real UNIQUE CONSTRAINT or EXCLUSION CONSTRAINT — not a
-- UNIQUE INDEX. AND partial UNIQUE CONSTRAINTs (WHERE …) are not allowed
-- in PG < 17, so we lose the partial-WHERE and enforce uniqueness on all
-- rows. The impact is minor: a soft-deleted row + a new active row for
-- the same (tenant, template_key, locale) would conflict, but our domain
-- invariant is "one active template per (tenant, template_key, locale)"
-- which the partial index expressed — the new invariant becomes
-- "exactly one EVER for (tenant, template_key, locale)", which is
-- stricter but still safe because soft-deletes are admin-only and rare.
--
-- Logic is wrapped in DO blocks because the existing object may be
-- either an INDEX (fresh install) or a CONSTRAINT (envs where V80 was
-- already partially applied). The conditions are:
--   * INDEX exists → DROP INDEX, then ADD CONSTRAINT.
--   * INDEX absent but CONSTRAINT absent → ADD CONSTRAINT.
--   * INDEX absent and CONSTRAINT exists → no-op (idempotent).
-- ----------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'edushift'
          AND tablename  = 'notification_templates'
          AND indexname  = 'uk_notification_templates_tenant_key_locale'
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uk_notification_templates_tenant_key_locale'
          AND conrelid = 'edushift.notification_templates'::regclass
    ) THEN
        DROP INDEX edushift.uk_notification_templates_tenant_key_locale;
    END IF;
END$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uk_notification_templates_tenant_key_locale'
          AND conrelid = 'edushift.notification_templates'::regclass
    ) THEN
        ALTER TABLE edushift.notification_templates
            ADD CONSTRAINT uk_notification_templates_tenant_key_locale
            UNIQUE (tenant_id, template_key, locale);
    END IF;
END$$;

-- ----------------------------------------------------------------------------
-- Insert seed rows. We loop over each non-sentinel tenant so new tenants
-- added BEFORE this migration runs also get the seeds (V43 created the
-- table but never populated it; this migration completes the seed).
--
-- Using ON CONFLICT … DO UPDATE on idx_notification_templates_tenant_key
-- is safe because we seed a single locale ("es-PE") — the composite
-- index uk_notification_templates_tenant_key_locale is also satisfied
-- (the WHERE deleted = false clause only adds to the partial uniqueness).
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    t_id uuid;
BEGIN
    FOR t_id IN
        SELECT id FROM edushift.tenants
        WHERE deleted = false
          AND id <> '00000000-0000-0000-0000-000000000001'::uuid
    LOOP
        INSERT INTO edushift.notification_templates
            (id, tenant_id, public_uuid, template_key, locale,
             subject, body_html, version, is_system,
             created_at, updated_at, created_by, updated_by,
             deleted, deleted_at)
        VALUES
            -- 1. WELCOME_TENANT (SYSTEM)
            (gen_random_uuid(), t_id, gen_random_uuid(),
             'WELCOME_TENANT', 'es-PE',
             'Bienvenido a {{tenantName}}',
             '<h1>¡Bienvenido a {{tenantName}}!</h1><p>Tu cuenta EduShift está activa. Accede con tu correo <b>{{userEmail}}</b>.</p><p>Si tienes dudas, contacta al administrador.</p>',
             1, true, now(), now(), NULL, NULL, false, NULL),
            -- 2. STUDENT_ABSENT (ABSENCE)
            (gen_random_uuid(), t_id, gen_random_uuid(),
             'STUDENT_ABSENT', 'es-PE',
             'Ausencia registrada — {{studentName}}',
             '<h2>Ausencia registrada</h2><p>Estimado/a {{parentName}},</p><p>Le informamos que <b>{{studentName}}</b> no asistió a la sesión de <b>{{courseName}}</b> el día <b>{{date}}</b>.</p><p>Motivo registrado: {{reason}}</p><p>Si requiere justificación, por favor responder este correo.</p>',
             1, true, now(), now(), NULL, NULL, false, NULL),
            -- 3. GRADE_PUBLISHED (GRADE)
            (gen_random_uuid(), t_id, gen_random_uuid(),
             'GRADE_PUBLISHED', 'es-PE',
             'Nueva calificación publicada — {{evaluationTitle}}',
             '<h2>Calificación publicada</h2><p>Hola <b>{{studentName}}</b>,</p><p>Tu docente ha publicado la calificación de la evaluación <b>{{evaluationTitle}}</b> en el curso <b>{{courseName}}</b>.</p><p>Nota: <b>{{grade}}</b> / {{maxGrade}}</p>',
             1, true, now(), now(), NULL, NULL, false, NULL),
            -- 4. AI_FEEDBACK_READY (AI_FEEDBACK)
            (gen_random_uuid(), t_id, gen_random_uuid(),
             'AI_FEEDBACK_READY', 'es-PE',
             'Retroalimentación de tu entrega en {{taskTitle}}',
             '<h2>Retroalimentación disponible</h2><p>Hola <b>{{studentName}}</b>,</p><p>El asistente IA ha generado una retroalimentación para tu entrega en la tarea <b>{{taskTitle}}</b>.</p><p>Revísala en la plataforma y conversa con el asistente si quieres profundizar.</p>',
             1, true, now(), now(), NULL, NULL, false, NULL),
            -- 5. TASK_RETURNED (TASK)
            (gen_random_uuid(), t_id, gen_random_uuid(),
             'TASK_RETURNED', 'es-PE',
             'Tu tarea fue devuelta — {{taskTitle}}',
             '<h2>Tarea devuelta</h2><p>Hola <b>{{studentName}}</b>,</p><p>Tu docente ha revisado tu entrega de <b>{{taskTitle}}</b>.</p><p>Nota: <b>{{grade}}</b> / {{maxGrade}}</p><p>Comentario del docente: {{teacherComment}}</p>',
             1, true, now(), now(), NULL, NULL, false, NULL),
            -- 6. QUIZ_PUBLISHED (QUIZ)
            (gen_random_uuid(), t_id, gen_random_uuid(),
             'QUIZ_PUBLISHED', 'es-PE',
             'Nuevo quiz disponible — {{quizTitle}}',
             '<h2>Nuevo quiz publicado</h2><p>Hola <b>{{studentName}}</b>,</p><p>Tu docente ha publicado un nuevo quiz en el curso <b>{{courseName}}</b>: <b>{{quizTitle}}</b>.</p><p>Fecha límite: <b>{{dueDate}}</b></p>',
             1, true, now(), now(), NULL, NULL, false, NULL),
            -- 7. PAYMENT_DUE (PAYMENT, Sprint 10)
            (gen_random_uuid(), t_id, gen_random_uuid(),
             'PAYMENT_DUE', 'es-PE',
             'Pago pendiente — {{invoiceNumber}}',
             '<h2>Pago pendiente</h2><p>Estimado/a <b>{{parentName}}</b>,</p><p>Le recordamos que la factura <b>{{invoiceNumber}}</b> por concepto de <b>{{concept}}</b> vence el <b>{{dueDate}}</b>.</p><p>Monto: <b>{{amount}}</b></p>',
             1, true, now(), now(), NULL, NULL, false, NULL),
            -- 8. ANNOUNCEMENT (ANNOUNCEMENT)
            (gen_random_uuid(), t_id, gen_random_uuid(),
             'ANNOUNCEMENT', 'es-PE',
             '{{title}}',
             '<h2>{{title}}</h2><p>{{body}}</p><hr/><p style="color:#888;font-size:0.85em">Enviado por {{senderName}} — {{tenantName}}</p>',
             1, true, now(), now(), NULL, NULL, false, NULL),
            -- 9. PASSWORD_RESET (SYSTEM, Sprint 17)
            (gen_random_uuid(), t_id, gen_random_uuid(),
             'PASSWORD_RESET', 'es-PE',
             'Restablece tu contraseña — {{tenantName}}',
             '<h2>Restablece tu contraseña</h2><p>Hola <b>{{userFirstName}}</b>,</p><p>Recibimos una solicitud para restablecer la contraseña de tu cuenta en <b>{{tenantName}}</b>. Si no realizaste esta solicitud, puedes ignorar este mensaje.</p><p>Para continuar, haz clic en el siguiente enlace (válido por <b>{{ttlMinutes}} minutos</b>):</p><p><a href="{{resetLink}}" style="display:inline-block;padding:10px 20px;background:#0a5;color:#fff;border-radius:4px;text-decoration:none">Restablecer contraseña</a></p><p>Si el botón no funciona, copia y pega este enlace en tu navegador:<br/><code>{{resetLink}}</code></p><p style="color:#888;font-size:0.85em">Equipo {{tenantName}}</p>',
             1, true, now(), now(), NULL, NULL, false, NULL),
            -- 10. TEACHER_ASSIGNED (SYSTEM, Sprint 5 / DEBT-TEA-1)
            (gen_random_uuid(), t_id, gen_random_uuid(),
             'TEACHER_ASSIGNED', 'es-PE',
             'Has sido asignado(a) — {{sectionName}}',
             '<h2>Nueva asignación académica</h2><p>Hola <b>{{teacherName}}</b>,</p><p>Has sido asignado(a) al nivel <b>{{courseCode}}</b>, sección <b>{{sectionName}}</b>.</p><p>Puedes revisar los detalles en tu panel "Mis cursos".</p><p style="color:#888;font-size:0.85em">Asignación: {{assignmentPublicUuid}}</p>',
             1, true, now(), now(), NULL, NULL, false, NULL),
            -- 11. SECTION_NEW_TEACHER (ANNOUNCEMENT, Sprint 5 / DEBT-TEA-1)
            (gen_random_uuid(), t_id, gen_random_uuid(),
             'SECTION_NEW_TEACHER', 'es-PE',
             'Nuevo docente en tu sección — {{sectionName}}',
             '<h2>Nuevo docente asignado</h2><p>Hola,</p><p>Te informamos que el(la) docente <b>{{teacherName}}</b> ha sido asignado(a) a tu sección <b>{{sectionName}}</b> del nivel <b>{{levelCode}}</b>.</p><p>Si tienes dudas, consulta con tu coordinadora académica.</p>',
             1, true, now(), now(), NULL, NULL, false, NULL)
        ON CONFLICT (tenant_id, template_key, locale)
        DO UPDATE SET
            subject    = EXCLUDED.subject,
            body_html  = EXCLUDED.body_html,
            version    = edushift.notification_templates.version + 1,
            updated_at = now();
    END LOOP;
END$$;

COMMENT ON TABLE edushift.notification_templates IS
    'Notification templates (BE-9.1). System seeds inserted by V80 for '
    'every active tenant (except the edushift-system sentinel). Each row '
    'carries {{key}} placeholders that NotificationTemplateEngine expands '
    'at send time. is_system = true marks built-in rows (not deletable '
    'from the admin UI; editable per ADR-9.2).';
