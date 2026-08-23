-- =============================================================================
-- V93__seed_extended_tecnosur.sql
--
-- Seed extendido para tecnosur (single-tenant, full scope).
--
-- Contexto
-- --------
-- V38 / V39 / V74 / V80 siembran lo MINIMO para hacer login + smoke (users,
-- tenants, teachers, students, sections, courses, quizzes, attendance,
-- ai_generations, notification_templates). Pero para tener una demo
-- realista hay entidades que ya existen en migraciones estructurales
-- (V25..V86) y nunca se siembran:
--
--   - lms_tasks, lms_submissions, lms_submission_revisions
--   - lms_materials, lms_file_objects
--   - lms_quiz_attempts, lms_quiz_answers
--   - ai_chat_sessions, ai_chat_messages
--   - evaluations, rubrics, evaluation_rubric, grade_records
--   - academic_units, competencies, capacities, learning_sessions,
--     learning_session_competencies, learning_session_capacities
--   - student_attendance_qr
--   - notifications, announcements, announcement_recipients
--   - classrooms, time_slots
--   - user_device_tokens
--   - 2 cuentas STUDENT logueables extra (lucia ya esta en V74)
--   - students.user_id linkeados para los 3 logins STUDENT
--
-- Esta migracion es ADITIVA, idempotente y safe-for-replay. Cada bloque
-- corre dentro de un DO $$ que verifica la existencia previa del tenant
-- tecnosur (sin el, retorna). Si los datos ya existen, el bloque termina
-- sin error.
--
-- CONVENCION de FK a users (importante - los headers de cada bloque
-- referencian la seccion Convenciones de FKs del plan):
--   * users.id             - PK interno UUIDv7; NUNCA se usa en FKs
--                            outbound (solo en columnas created_by /
--                            updated_by que no tienen FK).
--   * users.public_uuid    - UUIDv4 externo; este es el que se usa en
--                            TODAS las FKs a users (V48/V75/V76/V77/V90).
--
-- Volumen esperado: ~1500-2000 filas nuevas.
-- Tiempo esperado: 30-90s en BD fresca.
-- =============================================================================


-- ----------------------------------------------------------------------------
-- 0. GUARD: skip on production databases
-- ----------------------------------------------------------------------------
DO $$
BEGIN
    IF current_database() IN ('edushift_prod', 'edushift_production') THEN
        RAISE NOTICE 'V93 seed skipped on production database %', current_database();
        RETURN;
    END IF;
END;
$$;


-- ----------------------------------------------------------------------------
-- 5.1  2 STUDENT user accounts extra + link students.user_id a los 3
--      (lucia + mateo + valentina).
-- ----------------------------------------------------------------------------
-- lucia fue creada por V74. V93 anade mateo y valentina, y vincula
-- students.user_id = users.public_uuid (NO users.id, ver V76) para
-- los 3 alumnos en secciones distintas de 1A.
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id      uuid;
    v_existing       uuid;
    v_lucia_pu       uuid;
    v_mateo_pu       uuid;
    v_valentina_pu   uuid;
    v_student_id_1   uuid;
    v_student_id_2   uuid;
    v_student_id_3   uuid;
BEGIN
    SELECT id INTO v_tenant_id FROM edushift.tenants WHERE slug = 'tecnosur' AND deleted = false;
    IF v_tenant_id IS NULL THEN
        RAISE NOTICE 'V93.1: tenant tecnosur not found - V93 skipped';
        RETURN;
    END IF;

    -- mateo.estudiante
    SELECT id INTO v_existing FROM edushift.users
    WHERE email = 'mateo.estudiante@tecnosur.edushift.pe'
      AND deleted = false AND tenant_id = v_tenant_id;
    IF v_existing IS NULL THEN
        INSERT INTO edushift.users (
            id, tenant_id, public_uuid, email, password_hash,
            first_name, last_name, status, email_verified, mfa_enabled,
            roles, created_at, updated_at, deleted, created_by, updated_by
        ) VALUES (
            gen_random_uuid(), v_tenant_id, gen_random_uuid(),
            'mateo.estudiante@tecnosur.edushift.pe',
            'SEED_RESET_REQUIRED_v1_EduShift2026!',
            'Mateo', 'Ramirez Cardenas', 'ACTIVE', true, false,
            ARRAY['STUDENT']::varchar[], NOW(), NOW(), false, NULL, NULL
        );
        RAISE NOTICE 'V93.1: inserted STUDENT mateo.estudiante';
    END IF;

    -- valentina.estudiante
    SELECT id INTO v_existing FROM edushift.users
    WHERE email = 'valentina.estudiante@tecnosur.edushift.pe'
      AND deleted = false AND tenant_id = v_tenant_id;
    IF v_existing IS NULL THEN
        INSERT INTO edushift.users (
            id, tenant_id, public_uuid, email, password_hash,
            first_name, last_name, status, email_verified, mfa_enabled,
            roles, created_at, updated_at, deleted, created_by, updated_by
        ) VALUES (
            gen_random_uuid(), v_tenant_id, gen_random_uuid(),
            'valentina.estudiante@tecnosur.edushift.pe',
            'SEED_RESET_REQUIRED_v1_EduShift2026!',
            'Valentina', 'Quiroz Montes', 'ACTIVE', true, false,
            ARRAY['STUDENT']::varchar[], NOW(), NOW(), false, NULL, NULL
        );
        RAISE NOTICE 'V93.1: inserted STUDENT valentina.estudiante';
    END IF;

    -- Capturar public_uuid de los 3 STUDENT (lucia ya existe de V74).
    SELECT public_uuid INTO v_lucia_pu     FROM edushift.users WHERE email = 'lucia.student@tecnosur.edushift.pe'     AND deleted = false AND tenant_id = v_tenant_id;
    SELECT public_uuid INTO v_mateo_pu     FROM edushift.users WHERE email = 'mateo.estudiante@tecnosur.edushift.pe' AND deleted = false AND tenant_id = v_tenant_id;
    SELECT public_uuid INTO v_valentina_pu FROM edushift.users WHERE email = 'valentina.estudiante@tecnosur.edushift.pe' AND deleted = false AND tenant_id = v_tenant_id;

    IF v_lucia_pu IS NULL OR v_mateo_pu IS NULL OR v_valentina_pu IS NULL THEN
        RAISE EXCEPTION 'V93.1: failed to resolve public_uuid for one of the 3 STUDENT users';
    END IF;

    -- Vincular students.user_id (FK -> users.public_uuid, V76).
    -- Lookup por nombre + apellido deterministico (no OFFSET fragil).
    -- Martina Alvarez, Mateo Mendoza y Valentina Torres son los 3 estudiantes
    -- de la seccion A de 1er grado donde estan los quizzes (V39).
    SELECT id INTO v_student_id_1 FROM edushift.students
    WHERE tenant_id = v_tenant_id AND deleted = false
      AND first_name = 'Martina' AND last_name = 'Alvarez Soto'
    LIMIT 1;

    SELECT id INTO v_student_id_2 FROM edushift.students
    WHERE tenant_id = v_tenant_id AND deleted = false
      AND first_name = 'Mateo' AND last_name = 'Mendoza Rivera'
    LIMIT 1;

    SELECT id INTO v_student_id_3 FROM edushift.students
    WHERE tenant_id = v_tenant_id AND deleted = false
      AND first_name = 'Valentina' AND last_name = 'Torres Salas'
    LIMIT 1;

    IF v_student_id_1 IS NOT NULL THEN
        UPDATE edushift.students SET user_id = v_lucia_pu
        WHERE id = v_student_id_1 AND deleted = false AND user_id IS NULL;
    END IF;
    IF v_student_id_2 IS NOT NULL THEN
        UPDATE edushift.students SET user_id = v_mateo_pu
        WHERE id = v_student_id_2 AND deleted = false AND user_id IS NULL;
    END IF;
    IF v_student_id_3 IS NOT NULL THEN
        UPDATE edushift.students SET user_id = v_valentina_pu
        WHERE id = v_student_id_3 AND deleted = false AND user_id IS NULL;
    END IF;

    RAISE NOTICE 'V93.1: linked students.user_id to lucia/mateo/valentina public_uuids';
END;
$$;


-- ----------------------------------------------------------------------------
-- 5.2  classrooms (V86) + time_slots (V23+V87).
-- ----------------------------------------------------------------------------
-- 4 aulas tipo CLASSROOM/LAB. 6 bloques horarios para los primeros
-- `teacher_assignments` de 1A. classroom_id se asigna segun el curso
-- (PROG/OFI -> LAB-COMP, ELEC -> LAB-ELEC, otros -> A101).
-- teacher_assignments NO tiene columna classroom_id / time_slot_id — la
-- relacion es via `time_slots.teacher_assignment_id`.
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id     uuid;
    v_a101_id       uuid;
    v_a102_id       uuid;
    v_lab_comp_id   uuid;
    v_lab_elec_id   uuid;
BEGIN
    SELECT id INTO v_tenant_id FROM edushift.tenants WHERE slug = 'tecnosur' AND deleted = false;
    IF v_tenant_id IS NULL THEN RETURN; END IF;

    IF NOT EXISTS (SELECT 1 FROM edushift.classrooms WHERE tenant_id = v_tenant_id AND code = 'A101' AND deleted = false) THEN
        INSERT INTO edushift.classrooms (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, code, name, type, capacity, location, description)
        VALUES (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL,
                'A101', 'Aula 101', 'CLASSROOM', 30, 'Piso 1, ala norte', 'Aula tradicional 1ro-2do secundaria');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM edushift.classrooms WHERE tenant_id = v_tenant_id AND code = 'A102' AND deleted = false) THEN
        INSERT INTO edushift.classrooms (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, code, name, type, capacity, location, description)
        VALUES (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL,
                'A102', 'Aula 102', 'CLASSROOM', 30, 'Piso 1, ala sur', 'Aula tradicional 3ro-5to secundaria');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM edushift.classrooms WHERE tenant_id = v_tenant_id AND code = 'LAB-COMP' AND deleted = false) THEN
        INSERT INTO edushift.classrooms (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, code, name, type, capacity, location, description)
        VALUES (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL,
                'LAB-COMP', 'Lab. Computacion 1', 'LAB', 25, 'Piso 2, ala este', 'Laboratorio con 25 PCs para PROG y OFI');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM edushift.classrooms WHERE tenant_id = v_tenant_id AND code = 'LAB-ELEC' AND deleted = false) THEN
        INSERT INTO edushift.classrooms (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, code, name, type, capacity, location, description)
        VALUES (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL,
                'LAB-ELEC', 'Lab. Electronica', 'LAB', 25, 'Piso 2, ala oeste', 'Laboratorio con osciloscopios y fuentes para ELEC');
    END IF;

    SELECT id INTO v_a101_id     FROM edushift.classrooms WHERE tenant_id = v_tenant_id AND code = 'A101'     AND deleted = false;
    SELECT id INTO v_a102_id     FROM edushift.classrooms WHERE tenant_id = v_tenant_id AND code = 'A102'     AND deleted = false;
    SELECT id INTO v_lab_comp_id FROM edushift.classrooms WHERE tenant_id = v_tenant_id AND code = 'LAB-COMP' AND deleted = false;
    SELECT id INTO v_lab_elec_id FROM edushift.classrooms WHERE tenant_id = v_tenant_id AND code = 'LAB-ELEC' AND deleted = false;

    -- 6 time_slots: dias 1..6 (L-S) para los primeros 6 TA de 1A.
    INSERT INTO edushift.time_slots (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, classroom_id, teacher_assignment_id, day_of_week, start_time, end_time, classroom)
    SELECT
        gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false,
        CASE c.code WHEN 'PROG' THEN v_lab_comp_id WHEN 'OFI' THEN v_lab_comp_id WHEN 'ELEC' THEN v_lab_elec_id ELSE v_a101_id END,
        ta.id,
        ((row_number() OVER (ORDER BY ta.created_at)) - 1) % 6 + 1 AS day_of_week,
        '07:30:00'::time,
        '08:30:00'::time,
        'A101'
    FROM edushift.teacher_assignments ta
    JOIN edushift.courses c ON c.id = ta.course_id AND c.deleted = false
    JOIN edushift.sections s ON s.id = ta.section_id AND s.deleted = false
    JOIN edushift.grades g ON g.id = s.grade_id AND g.deleted = false AND g.ordinal = 1
    WHERE ta.tenant_id = v_tenant_id AND ta.deleted = false
    ORDER BY ta.created_at
    LIMIT 6
    ON CONFLICT DO NOTHING;

    RAISE NOTICE 'V93.2: classrooms + time_slots seeded for tecnosur';
END;
$$;


-- ----------------------------------------------------------------------------
-- 5.3  lms_file_objects (V31+V50) + lms_materials (V32).
-- ----------------------------------------------------------------------------
-- 10 archivos dummy (status='READY' default V50) + 10 materials linkeados
-- a la primera seccion de 1A. owner_user_id = hugo.salazar (public_uuid).
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id    uuid;
    v_section_id   uuid;
    v_owner_pu     uuid;
    v_file_id      uuid;
    v_file_count   int := 0;
BEGIN
    SELECT id INTO v_tenant_id FROM edushift.tenants WHERE slug = 'tecnosur' AND deleted = false;
    IF v_tenant_id IS NULL THEN RETURN; END IF;

    SELECT s.id INTO v_section_id
    FROM edushift.sections s
    JOIN edushift.grades g ON g.id = s.grade_id AND g.deleted = false AND g.ordinal = 1
    WHERE s.tenant_id = v_tenant_id AND s.deleted = false
    ORDER BY s.name LIMIT 1;

    SELECT public_uuid INTO v_owner_pu FROM edushift.users
    WHERE email = 'hugo.salazar@tecnosur.edushift.pe' AND deleted = false AND tenant_id = v_tenant_id;

    IF v_section_id IS NULL OR v_owner_pu IS NULL THEN
        RAISE NOTICE 'V93.3: missing 1A section or hugo user — skipped';
        RETURN;
    END IF;

    IF EXISTS (SELECT 1 FROM edushift.lms_materials WHERE tenant_id = v_tenant_id AND section_id = v_section_id AND deleted = false) THEN
        RAISE NOTICE 'V93.3: materials already seeded for 1A — skipped';
        RETURN;
    END IF;

    FOR i IN 1..10 LOOP
        v_file_id := gen_random_uuid();
        INSERT INTO edushift.lms_file_objects (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, provider, remote_key, original_name, content_type, size_bytes, checksum_sha256, bucket, reference_count, status)
        VALUES (
            v_file_id, v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL,
            'LOCAL_FS', 'seed/materials/tecnosur/file-' || i || '.pdf', 'Apunte ' || i || '.pdf',
            'application/pdf', 102400 + (i * 1024),
            encode(digest(random()::text, 'sha256'), 'hex'),
            'edushift-dev', 1, 'READY'
        );

        INSERT INTO edushift.lms_materials (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, section_id, file_public_uuid, title, description, kind, external_url, owner_user_id)
        VALUES (
            gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL,
            v_section_id, v_file_id,
            'Apunte ' || i || ' — Programacion basica',
            'Material de apoyo para el curso de Programacion (PROG)',
            'FILE', NULL, v_owner_pu
        );
        v_file_count := v_file_count + 1;
    END LOOP;

    RAISE NOTICE 'V93.3: % lms_file_objects + lms_materials seeded for 1A', v_file_count;
END;
$$;


-- ----------------------------------------------------------------------------
-- 5.4  lms_tasks (V33) — 1 task por curso tecnico en cada seccion.
-- ----------------------------------------------------------------------------
-- 4 cursos tecnicos (PROG, ELEC, DIBT, OFI) en secciones donde existe
-- teacher_assignment. owner_user_id = teacher user del assignment si
-- existe, o hugo.salazar como fallback.
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id    uuid;
    v_count        int := 0;
BEGIN
    SELECT id INTO v_tenant_id FROM edushift.tenants WHERE slug = 'tecnosur' AND deleted = false;
    IF v_tenant_id IS NULL THEN RETURN; END IF;

    IF EXISTS (SELECT 1 FROM edushift.lms_tasks WHERE tenant_id = v_tenant_id AND deleted = false LIMIT 1) THEN
        RAISE NOTICE 'V93.4: lms_tasks already seeded — skipped';
        RETURN;
    END IF;

    INSERT INTO edushift.lms_tasks (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, section_id, title, description, due_at, attachment_public_uuid, owner_user_id, allow_resubmission)
    SELECT
        gen_random_uuid(),
        v_tenant_id,
        gen_random_uuid(),
        NOW(),
        NOW(),
        NULL, NULL, false, NULL,
        ta.section_id,
        t.title,
        t.description,
        NOW() + (t.due_days || ' days')::interval,
        NULL,
        t.owner_user_id,
        true
    FROM edushift.teacher_assignments ta
    JOIN edushift.courses c ON c.id = ta.course_id AND c.deleted = false
    JOIN edushift.teachers t2 ON t2.id = ta.teacher_id AND t2.deleted = false
    CROSS JOIN LATERAL (
        SELECT
            CASE c.code
                WHEN 'PROG' THEN 'Ejercicio de bucles for en Python'
                WHEN 'ELEC' THEN 'Esquema de circuito RC serie'
                WHEN 'DIBT' THEN 'Plano en planta de una habitacion 4x4m'
                WHEN 'OFI'  THEN 'Tabla dinamica en Excel con VLOOKUP'
                ELSE 'Tarea del curso ' || c.code
            END AS title,
            CASE c.code
                WHEN 'PROG' THEN 'Implementar 5 ejercicios usando bucles for, range() y enumerate().'
                WHEN 'ELEC' THEN 'Calcular V, I y R del circuito propuesto. Adjuntar diagrama.'
                WHEN 'DIBT' THEN 'Escala 1:50. Acotar correctamente. Norma ISO 128.'
                WHEN 'OFI'  THEN 'Archivo .xlsx con la tabla y grafico del mes.'
                ELSE 'Tarea del curso ' || c.code
            END AS description,
            CASE (1 + (random() * 7)::int)
                WHEN 1 THEN 7 WHEN 2 THEN 8 WHEN 3 THEN 9 WHEN 4 THEN 10
                WHEN 5 THEN 11 WHEN 6 THEN 12 ELSE 14
            END AS due_days,
            COALESCE(
                t2.user_id,
                (SELECT public_uuid FROM edushift.users WHERE email = 'hugo.salazar@tecnosur.edushift.pe' AND deleted = false AND tenant_id = v_tenant_id LIMIT 1)
            ) AS owner_user_id
    ) t
    WHERE ta.tenant_id = v_tenant_id AND ta.deleted = false
      AND c.code IN ('PROG', 'ELEC', 'DIBT', 'OFI');

    GET DIAGNOSTICS v_count = ROW_COUNT;
    RAISE NOTICE 'V93.4: % lms_tasks seeded for tecnosur', v_count;
END;
$$;


-- ----------------------------------------------------------------------------
-- 5.5  lms_submissions (V34) + 5.6  lms_submission_revisions (V40).
-- ----------------------------------------------------------------------------
-- 7-8 submissions por task: 2-3 GRADED, resto SUBMITTED.
-- 2-3 parent-on-behalf (D-TSK-02).
-- text_body siempre NOT NULL (CHECK chk_lms_submissions_payload_not_empty).
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id   uuid;
    v_parent_pu   uuid;
    v_count       int := 0;
    v_rev_count   int := 0;
BEGIN
    SELECT id INTO v_tenant_id FROM edushift.tenants WHERE slug = 'tecnosur' AND deleted = false;
    IF v_tenant_id IS NULL THEN RETURN; END IF;

    IF EXISTS (SELECT 1 FROM edushift.lms_submissions WHERE tenant_id = v_tenant_id AND deleted = false LIMIT 1) THEN
        RAISE NOTICE 'V93.5/6: lms_submissions already seeded — skipped';
        RETURN;
    END IF;

    SELECT public_uuid INTO v_parent_pu FROM edushift.users
    WHERE email = 'padre.tecnosur@tecnosur.edushift.pe' AND deleted = false AND tenant_id = v_tenant_id;

    INSERT INTO edushift.lms_submissions (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, task_id, student_user_id, submitter_user_id, text_body, attachment_public_uuid, status, grade, feedback, graded_by_user_id, graded_at)
    WITH ranked AS (
        SELECT
            t.id AS task_id,
            t.owner_user_id,
            s.user_id AS student_pu,
            row_number() OVER (PARTITION BY t.id ORDER BY s.id) AS rn
        FROM edushift.lms_tasks t
        JOIN edushift.student_enrollments e ON e.section_id = t.section_id AND e.deleted = false
        JOIN edushift.students s ON s.id = e.student_id AND s.deleted = false AND s.user_id IS NOT NULL
        WHERE t.tenant_id = v_tenant_id AND t.deleted = false
    )
    SELECT
        gen_random_uuid(),
        v_tenant_id,
        gen_random_uuid(),
        NOW(),
        NOW(),
        NULL, NULL, false, NULL,
        r.task_id,
        r.student_pu,
        CASE WHEN r.rn <= 2 AND v_parent_pu IS NOT NULL THEN v_parent_pu ELSE r.student_pu END,
        '[Entrega automatica V93] Resolucion del ejercicio. Fecha: ' || to_char(NOW() - (random() * INTERVAL '3 days'), 'YYYY-MM-DD HH24:MI') || '.',
        NULL,
        CASE WHEN r.rn <= 3 THEN 'GRADED' ELSE 'SUBMITTED' END,
        CASE WHEN r.rn <= 3 THEN 14 + (random() * 6)::int ELSE NULL END,
        CASE WHEN r.rn <= 3 THEN 'Buen trabajo. Revisar la seccion de acotacion.' ELSE NULL END,
        CASE WHEN r.rn <= 3 THEN r.owner_user_id ELSE NULL END,
        CASE WHEN r.rn <= 3 THEN NOW() - INTERVAL '2 days' ELSE NULL END
    FROM ranked r
    WHERE r.rn <= 8;

    GET DIAGNOSTICS v_count = ROW_COUNT;
    RAISE NOTICE 'V93.5: % lms_submissions seeded for tecnosur', v_count;

    INSERT INTO edushift.lms_submission_revisions (id, tenant_id, created_at, updated_at, created_by, updated_by, deleted, deleted_at, submission_id, revision_number, text_body, attachment_public_uuid, created_by_user_id)
    SELECT
        gen_random_uuid(),
        v_tenant_id,
        NOW() - INTERVAL '3 days',
        NOW() - INTERVAL '3 days',
        NULL, NULL, false, NULL,
        sub.id,
        1,
        'Version anterior: entrega incompleta, falta paso 3.',
        NULL,
        sub.student_user_id
    FROM edushift.lms_submissions sub
    WHERE sub.tenant_id = v_tenant_id AND sub.status = 'GRADED' AND sub.deleted = false
    LIMIT 3;

    GET DIAGNOSTICS v_rev_count = ROW_COUNT;
    RAISE NOTICE 'V93.6: % lms_submission_revisions seeded for tecnosur', v_rev_count;
END;
$$;


-- ----------------------------------------------------------------------------
-- 5.7  lms_quiz_attempts (V35) + lms_quiz_answers (V35).
-- ----------------------------------------------------------------------------
-- Para cada uno de los 4 quizzes PUBLISHED de tecnosur (V39), 6 attempts.
-- status='GRADED' (NO 'COMPLETED' — V35 CHECK solo acepta
-- IN_PROGRESS|SUBMITTED|AUTO_GRADED|GRADED|EXPIRED).
-- Para MC: 60% correctas, 40% incorrectas. Para TF: 70% match, 30% no.
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id   uuid;
    v_count       int := 0;
    v_ans_count   int := 0;
BEGIN
    SELECT id INTO v_tenant_id FROM edushift.tenants WHERE slug = 'tecnosur' AND deleted = false;
    IF v_tenant_id IS NULL THEN RETURN; END IF;

    IF EXISTS (SELECT 1 FROM edushift.lms_quiz_attempts WHERE tenant_id = v_tenant_id) THEN
        RAISE NOTICE 'V93.7: lms_quiz_attempts already seeded — skipped';
        RETURN;
    END IF;

    -- Crear attempts (uno por (quiz, student))
    INSERT INTO edushift.lms_quiz_attempts (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, quiz_id, student_user_id, submitter_user_id, attempt_number, started_at, expires_at, submitted_at, status, auto_score, manual_score, score, graded_by_user_id, graded_at, feedback)
    SELECT
        gen_random_uuid(), v_tenant_id, gen_random_uuid(),
        NOW() - INTERVAL '2 days', NOW() - INTERVAL '1 day', NULL, NULL, false, NULL,
        q.id, s.user_id, s.user_id, 1,
        NOW() - INTERVAL '2 days', NULL, NOW() - INTERVAL '1 day',
        'GRADED', 0, 0, 0,
        q.owner_user_id, NOW() - INTERVAL '1 day',
        'Calificado automaticamente. Buen trabajo.'
    FROM edushift.lms_quizzes q
    CROSS JOIN LATERAL (
        SELECT s2.id, s2.user_id
        FROM edushift.student_enrollments e
        JOIN edushift.students s2 ON s2.id = e.student_id AND s2.deleted = false AND s2.user_id IS NOT NULL
        WHERE e.section_id = q.section_id AND e.deleted = false
        ORDER BY random()
        LIMIT 6
    ) s
    WHERE q.tenant_id = v_tenant_id AND q.deleted = false AND q.status = 'PUBLISHED';

    GET DIAGNOSTICS v_count = ROW_COUNT;
    RAISE NOTICE 'V93.7: % lms_quiz_attempts seeded for tecnosur', v_count;

    -- Crear answers (uno por (attempt, question))
    INSERT INTO edushift.lms_quiz_answers (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, attempt_id, question_id, selected_option_id, selected_boolean, text_answer, points_awarded, is_correct, graded_by_user_id, graded_at)
    WITH pick AS (
        SELECT
            a.id AS attempt_id,
            qq.id AS question_id,
            qq.question_type,
            qq.points,
            qq.correct_boolean,
            (random() < 0.6) AS want_correct_mc,
            (random() < 0.7) AS want_match_tf
        FROM edushift.lms_quiz_attempts a
        JOIN edushift.lms_quiz_questions qq ON qq.quiz_id = a.quiz_id AND qq.deleted = false
        WHERE a.tenant_id = v_tenant_id
    )
    SELECT
        gen_random_uuid(),
        v_tenant_id,
        gen_random_uuid(),
        NOW(), NOW(), NULL, NULL, false, NULL,
        p.attempt_id,
        p.question_id,
        CASE WHEN p.question_type = 'MC' THEN
            COALESCE(
                (SELECT o.public_uuid FROM edushift.lms_quiz_options o
                 WHERE o.question_id = p.question_id AND o.deleted = false
                   AND ((p.want_correct_mc AND o.is_correct) OR (NOT p.want_correct_mc AND NOT o.is_correct))
                 ORDER BY random() LIMIT 1),
                (SELECT o.public_uuid FROM edushift.lms_quiz_options o
                 WHERE o.question_id = p.question_id AND o.deleted = false
                 ORDER BY random() LIMIT 1)
            )
        END,
        CASE WHEN p.question_type = 'TF' THEN
            CASE WHEN p.want_match_tf THEN p.correct_boolean ELSE NOT p.correct_boolean END
        END,
        NULL,
        CASE
            WHEN p.question_type = 'MC' THEN
                CASE WHEN p.want_correct_mc THEN p.points ELSE 0 END
            WHEN p.question_type = 'TF' THEN
                CASE WHEN p.want_match_tf THEN p.points ELSE 0 END
            ELSE 0
        END,
        CASE
            WHEN p.question_type = 'MC' THEN p.want_correct_mc
            WHEN p.question_type = 'TF' THEN p.want_match_tf
            ELSE false
        END,
        NULL,
        NOW() - INTERVAL '1 day'
    FROM pick p;

    GET DIAGNOSTICS v_ans_count = ROW_COUNT;
    RAISE NOTICE 'V93.7: % lms_quiz_answers seeded for tecnosur', v_ans_count;

    -- Recalcular score en cada attempt (suma de points_awarded) + auto_score.
    UPDATE edushift.lms_quiz_attempts a
    SET score = COALESCE(agg.total, 0),
        auto_score = COALESCE(agg.total, 0)
    FROM (
        SELECT ia_inner.id, SUM(ans.points_awarded) AS total
        FROM edushift.lms_quiz_attempts ia_inner
        JOIN edushift.lms_quiz_answers ans ON ans.attempt_id = ia_inner.id AND ans.deleted = false
        WHERE ia_inner.tenant_id = v_tenant_id
        GROUP BY ia_inner.id
    ) agg
    WHERE a.id = agg.id;
END;
$$;


-- ----------------------------------------------------------------------------
-- 5.9  academic_units (V21) + competencies (V22) + capacities (V22) +
--      learning_sessions (V24) + learning_session_competencies +
--      learning_session_capacities.
-- ----------------------------------------------------------------------------
-- 4 academic_units (uno por curso tecnico), 8 competencies (2 por unit),
-- 16 capacities (2 por competency), 1-2 learning_sessions por TA activo,
-- m:n con competencies/capacities.
-- learning_sessions.status='COMPLETED' exige started_at + ended_at IS NOT NULL.
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id  uuid;
    v_count      int := 0;
BEGIN
    SELECT id INTO v_tenant_id FROM edushift.tenants WHERE slug = 'tecnosur' AND deleted = false;
    IF v_tenant_id IS NULL THEN RETURN; END IF;

    IF NOT EXISTS (SELECT 1 FROM edushift.academic_units WHERE tenant_id = v_tenant_id AND deleted = false LIMIT 1) THEN
        -- 5.9.1 academic_units (4)
        INSERT INTO edushift.academic_units (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, course_id, name, description, display_order, start_date, end_date, is_active)
        SELECT
            gen_random_uuid(),
            v_tenant_id,
            gen_random_uuid(),
            NOW(), NOW(), NULL, NULL, false, NULL,
            c.id,
            'Unidad I: Fundamentos de ' || c.name,
            'Conceptos basicos y fundamentos teoricos.',
            ROW_NUMBER() OVER (ORDER BY c.code),
            (CURRENT_DATE - INTERVAL '30 days')::date,
            (CURRENT_DATE - INTERVAL '1 day')::date,
            true
        FROM edushift.courses c
        WHERE c.tenant_id = v_tenant_id AND c.deleted = false AND c.code IN ('PROG', 'ELEC', 'DIBT', 'OFI');
        GET DIAGNOSTICS v_count = ROW_COUNT;
        RAISE NOTICE 'V93.9: % academic_units seeded for tecnosur', v_count;

        -- 5.9.2 competencies (2 por unit)
        INSERT INTO edushift.competencies (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, course_id, code, name, description, display_order, is_active)
        SELECT
            gen_random_uuid(),
            v_tenant_id,
            gen_random_uuid(),
            NOW(), NOW(), NULL, NULL, false, NULL,
            au.course_id,
            c.code || '_U' || au.display_order || '_C' || rn,
            CASE rn
                WHEN 1 THEN 'Disena algoritmos basicos'
                WHEN 2 THEN 'Aplica estructuras de control'
                ELSE 'Competencia ' || rn
            END,
            'Competencia del curso',
            rn,
            true
        FROM edushift.academic_units au
        JOIN edushift.courses c ON c.id = au.course_id AND c.deleted = false
        CROSS JOIN generate_series(1, 2) AS rn
        WHERE au.tenant_id = v_tenant_id AND au.deleted = false;

        -- 5.9.3 capacities (2 por competency)
        INSERT INTO edushift.capacities (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, competency_id, code, name, description, display_order, is_active)
        SELECT
            gen_random_uuid(),
            v_tenant_id,
            gen_random_uuid(),
            NOW(), NOW(), NULL, NULL, false, NULL,
            co.id,
            co.code || '_CAP' || rn,
            CASE rn
                WHEN 1 THEN 'Aplica estructuras secuenciales'
                WHEN 2 THEN 'Aplica estructuras selectivas'
                ELSE 'Capacidad ' || rn
            END,
            'Capacidad del curso',
            rn,
            true
        FROM edushift.competencies co
        CROSS JOIN generate_series(1, 2) AS rn
        WHERE co.tenant_id = v_tenant_id AND co.deleted = false;
    ELSE
        RAISE NOTICE 'V93.9: academic_units already seeded — skipping units/competencies/capacities';
    END IF;

    -- 5.9.4 learning_sessions: 1-2 por TA activo de 1A. status='COMPLETED'
    -- exige started_at + ended_at NOT NULL.
    IF NOT EXISTS (SELECT 1 FROM edushift.learning_sessions WHERE tenant_id = v_tenant_id AND deleted = false LIMIT 1) THEN
        INSERT INTO edushift.learning_sessions (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, version, teacher_assignment_id, unit_id, title, objective, scheduled_date, duration_minutes, content, status, started_at, ended_at, cancelled_at)
        SELECT
            gen_random_uuid(),
            v_tenant_id,
            gen_random_uuid(),
            NOW() - INTERVAL '15 days',
            NOW() - INTERVAL '15 days',
            NULL, NULL, false, NULL,
            0,
            ta.id,
            au.id,
            'Clase: ' || c.code || ' - sesion ' || rn,
            'Introducir conceptos de la unidad.',
            (CURRENT_DATE - INTERVAL '15 days')::date,
            90,
            '{"objective":"comprender los fundamentos","activities":["explicacion","ejercicios"]}'::jsonb,
            'COMPLETED',
            (CURRENT_DATE - INTERVAL '15 days' + TIME '07:30')::timestamp,
            (CURRENT_DATE - INTERVAL '15 days' + TIME '09:00')::timestamp,
            NULL
        FROM edushift.teacher_assignments ta
        JOIN edushift.courses c ON c.id = ta.course_id AND c.deleted = false
        JOIN edushift.sections s ON s.id = ta.section_id AND s.deleted = false
        JOIN edushift.grades g ON g.id = s.grade_id AND g.deleted = false AND g.ordinal = 1
        JOIN edushift.academic_units au ON au.course_id = c.id AND au.tenant_id = v_tenant_id AND au.deleted = false
        CROSS JOIN generate_series(1, 2) AS rn
        WHERE ta.tenant_id = v_tenant_id AND ta.deleted = false;
        GET DIAGNOSTICS v_count = ROW_COUNT;
        RAISE NOTICE 'V93.9: % learning_sessions seeded for tecnosur', v_count;

        -- 5.9.5 M:N session <-> competency
        INSERT INTO edushift.learning_session_competencies (learning_session_id, competency_id)
        SELECT ls.id, co.id
        FROM edushift.learning_sessions ls
        JOIN edushift.competencies co ON co.tenant_id = ls.tenant_id AND co.course_id = (
            SELECT c_inner.id FROM edushift.courses c_inner
            JOIN edushift.teacher_assignments ta_inner ON ta_inner.course_id = c_inner.id AND ta_inner.id = ls.teacher_assignment_id
        ) AND co.deleted = false
        WHERE ls.tenant_id = v_tenant_id AND ls.deleted = false;

        -- 5.9.6 M:N session <-> capacity
        INSERT INTO edushift.learning_session_capacities (learning_session_id, capacity_id)
        SELECT ls.id, cap.id
        FROM edushift.learning_sessions ls
        JOIN edushift.learning_session_competencies lsc ON lsc.learning_session_id = ls.id
        JOIN edushift.capacities cap ON cap.competency_id = lsc.competency_id AND cap.deleted = false
        WHERE ls.tenant_id = v_tenant_id AND ls.deleted = false;
    ELSE
        RAISE NOTICE 'V93.9: learning_sessions already seeded — skipping';
    END IF;
END;
$$;
-- ----------------------------------------------------------------------------
-- 5.10  student_attendance_qr (V30).
-- ----------------------------------------------------------------------------
-- 1 fila por estudiante activo de tecnosur. token_hash = SHA-256 hex 64.
-- UNIQUE INDEX uk_qr_student_active garantiza 1 activo por student.
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id  uuid;
    v_count      int := 0;
BEGIN
    SELECT id INTO v_tenant_id FROM edushift.tenants WHERE slug = 'tecnosur' AND deleted = false;
    IF v_tenant_id IS NULL THEN RETURN; END IF;
    IF EXISTS (SELECT 1 FROM edushift.student_attendance_qr WHERE tenant_id = v_tenant_id LIMIT 1) THEN
        RAISE NOTICE 'V93.10: student_attendance_qr already seeded — skipped';
        RETURN;
    END IF;
    INSERT INTO edushift.student_attendance_qr (id, tenant_id, created_at, updated_at, created_by, updated_by, deleted, deleted_at, student_id, token_hash, issued_at, revoked_at, revoked_reason)
    SELECT
        gen_random_uuid(),
        v_tenant_id,
        NOW(), NOW(), NULL, NULL, false, NULL,
        s.id,
        encode(digest(random()::text || s.id::text, 'sha256'), 'hex'),
        NOW(),
        NULL, NULL
    FROM edushift.students s
    WHERE s.tenant_id = v_tenant_id AND s.deleted = false;
    GET DIAGNOSTICS v_count = ROW_COUNT;
    RAISE NOTICE 'V93.10: % student_attendance_qr seeded for tecnosur', v_count;
END;
$$;
-- ----------------------------------------------------------------------------
-- 5.11  ai_chat_sessions (V42+V75) + ai_chat_messages (V42).
-- ----------------------------------------------------------------------------
-- 1 sesion por cada teacher user account (5). user_id = public_uuid (V75).
-- 4 mensajes por sesion (user/assistant/user/assistant).
-- model_used = 'MiniMax-M3'.
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id   uuid;
    v_sess_count  int := 0;
    v_msg_count   int := 0;
    v_session_id  uuid;
    v_teacher_pu  uuid;
BEGIN
    SELECT id INTO v_tenant_id FROM edushift.tenants WHERE slug = 'tecnosur' AND deleted = false;
    IF v_tenant_id IS NULL THEN RETURN; END IF;
    IF EXISTS (SELECT 1 FROM edushift.ai_chat_sessions WHERE tenant_id = v_tenant_id LIMIT 1) THEN
        RAISE NOTICE 'V93.11: ai_chat_sessions already seeded — skipped';
        RETURN;
    END IF;
    FOR v_teacher_pu IN
        SELECT public_uuid FROM edushift.users
        WHERE tenant_id = v_tenant_id AND deleted = false AND 'TEACHER' = ANY(roles)
        ORDER BY email
    LOOP
        v_session_id := gen_random_uuid();
        INSERT INTO edushift.ai_chat_sessions (id, tenant_id, public_uuid, user_id, title, status, message_count, total_tokens_in, total_tokens_out, last_message_at, expires_at, created_at, updated_at, deleted, deleted_at)
        VALUES (
            v_session_id, v_tenant_id, gen_random_uuid(), v_teacher_pu,
            'Sesion de prueba IA: Programacion',
            'ACTIVE', 4, 240, 480, NOW(), NOW() + INTERVAL '7 days',
            NOW(), NOW(), false, NULL
        );
        v_sess_count := v_sess_count + 1;
        -- 4 mensajes: 1 user, 1 assistant, 1 user, 1 assistant.
        INSERT INTO edushift.ai_chat_messages (id, tenant_id, public_uuid, chat_session_id, role, content, status, parent_message_id, model_used, prompt_tokens, response_tokens, latency_ms, error_code, error_message, input_hash, output_hash, created_at, updated_at, deleted, deleted_at)
        VALUES
        (gen_random_uuid(), v_tenant_id, gen_random_uuid(), v_session_id, 'user', 'Explícame los bucles for en Python con un ejemplo.', 'COMPLETED', NULL, NULL, 60, NULL, NULL, NULL, NULL, encode(digest('for-loop-q', 'sha256'), 'hex'), NULL, NOW() - INTERVAL '3 hours', NOW() - INTERVAL '3 hours', false, NULL),
        (gen_random_uuid(), v_tenant_id, gen_random_uuid(), v_session_id, 'assistant', 'En Python, `for i in range(10):` itera 10 veces. Ejemplo:\n```\nfor i in range(5):\n    print(i)\n# 0,1,2,3,4\n```', 'COMPLETED', NULL, 'MiniMax-M3', NULL, 180, 1200, NULL, NULL, NULL, encode(digest('for-loop-a', 'sha256'), 'hex'), NOW() - INTERVAL '3 hours' + INTERVAL '5 seconds', NOW() - INTERVAL '3 hours' + INTERVAL '5 seconds', false, NULL),
        (gen_random_uuid(), v_tenant_id, gen_random_uuid(), v_session_id, 'user', 'Y qué diferencia hay con `while`?', 'COMPLETED', NULL, NULL, 30, NULL, NULL, NULL, NULL, encode(digest('for-vs-while-q', 'sha256'), 'hex'), NULL, NOW() - INTERVAL '2 hours', NOW() - INTERVAL '2 hours', false, NULL),
        (gen_random_uuid(), v_tenant_id, gen_random_uuid(), v_session_id, 'assistant', '`while` se usa cuando no sabés cuántas iteraciones; `for` itera sobre una secuencia. Por claridad, preferí `for` siempre que puedas.', 'COMPLETED', NULL, 'MiniMax-M3', NULL, 150, 850, NULL, NULL, NULL, encode(digest('for-vs-while-a', 'sha256'), 'hex'), NOW() - INTERVAL '2 hours' + INTERVAL '3 seconds', NOW() - INTERVAL '2 hours' + INTERVAL '3 seconds', false, NULL);
        v_msg_count := v_msg_count + 4;
    END LOOP;
    RAISE NOTICE 'V93.11: % ai_chat_sessions + % ai_chat_messages seeded for tecnosur', v_sess_count, v_msg_count;
END;
$$;
-- ----------------------------------------------------------------------------
-- 5.12  notifications (V43+V48).
-- ----------------------------------------------------------------------------
-- 5 notificaciones por tenant, repartidas entre admin y teachers.
-- recipient_user_id = public_uuid (V48). category según V80 templates.
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id  uuid;
    v_count      int := 0;
    v_admin_pu   uuid;
    v_hugo_pu    uuid;
    v_teacher_pu uuid;
BEGIN
    SELECT id INTO v_tenant_id FROM edushift.tenants WHERE slug = 'tecnosur' AND deleted = false;
    IF v_tenant_id IS NULL THEN RETURN; END IF;
    IF EXISTS (SELECT 1 FROM edushift.notifications WHERE tenant_id = v_tenant_id AND deleted = false LIMIT 1) THEN
        RAISE NOTICE 'V93.12: notifications already seeded — skipped';
        RETURN;
    END IF;
    SELECT public_uuid INTO v_admin_pu FROM edushift.users WHERE email = 'admin@tecnosur.edushift.pe' AND deleted = false AND tenant_id = v_tenant_id;
    SELECT public_uuid INTO v_hugo_pu  FROM edushift.users WHERE email = 'hugo.salazar@tecnosur.edushift.pe' AND deleted = false AND tenant_id = v_tenant_id;
    SELECT public_uuid INTO v_teacher_pu FROM edushift.users WHERE email = 'mariela.paredes@tecnosur.edushift.pe' AND deleted = false AND tenant_id = v_tenant_id;
    IF v_admin_pu IS NULL OR v_hugo_pu IS NULL THEN
        RAISE NOTICE 'V93.12: missing admin/hugo public_uuid — skipped';
        RETURN;
    END IF;
    INSERT INTO edushift.notifications (id, tenant_id, public_uuid, recipient_user_id, template_key, category, channel, payload, status, sent_at, read_at, error_code, error_message, created_at, updated_at, deleted, deleted_at) VALUES
        (gen_random_uuid(), v_tenant_id, gen_random_uuid(), v_admin_pu, 'WELCOME_TENANT', 'SYSTEM', 'IN_APP', '{"userEmail":"admin@tecnosur.edushift.pe","tenantName":"Colegio Tecnico Tecnosur"}'::jsonb, 'READ', NOW() - INTERVAL '7 days', NOW() - INTERVAL '6 days', NULL, NULL, NOW() - INTERVAL '7 days', NOW() - INTERVAL '6 days', false, NULL),
        (gen_random_uuid(), v_tenant_id, gen_random_uuid(), v_hugo_pu, 'WELCOME_TENANT', 'SYSTEM', 'IN_APP', '{"userEmail":"hugo.salazar@tecnosur.edushift.pe","tenantName":"Colegio Tecnico Tecnosur"}'::jsonb, 'SENT', NOW() - INTERVAL '1 day', NULL, NULL, NULL, NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day', false, NULL),
        (gen_random_uuid(), v_tenant_id, gen_random_uuid(), v_admin_pu, 'STUDENT_ABSENT', 'ABSENCE', 'IN_APP', '{"studentName":"Alumno Demo 1A","parentName":"Veronica Cossio","courseName":"Programacion","date":"2026-07-25","reason":"Sin justificacion"}'::jsonb, 'SENT', NOW() - INTERVAL '2 days', NULL, NULL, NULL, NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days', false, NULL),
        (gen_random_uuid(), v_tenant_id, gen_random_uuid(), v_hugo_pu, 'TASK_RETURNED', 'TASK', 'IN_APP', '{"studentName":"Alumno Demo 1A","taskTitle":"Ejercicio de bucles for en Python","maxGrade":20,"teacherComment":"Buen trabajo. Revisar la seccion de acotacion."}'::jsonb, 'SENT', NOW() - INTERVAL '2 days', NULL, NULL, NULL, NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days', false, NULL),
        (gen_random_uuid(), v_tenant_id, gen_random_uuid(), v_hugo_pu, 'QUIZ_PUBLISHED', 'QUIZ', 'IN_APP', '{"studentName":"Alumno Demo 1A","courseName":"Programacion","quizTitle":"Quiz parcial I - Programacion","dueDate":"2026-08-05"}'::jsonb, 'SENT', NOW() - INTERVAL '1 day', NULL, NULL, NULL, NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day', false, NULL);
    GET DIAGNOSTICS v_count = ROW_COUNT;
    RAISE NOTICE 'V93.12: % notifications seeded for tecnosur', v_count;
END;
$$;
-- ----------------------------------------------------------------------------
-- 5.13  announcements (V44+V48) + announcement_recipients (V44).
-- ----------------------------------------------------------------------------
-- 2 announcements por tenant: 1 SCHOOL pinned, 1 ROLE=PARENT.
-- author_user_id = public_uuid (V48). audience_type ∈ {SCHOOL, ROLE, ...}.
-- NO existe columna priority — solo pinned boolean.
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id   uuid;
    v_admin_pu    uuid;
    v_count       int := 0;
    v_general_id  uuid;
    v_parents_id  uuid;
BEGIN
    SELECT id INTO v_tenant_id FROM edushift.tenants WHERE slug = 'tecnosur' AND deleted = false;
    IF v_tenant_id IS NULL THEN RETURN; END IF;
    IF EXISTS (SELECT 1 FROM edushift.announcements WHERE tenant_id = v_tenant_id AND deleted = false LIMIT 1) THEN
        RAISE NOTICE 'V93.13: announcements already seeded — skipped';
        RETURN;
    END IF;
    SELECT public_uuid INTO v_admin_pu FROM edushift.users WHERE email = 'admin@tecnosur.edushift.pe' AND deleted = false AND tenant_id = v_tenant_id;
    IF v_admin_pu IS NULL THEN RETURN; END IF;
    v_general_id := gen_random_uuid();
    v_parents_id := gen_random_uuid();
    INSERT INTO edushift.announcements (id, tenant_id, public_uuid, author_user_id, title, body_html, audience_type, audience_ids, status, publish_at, published_at, pinned, created_at, updated_at, deleted, deleted_at) VALUES
        (v_general_id, v_tenant_id, gen_random_uuid(), v_admin_pu,
         'Comunicado de inicio de año 2026',
         '<h2>Bienvenidos al año escolar 2026</h2><p>El equipo del Colegio Tecnico Tecnosur les desea un excelente inicio de clases.</p><p>Las actividades comienzan el 1 de marzo.</p>',
         'SCHOOL', '[]'::jsonb, 'PUBLISHED', NULL, NOW() - INTERVAL '7 days', true, NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days', false, NULL),
        (v_parents_id, v_tenant_id, gen_random_uuid(), v_admin_pu,
         'Reunion de padres - Marzo 2026',
         '<h2>Reunion general de padres</h2><p>La reunion se llevara a cabo el 15 de marzo a las 18:00 en el auditorio principal.</p>',
         'ROLE', '["PARENT"]'::jsonb, 'PUBLISHED', NULL, NOW() - INTERVAL '3 days', false, NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days', false, NULL);
    GET DIAGNOSTICS v_count = ROW_COUNT;
    RAISE NOTICE 'V93.13: % announcements seeded for tecnosur', v_count;
    -- announcement_recipients: 1 fila por user con rol aplicable.
    -- Para SCHOOL: todos los users activos con rol (5 teachers + 2 staff + 1 admin + 1 parent + 3 students = 12).
    INSERT INTO edushift.announcement_recipients (id, tenant_id, announcement_id, user_id, delivered_at, read_at, created_at, updated_at, deleted, deleted_at)
    SELECT gen_random_uuid(), v_tenant_id, v_general_id, u.public_uuid, NOW() - INTERVAL '7 days', NULL, NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days', false, NULL
    FROM edushift.users u
    WHERE u.tenant_id = v_tenant_id AND u.deleted = false AND cardinality(u.roles) > 0;
    -- Para ROLE=PARENT: solo el parent.
    INSERT INTO edushift.announcement_recipients (id, tenant_id, announcement_id, user_id, delivered_at, read_at, created_at, updated_at, deleted, deleted_at)
    SELECT gen_random_uuid(), v_tenant_id, v_parents_id, u.public_uuid, NOW() - INTERVAL '3 days', NULL, NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days', false, NULL
    FROM edushift.users u
    WHERE u.tenant_id = v_tenant_id AND u.deleted = false AND 'PARENT' = ANY(u.roles);
END;
$$;
-- ----------------------------------------------------------------------------
-- 5.14  user_device_tokens (V88).
-- ----------------------------------------------------------------------------
-- 1 token FCM dummy por usuario con rol en el tenant.
-- user_public_uuid (NO user_id) → users.public_uuid (V88).
-- token != '' con UNIQUE global.
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id  uuid;
    v_count      int := 0;
    v_platforms  varchar(10)[] := ARRAY['ANDROID','IOS','WEB'];
    v_idx        int := 0;
BEGIN
    SELECT id INTO v_tenant_id FROM edushift.tenants WHERE slug = 'tecnosur' AND deleted = false;
    IF v_tenant_id IS NULL THEN RETURN; END IF;
    IF EXISTS (SELECT 1 FROM edushift.user_device_tokens WHERE tenant_id = v_tenant_id LIMIT 1) THEN
        RAISE NOTICE 'V93.14: user_device_tokens already seeded — skipped';
        RETURN;
    END IF;
    FOR v_idx IN 1..3 LOOP
        INSERT INTO edushift.user_device_tokens (id, tenant_id, created_at, updated_at, created_by, updated_by, deleted, deleted_at, user_public_uuid, token, platform, active, last_seen_at, unregistered_at)
        SELECT
            gen_random_uuid(),
            v_tenant_id,
            NOW(), NOW(), NULL, NULL, false, NULL,
            u.public_uuid,
            'fake-fcm-' || u.id::text || '-' || v_idx,
            v_platforms[((row_number() OVER (ORDER BY u.email))::int % 3) + 1],
            true,
            NOW() - INTERVAL '1 day',
            NULL
        FROM edushift.users u
        WHERE u.tenant_id = v_tenant_id AND u.deleted = false AND cardinality(u.roles) > 0;
    END LOOP;
    GET DIAGNOSTICS v_count = ROW_COUNT;
    RAISE NOTICE 'V93.14: % user_device_tokens seeded for tecnosur', v_count;
END;
$$;
-- =============================================================================
-- FIN V93
-- =============================================================================


-- ----------------------------------------------------------------------------
-- 5.8  evaluations (V25) + rubrics (V26) + evaluation_rubric (V28) +
--      grade_records (V27+V29).
-- ----------------------------------------------------------------------------
-- 1 evaluation por curso tecnico (4), PUBLISHED, EXAM, SCORE_0_20.
-- 1 rubric por evaluation (3 criterios, 4 niveles).
-- 1-2 grade_records por estudiante de 1A en el curso.
-- recorded_by_user_id = teacher user (public_uuid, V29).
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id  uuid;
    v_count      int := 0;
BEGIN
    SELECT id INTO v_tenant_id FROM edushift.tenants WHERE slug = 'tecnosur' AND deleted = false;
    IF v_tenant_id IS NULL THEN RETURN; END IF;

    IF EXISTS (SELECT 1 FROM edushift.evaluations WHERE tenant_id = v_tenant_id AND deleted = false LIMIT 1) THEN
        RAISE NOTICE 'V93.8: evaluations already seeded — skipped';
        RETURN;
    END IF;

    -- 5.8.1 evaluations: 1 por (curso tecnico, 1A).
    INSERT INTO edushift.evaluations (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, teacher_assignment_id, unit_id, learning_session_id, kind, name, description, weight, scheduled_date, due_date, scale, status, published_at, closed_at, is_active)
    SELECT
        gen_random_uuid(),
        v_tenant_id,
        gen_random_uuid(),
        NOW() - INTERVAL '30 days',
        NOW() - INTERVAL '30 days',
        NULL, NULL, false, NULL,
        ta.id,
        au.id,
        NULL,
        'EXAM',
        'Examen Parcial I Bimestre - ' || c.code || ' - ' || s.name,
        'Evaluacion de medio bimestre. Temas: unidades I-II.',
        1.00,
        (CURRENT_DATE - INTERVAL '30 days')::date,
        (CURRENT_DATE - INTERVAL '23 days')::date,
        'SCORE_0_20',
        'PUBLISHED',
        NOW() - INTERVAL '30 days',
        NULL,
        true
    FROM edushift.teacher_assignments ta
    JOIN edushift.courses c ON c.id = ta.course_id AND c.deleted = false AND c.code IN ('PROG', 'ELEC', 'DIBT', 'OFI')
    JOIN edushift.sections s ON s.id = ta.section_id AND s.deleted = false
    JOIN edushift.grades g ON g.id = s.grade_id AND g.deleted = false AND g.ordinal = 1
    JOIN edushift.academic_units au ON au.course_id = c.id AND au.tenant_id = v_tenant_id AND au.deleted = false
    WHERE ta.tenant_id = v_tenant_id AND ta.deleted = false;

    GET DIAGNOSTICS v_count = ROW_COUNT;
    RAISE NOTICE 'V93.8: % evaluations seeded for tecnosur', v_count;

    -- 5.8.2 rubrics: 1 por evaluation (1-1 implicit).
    INSERT INTO edushift.rubrics (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, name, description, criteria, levels, is_system, parent_rubric_id, is_active)
    SELECT
        gen_random_uuid(),
        v_tenant_id,
        gen_random_uuid(),
        NOW() - INTERVAL '30 days',
        NOW() - INTERVAL '30 days',
        NULL, NULL, false, NULL,
        'Rubrica - ' || e.name,
        'RUB: razonamiento, aplicacion, presentacion.',
        '[
          {"key":"razonamiento","name":"Razonamiento","description":"Capacidad de analizar el problema","weight":40.0,"descriptors":[
            {"level":"EN_INICIO","text":"Identifica el problema pero no lo analiza"},
            {"level":"EN_PROCESO","text":"Analiza parcialmente"},
            {"level":"ESPERADO","text":"Analiza correctamente"},
            {"level":"SOBRESALIENTE","text":"Analiza y propone alternativas"}
          ]},
          {"key":"aplicacion","name":"Aplicacion","description":"Aplica conceptos","weight":40.0,"descriptors":[
            {"level":"EN_INICIO","text":"No aplica los conceptos"},
            {"level":"EN_PROCESO","text":"Aplica parcialmente"},
            {"level":"ESPERADO","text":"Aplica correctamente"},
            {"level":"SOBRESALIENTE","text":"Aplica y generaliza"}
          ]},
          {"key":"presentacion","name":"Presentacion","description":"Orden y claridad","weight":20.0,"descriptors":[
            {"level":"EN_INICIO","text":"Desordenado"},
            {"level":"EN_PROCESO","text":"Aceptable"},
            {"level":"ESPERADO","text":"Ordenado"},
            {"level":"SOBRESALIENTE","text":"Sobresaliente"}
          ]}
        ]'::jsonb,
        '[
          {"code":"EN_INICIO","name":"En inicio","order":1},
          {"code":"EN_PROCESO","name":"En proceso","order":2},
          {"code":"ESPERADO","name":"Esperado","order":3},
          {"code":"SOBRESALIENTE","name":"Sobresaliente","order":4}
        ]'::jsonb,
        false,
        NULL,
        true
    FROM edushift.evaluations e
    WHERE e.tenant_id = v_tenant_id AND e.deleted = false
      AND NOT EXISTS (SELECT 1 FROM edushift.rubrics r WHERE r.tenant_id = v_tenant_id AND r.name = 'Rubrica - ' || e.name AND r.deleted = false);

    -- 5.8.3 evaluation_rubric (1-1).
    INSERT INTO edushift.evaluation_rubric (id, tenant_id, created_at, updated_at, created_by, updated_by, deleted, deleted_at, evaluation_id, rubric_id)
    SELECT
        gen_random_uuid(),
        v_tenant_id,
        NOW() - INTERVAL '30 days',
        NOW() - INTERVAL '30 days',
        NULL, NULL, false, NULL,
        e.id,
        r.id
    FROM edushift.evaluations e
    JOIN edushift.rubrics r ON r.tenant_id = v_tenant_id AND r.name = 'Rubrica - ' || e.name AND r.deleted = false
    WHERE e.tenant_id = v_tenant_id AND e.deleted = false
      AND NOT EXISTS (SELECT 1 FROM edushift.evaluation_rubric er WHERE er.evaluation_id = e.id AND er.deleted = false);

    -- 5.8.4 grade_records: 1-2 por estudiante activo en la seccion del TA.
    INSERT INTO edushift.grade_records (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, evaluation_id, student_id, score, literal, comments, recorded_at, recorded_by_user_id, is_active)
    WITH ranked AS (
        SELECT
            e.id AS evaluation_id,
            ta.teacher_id,
            (SELECT public_uuid FROM edushift.users WHERE public_uuid = t2.user_id AND deleted = false AND tenant_id = v_tenant_id LIMIT 1) AS teacher_pu,
            s.id AS student_id,
            s.user_id AS student_pu,
            row_number() OVER (PARTITION BY e.id ORDER BY s.id) AS rn
        FROM edushift.evaluations e
        JOIN edushift.teacher_assignments ta ON ta.id = e.teacher_assignment_id AND ta.deleted = false
        JOIN edushift.teachers t2 ON t2.id = ta.teacher_id AND t2.deleted = false
        JOIN edushift.student_enrollments se ON se.section_id = ta.section_id AND se.deleted = false
        JOIN edushift.students s ON s.id = se.student_id AND s.deleted = false
        WHERE e.tenant_id = v_tenant_id AND e.deleted = false
    )
    SELECT
        gen_random_uuid(),
        v_tenant_id,
        gen_random_uuid(),
        NOW() - INTERVAL '20 days',
        NOW() - INTERVAL '20 days',
        NULL, NULL, false, NULL,
        r.evaluation_id,
        r.student_id,
        11 + (random() * 8)::numeric(6,2),
        NULL,
        'Calificacion registrada. Continuar con el ritmo.',
        NOW() - INTERVAL '20 days',
        COALESCE(r.teacher_pu, (SELECT public_uuid FROM edushift.users WHERE email = 'hugo.salazar@tecnosur.edushift.pe' AND deleted = false AND tenant_id = v_tenant_id LIMIT 1)),
        true
    FROM ranked r
    WHERE r.rn <= 2;

    SELECT COUNT(*) INTO v_count FROM edushift.grade_records WHERE tenant_id = v_tenant_id AND deleted = false;
    RAISE NOTICE 'V93.8: % grade_records seeded for tecnosur', v_count;
END;
$$;
