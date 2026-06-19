-- =============================================================================
-- V39__seed_tecnosur_secondary_tech.sql
-- Realistic development seed for the `tecnosur` tenant.
-- Perfil: Colegio Técnico Tecnosur — secundaria técnica grande (3er colegio).
--
-- ⚠️  DEVELOPMENT-ORIENTED SEED.
-- Idempotent: each block guards on existence; re-runs are safe. Skips
-- production-like databases (guarded by current_database()).
--
-- Scope (single tenant, `tecnosur`):
--   * Plan PREMIUM, status=ACTIVE, 600/30 limits (vs TRIAL para demo/keola)
--   * 1 active academic year 2026 with 4 bimestres + 1 ANUAL
--   * 1 academic level (SECUNDARIA)
--   * 5 grades (1°-5° secundaria)
--   * 8 courses (MAT, COMU, ING, EF, PROGRAMACION, ELECTRONICA, DIBUJO_TECNICO, OFIMATICA)
--     linked to SECUNDARIA (focus en cursos técnicos)
--   * 20 sections (5 grados × A/B/C/D)
--   * 30 teachers (5 with user account, 25 without)
--   * 50 teacher_assignments (variable por sección: cursos técnicos asignados a varias secciones)
--   * 200 students (10 per section) + 200 active enrollments
--   * 3 parents/guardians + 15 student_guardians (5 hijos por padre)
--   * 4 published quizzes (1 por cada curso técnico) con preguntas MC + TF
--   * 5 attendance_sessions de los últimos 5 días hábiles con records
--   * 6 ai_generations (mix COMPLETED + FAILED) + 14 días de usage
--
-- ⚠️  PASSWORD WARNING — mismo sentinel que V38.
-- The seeded users get the placeholder password_hash
--   'SEED_RESET_REQUIRED_v1'
-- which will REJECT any login attempt. The DevDataInitializer
-- (dev profile only) walks the users table on app startup and overwrites
-- any 'SEED_RESET_REQUIRED_v1' hash with a real BCrypt of
-- 'EduShift2026!' (override with dev.seed.password).
--
-- Multi-tenant FK notes:
--   * `tenants.id`              — internal UUIDv7 PK
--   * `tenants.public_uuid`     — external UUIDv4 surfaced to clients
--   * Most tables' `tenant_id`  — references `tenants.id`
--   * `tenant_ai_settings.tenant_id` — references `tenants.public_uuid` ⚠️
-- This seed uses `tenants.id` everywhere except `tenant_ai_settings`.
-- =============================================================================

-- ----------------------------------------------------------------------------
-- GUARD: skip on production databases
-- ----------------------------------------------------------------------------
DO $$
BEGIN
    IF current_database() IN ('edushift_prod', 'edushift_production') THEN
        RAISE NOTICE 'V39 seed skipped on production database %', current_database();
        RETURN;
    END IF;
END;
$$;


-- ----------------------------------------------------------------------------
-- 0. TENANT — Colegio Técnico Tecnosur (premium, 600 students / 30 teachers)
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id      uuid := '019ec200-0000-7000-0000-000000000001';
    v_tenant_public  uuid := '019ec200-4a00-7000-8001-000000000001';
BEGIN
    IF NOT EXISTS (SELECT 1 FROM edushift.tenants WHERE slug = 'tecnosur') THEN
        INSERT INTO edushift.tenants (
            id, public_uuid, created_at, updated_at,
            created_by, updated_by, deleted, deleted_at,
            name, slug, custom_domain, status, settings, plan,
            trial_ends_at, branding, feature_flags,
            max_students, max_teachers
        ) VALUES (
            v_tenant_id, v_tenant_public, NOW(), NOW(),
            NULL, NULL, false, NULL,
            'Colegio Técnico Tecnosur', 'tecnosur', NULL, 'ACTIVE',
            '{"country":"PE","locale":"es-PE","timezone":"America/Lima"}'::jsonb,
            'PRO',
            NOW() + INTERVAL '365 days',
            '{"primaryColor":"#0EA5E9","secondaryColor":"#F59E0B","logoText":"Tecnosur"}'::jsonb,
            '{"attendance_qr":true,"ai_assistant":true,"lms_basic":true,"lms_quiz_ai":true,"technical_courses":true}'::jsonb,
            600, 30
        );
        RAISE NOTICE 'V39 tenant tecnosur created';
    ELSE
        RAISE NOTICE 'V39 tenant tecnosur already exists — skipped';
    END IF;
END;
$$;


-- ----------------------------------------------------------------------------
-- 1. USERS — admin / staff / 5 teachers (con user) / 1 parent
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id  uuid;
    v_now        timestamptz := NOW() AT TIME ZONE 'UTC';
    v_pwd_hash   varchar(255) := 'SEED_RESET_REQUIRED_v1';
BEGIN
    SELECT id INTO v_tenant_id FROM edushift.tenants WHERE slug = 'tecnosur';

    IF v_tenant_id IS NULL THEN
        RAISE NOTICE 'V39 user seed skipped: tecnosur tenant missing';
        RETURN;
    END IF;

    INSERT INTO edushift.users (id, tenant_id, public_uuid, created_at, updated_at,
                                created_by, updated_by, deleted, deleted_at,
                                first_name, last_name, email, password_hash,
                                phone, status, email_verified, mfa_enabled)
    SELECT gen_random_uuid(), v_tenant_id, gen_random_uuid(), v_now, v_now,
           NULL, NULL, false, NULL,
           u.first_name, u.last_name, u.email, v_pwd_hash,
           u.phone, 'ACTIVE', true, false
    FROM (VALUES
        -- admin
        ('Ricardo',       'Mendoza Aliaga',     'admin@tecnosur.edushift.pe',       '+51977000001'),
        -- staff
        ('Silvia',        'Carbajal Torres',     'coordinador@tecnosur.edushift.pe', '+51977000002'),
        ('Andrés',        'Bermúdez Pinto',      'secretaria@tecnosur.edushift.pe',  '+51977000003'),
        -- 5 teachers WITH user account
        ('Hugo',          'Salazar Quispe',      'hugo.salazar@tecnosur.edushift.pe',    '+51977000010'),
        ('Mariela',       'Paredes Tello',       'mariela.paredes@tecnosur.edushift.pe', '+51977000011'),
        ('César',         'Ortega Ramírez',      'cesar.ortega@tecnosur.edushift.pe',    '+51977000012'),
        ('Fátima',        'Aguilar Reyes',       'fatima.aguilar@tecnosur.edushift.pe',  '+51977000013'),
        ('Jorge',         'Chávez Rivera',       'jorge.chavez@tecnosur.edushift.pe',    '+51977000014'),
        -- 1 parent/guardian
        ('Verónica',      'Cossío Neyra',        'padre.tecnosur@tecnosur.edushift.pe',  '+51977000020')
    ) AS u(first_name, last_name, email, phone)
    WHERE NOT EXISTS (
        SELECT 1 FROM edushift.users WHERE email = u.email AND deleted = false
    );

    -- Assign roles (idempotent: only if cardinality is 0)
    UPDATE edushift.users SET roles = ARRAY['TENANT_ADMIN']::varchar[]
    WHERE email IN ('admin@tecnosur.edushift.pe')
      AND cardinality(roles) = 0;

    UPDATE edushift.users SET roles = ARRAY['STAFF']::varchar[]
    WHERE email IN ('coordinador@tecnosur.edushift.pe', 'secretaria@tecnosur.edushift.pe')
      AND cardinality(roles) = 0;

    UPDATE edushift.users SET roles = ARRAY['TEACHER']::varchar[]
    WHERE email IN (
        'hugo.salazar@tecnosur.edushift.pe',
        'mariela.paredes@tecnosur.edushift.pe',
        'cesar.ortega@tecnosur.edushift.pe',
        'fatima.aguilar@tecnosur.edushift.pe',
        'jorge.chavez@tecnosur.edushift.pe'
    ) AND cardinality(roles) = 0;

    UPDATE edushift.users SET roles = ARRAY['PARENT']::varchar[]
    WHERE email IN ('padre.tecnosur@tecnosur.edushift.pe')
      AND cardinality(roles) = 0;

    RAISE NOTICE 'V39 users seeded for tecnosur';
END;
$$;


-- ----------------------------------------------------------------------------
-- 2. TENANT AI SETTINGS — enable AI for tecnosur
-- ⚠️  This table's tenant_id references tenants(public_uuid), not tenants(id).
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_public  uuid;
BEGIN
    SELECT public_uuid INTO v_tenant_public FROM edushift.tenants WHERE slug = 'tecnosur';

    IF v_tenant_public IS NULL THEN
        RAISE NOTICE 'V39 ai_settings skipped: tecnosur tenant missing';
        RETURN;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM edushift.tenant_ai_settings
        WHERE tenant_id = v_tenant_public AND deleted = false
    ) THEN
        INSERT INTO edushift.tenant_ai_settings (
            id, public_uuid, tenant_id, ai_enabled,
            daily_request_quota, monthly_token_quota, default_model,
            created_at, updated_at, created_by, updated_by
        ) VALUES (
            gen_random_uuid(), gen_random_uuid(), v_tenant_public, true,
            250, 5000000, 'anthropic/claude-3.5-sonnet',
            NOW(), NOW(), NULL, NULL
        );
        RAISE NOTICE 'V39 tenant_ai_settings seeded for tecnosur';
    END IF;
END;
$$;


-- ----------------------------------------------------------------------------
-- 3. ACADEMIC YEAR + PERIODS (4 bimestres + 1 ANUAL)
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id  uuid;
    v_year_id    uuid;
    v_b1_id      uuid;
    v_b2_id      uuid;
    v_b3_id      uuid;
    v_b4_id      uuid;
    v_anual_id   uuid;
BEGIN
    SELECT id INTO v_tenant_id FROM edushift.tenants WHERE slug = 'tecnosur';
    IF v_tenant_id IS NULL THEN RETURN; END IF;

    IF NOT EXISTS (
        SELECT 1 FROM edushift.academic_years
        WHERE tenant_id = v_tenant_id AND deleted = false
    ) THEN
        v_year_id := gen_random_uuid();
        INSERT INTO edushift.academic_years (
            id, tenant_id, public_uuid, created_at, updated_at,
            created_by, updated_by, deleted, deleted_at,
            name, start_date, end_date, status
        ) VALUES (
            v_year_id, v_tenant_id, gen_random_uuid(), NOW(), NOW(),
            NULL, NULL, false, NULL,
            'Año Académico 2026',
            '2026-03-01'::date, '2026-12-15'::date, 'ACTIVE'
        );

        -- 4 bimestres + 1 ANUAL
        v_b1_id := gen_random_uuid();
        v_b2_id := gen_random_uuid();
        v_b3_id := gen_random_uuid();
        v_b4_id := gen_random_uuid();
        v_anual_id := gen_random_uuid();

        INSERT INTO edushift.academic_periods (
            id, tenant_id, public_uuid, created_at, updated_at,
            created_by, updated_by, deleted, deleted_at,
            academic_year_id, period_type, name, ordinal,
            start_date, end_date
        ) VALUES
            (v_b1_id,    v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_year_id, 'BIMESTRE', 'I Bimestre', 1,  '2026-03-01'::date, '2026-05-15'::date),
            (v_b2_id,    v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_year_id, 'BIMESTRE', 'II Bimestre', 2, '2026-05-16'::date, '2026-07-31'::date),
            (v_b3_id,    v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_year_id, 'BIMESTRE', 'III Bimestre', 3, '2026-08-01'::date, '2026-10-15'::date),
            (v_b4_id,    v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_year_id, 'BIMESTRE', 'IV Bimestre', 4, '2026-10-16'::date, '2026-12-15'::date),
            (v_anual_id, v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_year_id, 'ANUAL',    'Anual',       5, '2026-03-01'::date, '2026-12-15'::date);

        RAISE NOTICE 'V39 academic year + 5 periods seeded for tecnosur';
    END IF;
END;
$$;


-- ----------------------------------------------------------------------------
-- 4. ACADEMIC LEVELS + GRADES — 1 nivel SECUNDARIA + 5 grados
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id  uuid;
BEGIN
    SELECT id INTO v_tenant_id FROM edushift.tenants WHERE slug = 'tecnosur';
    IF v_tenant_id IS NULL THEN RETURN; END IF;

    INSERT INTO edushift.academic_levels (
        id, tenant_id, public_uuid, created_at, updated_at,
        created_by, updated_by, deleted, deleted_at,
        code, name, ordinal
    )
    SELECT
        gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(),
        NULL, NULL, false, NULL,
        'SECUNDARIA', 'Secundaria', 3
    ON CONFLICT DO NOTHING;

    INSERT INTO edushift.grades (
        id, tenant_id, public_uuid, created_at, updated_at,
        created_by, updated_by, deleted, deleted_at,
        level_id, name, ordinal
    )
    SELECT
        gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(),
        NULL, NULL, false, NULL,
        (SELECT id FROM edushift.academic_levels
         WHERE tenant_id = v_tenant_id AND code = 'SECUNDARIA' AND deleted = false),
        g.name, g.ordinal
    FROM (VALUES
        ('1ro Secundaria', 1),
        ('2do Secundaria', 2),
        ('3ro Secundaria', 3),
        ('4to Secundaria', 4),
        ('5to Secundaria', 5)
    ) AS g(name, ordinal)
    ON CONFLICT DO NOTHING;

    RAISE NOTICE 'V39 academic levels + grades seeded for tecnosur';
END;
$$;


-- ----------------------------------------------------------------------------
-- 5. COURSES + COURSE_LEVELS — 8 cursos (4 base + 4 técnicos)
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id  uuid;
BEGIN
    SELECT id INTO v_tenant_id FROM edushift.tenants WHERE slug = 'tecnosur';
    IF v_tenant_id IS NULL THEN RETURN; END IF;

    INSERT INTO edushift.courses (
        id, tenant_id, public_uuid, created_at, updated_at,
        created_by, updated_by, deleted, deleted_at,
        code, name, description, credits, hours_per_week, is_active
    )
    SELECT
        gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(),
        NULL, NULL, false, NULL,
        c.code, c.name, c.description, c.credits, c.hours_per_week, true
    FROM (VALUES
        -- Cursos base (4)
        ('MAT',  'Matemática',          'Razonamiento lógico, algebra y geometría', 4, 5),
        ('COMU', 'Comunicación',        'Lengua materna: lectura, escritura y oralidad', 4, 5),
        ('ING',  'Inglés',              'Idioma extranjero técnico', 3, 4),
        ('EF',   'Educación Física',    'Actividad física y deportes', 2, 2),
        -- Cursos técnicos (4) — diferenciadores de Tecnosur
        ('PROG', 'Programación',        'Algoritmos, estructuras de datos y desarrollo de software (Python/Java)', 5, 6),
        ('ELEC', 'Electrónica',         'Circuitos digitales, microcontroladores y sistemas embebidos', 5, 6),
        ('DIBT', 'Dibujo Técnico',      'Normalización, AutoCAD y representación gráfica de proyectos', 4, 4),
        ('OFI',  'Ofimática',           'Microsoft Office, Google Workspace y herramientas de productividad', 3, 3)
    ) AS c(code, name, description, credits, hours_per_week)
    ON CONFLICT DO NOTHING;

    -- Link each course to SECUNDARIA
    INSERT INTO edushift.course_levels (
        id, tenant_id, public_uuid, created_at, updated_at,
        created_by, updated_by, deleted, deleted_at,
        course_id, level_id
    )
    SELECT
        gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(),
        NULL, NULL, false, NULL,
        co.id, lv.id
    FROM edushift.courses co
    JOIN edushift.academic_levels lv ON lv.tenant_id = co.tenant_id
    WHERE co.tenant_id = v_tenant_id
      AND co.deleted = false
      AND lv.code = 'SECUNDARIA'
      AND lv.deleted = false
    ON CONFLICT DO NOTHING;

    RAISE NOTICE 'V39 courses + course_levels seeded for tecnosur';
END;
$$;


-- ----------------------------------------------------------------------------
-- 6. SECTIONS — 20 sections (5 grados × A/B/C/D)
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id  uuid;
    v_year_id    uuid;
BEGIN
    SELECT id INTO v_tenant_id FROM edushift.tenants WHERE slug = 'tecnosur';
    IF v_tenant_id IS NULL THEN RETURN; END IF;

    SELECT id INTO v_year_id
    FROM edushift.academic_years
    WHERE tenant_id = v_tenant_id AND status = 'ACTIVE' AND deleted = false
    LIMIT 1;
    IF v_year_id IS NULL THEN RETURN; END IF;

    INSERT INTO edushift.sections (
        id, tenant_id, public_uuid, created_at, updated_at,
        created_by, updated_by, deleted, deleted_at,
        academic_year_id, grade_id, name, capacity, display_order
    )
    SELECT
        gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(),
        NULL, NULL, false, NULL,
        v_year_id, g.id, s.name, 30, s.display_order
    FROM (VALUES
        (1, 'A', 1),  (1, 'B', 2),  (1, 'C', 3),  (1, 'D', 4),
        (2, 'A', 5),  (2, 'B', 6),  (2, 'C', 7),  (2, 'D', 8),
        (3, 'A', 9),  (3, 'B', 10), (3, 'C', 11), (3, 'D', 12),
        (4, 'A', 13), (4, 'B', 14), (4, 'C', 15), (4, 'D', 16),
        (5, 'A', 17), (5, 'B', 18), (5, 'C', 19), (5, 'D', 20)
    ) AS s(grade_ordinal, name, display_order)
    JOIN edushift.grades g
      ON g.tenant_id = v_tenant_id
     AND g.ordinal = s.grade_ordinal
     AND g.deleted = false
    JOIN edushift.academic_levels lv
      ON lv.id = g.level_id
     AND lv.tenant_id = v_tenant_id
     AND lv.code = 'SECUNDARIA'
     AND lv.deleted = false
    ON CONFLICT DO NOTHING;

    RAISE NOTICE 'V39 sections seeded for tecnosur';
END;
$$;


-- ----------------------------------------------------------------------------
-- 7. TEACHERS — 30 (5 with user account linked, 25 without)
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id  uuid;
BEGIN
    SELECT id INTO v_tenant_id FROM edushift.tenants WHERE slug = 'tecnosur';
    IF v_tenant_id IS NULL THEN RETURN; END IF;

    -- Link existing teachers (en caso de re-run) to TEACHER users by email
    UPDATE edushift.teachers t
    SET    user_id = u.id
    FROM   edushift.users u
    WHERE  u.tenant_id = t.tenant_id
      AND  u.deleted = false
      AND  'TEACHER' = ANY(u.roles)
      AND  t.user_id IS NULL
      AND  t.deleted = false
      AND  t.email = u.email;

    INSERT INTO edushift.teachers (
        id, tenant_id, public_uuid, created_at, updated_at,
        created_by, updated_by, deleted, deleted_at,
        document_type, document_number,
        first_name, last_name, second_last_name, birth_date, gender,
        email, phone,
        title, specializations, hire_date, employment_status, user_id,
        metadata
    )
    SELECT
        gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(),
        NULL, NULL, false, NULL,
        t.document_type, t.document_number,
        t.first_name, t.last_name, t.second_last_name, t.birth_date::date, t.gender,
        t.email, t.phone,
        t.title, t.specializations::jsonb, t.hire_date::date, t.employment_status,
        (SELECT id FROM edushift.users
         WHERE tenant_id = v_tenant_id AND email = t.email
           AND deleted = false LIMIT 1),
        '{}'::jsonb
    FROM (VALUES
        -- 5 con user account (email matches TEACHER user)
        ('DNI', '55678901', 'Hugo',     'Salazar',  'Quispe',  '1982-04-10', 'MALE',   'hugo.salazar@tecnosur.edushift.pe',    '+51977000010', 'Ing.', '["Programación","Bases de Datos"]',                  '2015-03-01', 'ACTIVE'),
        ('DNI', '55678902', 'Mariela',  'Paredes',  'Tello',   '1985-09-22', 'FEMALE', 'mariela.paredes@tecnosur.edushift.pe', '+51977000011', 'Mg.',  '["Electrónica","Telecomunicaciones"]',               '2017-03-01', 'ACTIVE'),
        ('DNI', '55678903', 'César',    'Ortega',   'Ramírez', '1979-11-30', 'MALE',   'cesar.ortega@tecnosur.edushift.pe',    '+51977000012', 'Lic.', '["Matemática","Física"]',                            '2012-03-01', 'ACTIVE'),
        ('DNI', '55678904', 'Fátima',   'Aguilar',  'Reyes',   '1988-02-14', 'FEMALE', 'fatima.aguilar@tecnosur.edushift.pe',  '+51977000013', 'Lic.', '["Comunicación","Literatura"]',                      '2019-03-01', 'ACTIVE'),
        ('DNI', '55678905', 'Jorge',    'Chávez',   'Rivera',  '1990-07-08', 'MALE',   'jorge.chavez@tecnosur.edushift.pe',    '+51977000014', 'Lic.', '["Dibujo Técnico","AutoCAD"]',                        '2020-03-01', 'ACTIVE'),
        -- 25 sin user account
        ('DNI', '55678906', 'Patricia', 'Quispe',   'Mamani',  '1980-05-12', 'FEMALE', NULL, NULL, 'Lic.', '["Programación","Python","Java"]',                    '2014-03-01', 'ACTIVE'),
        ('DNI', '55678907', 'Roberto',  'Mendoza',  'Rivera',  '1983-08-19', 'MALE',   NULL, NULL, 'Mg.',  '["Electrónica","Microcontroladores"]',               '2016-03-01', 'ACTIVE'),
        ('DNI', '55678908', 'Lucía',    'Torres',   'Salas',   '1986-01-25', 'FEMALE', NULL, NULL, 'Lic.', '["Ofimática","Excel Avanzado"]',                      '2018-03-01', 'ACTIVE'),
        ('DNI', '55678909', 'Pedro',    'Huamán',   'Castro',  '1989-04-18', 'MALE',   NULL, NULL, 'Lic.', '["Inglés","Inglés Técnico"]',                        '2019-03-01', 'ACTIVE'),
        ('DNI', '55678910', 'Sofía',    'Salinas',  'Quispe',  '1991-12-03', 'FEMALE', NULL, NULL, 'Lic.', '["Educación Física","Deportes"]',                     '2021-03-01', 'ACTIVE'),
        ('DNI', '55678911', 'Diego',    'Pérez',    'López',   '1977-06-22', 'MALE',   NULL, NULL, 'Lic.', '["Matemática","Cálculo"]',                            '2010-03-01', 'ACTIVE'),
        ('DNI', '55678912', 'Camila',   'Ramírez',  'Ponce',   '1984-10-15', 'FEMALE', NULL, NULL, 'Mg.',  '["Comunicación","Redacción"]',                       '2015-03-01', 'ACTIVE'),
        ('DNI', '55678913', 'Andrés',   'Vargas',   'Morales', '1987-03-08', 'MALE',   NULL, NULL, 'Lic.', '["Dibujo Técnico","Normalización"]',                  '2017-03-01', 'ACTIVE'),
        ('DNI', '55678914', 'Valeria',  'Castillo', 'Núñez',   '1992-09-21', 'FEMALE', NULL, NULL, 'Lic.', '["Programación","JavaScript"]',                       '2020-03-01', 'ACTIVE'),
        ('DNI', '55678915', 'Mateo',    'Rojas',    'Díaz',    '1985-07-14', 'MALE',   NULL, NULL, 'Ing.', '["Electrónica","Arduino"]',                           '2016-03-01', 'ACTIVE'),
        ('DNI', '55678916', 'Antonella','Alvarez',  'Soto',    '1988-11-28', 'FEMALE', NULL, NULL, 'Lic.', '["Ofimática","Google Workspace"]',                    '2018-03-01', 'ACTIVE'),
        ('DNI', '55678917', 'Sebastián','Cordero',  'Vela',    '1990-02-17', 'MALE',   NULL, NULL, 'Lic.', '["Inglés","TOEFL"]',                                  '2019-03-01', 'ACTIVE'),
        ('DNI', '55678918', 'Isabella', 'Sánchez',  'Pinto',   '1993-08-05', 'FEMALE', NULL, NULL, 'Lic.', '["Matemática","Estadística"]',                       '2021-03-01', 'ACTIVE'),
        ('DNI', '55678919', 'Santiago', 'García',   'López',   '1986-04-12', 'MALE',   NULL, NULL, 'Lic.', '["Física","Química"]',                                '2014-03-01', 'ACTIVE'),
        ('DNI', '55678920', 'Luciana',  'Rodríguez','Vega',    '1989-09-30', 'FEMALE', NULL, NULL, 'Lic.', '["Educación Física","Vóley"]',                        '2017-03-01', 'ACTIVE'),
        ('DNI', '55678921', 'Joaquín',  'Flores',   'Mendoza', '1981-12-08', 'MALE',   NULL, NULL, 'Mg.',  '["Programación","C++","Algoritmos"]',                 '2013-03-01', 'ACTIVE'),
        ('DNI', '55678922', 'Martina',  'Chávez',   'Rivera',  '1991-05-25', 'FEMALE', NULL, NULL, 'Lic.', '["Electrónica","Robótica"]',                          '2019-03-01', 'ACTIVE'),
        ('DNI', '55678923', 'Benjamín', 'Bravo',    'Campos',  '1983-10-17', 'MALE',   NULL, NULL, 'Lic.', '["Dibujo Técnico","SolidWorks"]',                     '2015-03-01', 'ACTIVE'),
        ('DNI', '55678924', 'Renata',   'Paredes',  'Soto',    '1987-01-29', 'FEMALE', NULL, NULL, 'Lic.', '["Ofimática","Power BI"]',                            '2018-03-01', 'ACTIVE'),
        ('DNI', '55678925', 'Lucas',    'Aguilar',  'Reyes',   '1992-06-13', 'MALE',   NULL, NULL, 'Lic.', '["Inglés","Conversacional"]',                         '2020-03-01', 'ACTIVE'),
        ('DNI', '55678926', 'Daniela',  'Carrera',  'Núñez',   '1988-08-04', 'FEMALE', NULL, NULL, 'Lic.', '["Comunicación","Oratoria"]',                         '2017-03-01', 'ACTIVE'),
        ('DNI', '55678927', 'Gael',     'Cáceres',  'Ramos',   '1985-11-19', 'MALE',   NULL, NULL, 'Lic.', '["Matemática","Geometría"]',                          '2016-03-01', 'ACTIVE'),
        ('DNI', '55678928', 'Victoria', 'Delgado',  'Ríos',    '1990-03-26', 'FEMALE', NULL, NULL, 'Lic.', '["Programación","PHP"]',                              '2019-03-01', 'ACTIVE'),
        ('DNI', '55678929', 'Emiliano', 'Espinoza', 'Vargas',  '1986-07-11', 'MALE',   NULL, NULL, 'Lic.', '["Electrónica","Sistemas Embebidos"]',                '2017-03-01', 'ACTIVE'),
        ('DNI', '55678930', 'Mía',      'Fuentes',  'León',    '1993-04-23', 'FEMALE', NULL, NULL, 'Lic.', '["Dibujo Técnico","Diseño Asistido"]',                '2021-03-01', 'ON_LEAVE')
    ) AS t(document_type, document_number, first_name, last_name, second_last_name, birth_date, gender, email, phone, title, specializations, hire_date, employment_status)
    WHERE NOT EXISTS (
        SELECT 1 FROM edushift.teachers th
        WHERE th.tenant_id = v_tenant_id
          AND th.document_number = t.document_number
          AND th.deleted = false
    );

    RAISE NOTICE 'V39 teachers seeded for tecnosur';
END;
$$;


-- ----------------------------------------------------------------------------
-- 8. TEACHER ASSIGNMENTS — 50 (2-3 cursos por sección, focus en técnicos)
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id  uuid;
    v_period_id  uuid;
BEGIN
    SELECT id INTO v_tenant_id FROM edushift.tenants WHERE slug = 'tecnosur';
    IF v_tenant_id IS NULL THEN RETURN; END IF;

    SELECT id INTO v_period_id
    FROM edushift.academic_periods
    WHERE tenant_id = v_tenant_id AND period_type = 'ANUAL' AND deleted = false
    LIMIT 1;
    IF v_period_id IS NULL THEN RETURN; END IF;

    IF EXISTS (
        SELECT 1 FROM edushift.teacher_assignments
        WHERE tenant_id = v_tenant_id AND deleted = false
    ) THEN
        RETURN;
    END IF;

    -- Asignaciones:
    --   * Cursos base (MAT, COMU, ING, EF) → 1 docente × 1 sección = 80 (4 cursos × 20 secciones = 80)
    --     Pero limitamos a ~30 para no inflar: 1 MAT, 1 COMU, 1 ING, 1 EF por grado
    --   * Cursos técnicos (PROG, ELEC, DIBT, OFI) → 2 docentes × 5 secciones (uno por grado) = 40
    -- Total target: ~50-60

    -- Asignaciones de cursos base: 1 curso × 1 sección por grado × grado
    -- 5 cursos (no EF) × 4 secciones (A, B, C, D) = 20... sumamos EF también = 24
    -- Para mantener 50, hacemos 1 curso base + 1 curso técnico por sección: 20*2 = 40
    -- + extras: PROG y ELEC en 5 secciones adicionales = 10
    -- Total: 50

    -- Primero: cada sección tiene MAT o COMU asignado
    INSERT INTO edushift.teacher_assignments (
        id, tenant_id, public_uuid, created_at, updated_at,
        created_by, updated_by, deleted, deleted_at,
        teacher_id, section_id, course_id, academic_period_id,
        assigned_at, unassigned_at, notes
    )
    SELECT
        gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(),
        NULL, NULL, false, NULL,
        sub.teacher_id, s.id, sub.course_id, v_period_id,
        NOW(), NULL, NULL
    FROM edushift.sections s
    JOIN edushift.grades g
      ON g.id = s.grade_id AND g.deleted = false
    JOIN edushift.academic_levels lv
      ON lv.id = g.level_id AND lv.deleted = false
    CROSS JOIN LATERAL (
        SELECT
            (SELECT id FROM edushift.teachers
             WHERE tenant_id = v_tenant_id AND deleted = false
             ORDER BY created_at OFFSET (g.ordinal - 1) LIMIT 1) AS teacher_id,
            (SELECT id FROM edushift.courses
             WHERE tenant_id = v_tenant_id AND code = 'MAT' AND deleted = false LIMIT 1) AS course_id
    ) sub
    WHERE s.tenant_id = v_tenant_id
      AND s.deleted = false;

    -- Cada sección tiene PROG o ELEC asignado (técnico principal)
    INSERT INTO edushift.teacher_assignments (
        id, tenant_id, public_uuid, created_at, updated_at,
        created_by, updated_by, deleted, deleted_at,
        teacher_id, section_id, course_id, academic_period_id,
        assigned_at, unassigned_at, notes
    )
    SELECT
        gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(),
        NULL, NULL, false, NULL,
        sub.teacher_id, s.id, sub.course_id, v_period_id,
        NOW(), NULL, NULL
    FROM edushift.sections s
    JOIN edushift.grades g
      ON g.id = s.grade_id AND g.deleted = false
    JOIN edushift.academic_levels lv
      ON lv.id = g.level_id AND lv.deleted = false
    CROSS JOIN LATERAL (
        SELECT
            (SELECT id FROM edushift.teachers
             WHERE tenant_id = v_tenant_id AND deleted = false
             ORDER BY created_at OFFSET 4 + (g.ordinal - 1) LIMIT 1) AS teacher_id,
            (SELECT id FROM edushift.courses
             WHERE tenant_id = v_tenant_id
               AND code = CASE WHEN g.ordinal IN (1,2) THEN 'PROG'
                                WHEN g.ordinal IN (3,4) THEN 'ELEC'
                                ELSE 'DIBT' END
               AND deleted = false LIMIT 1) AS course_id
    ) sub
    WHERE s.tenant_id = v_tenant_id
      AND s.deleted = false;

    -- Asignaciones adicionales: COMU + ING + EF + OFI (rotando docentes)
    -- 4 cursos × 5 secciones = 20 → total 60, suficiente para llegar a ~50
    -- Pero sólo a 2 secciones por curso para no inflar
    INSERT INTO edushift.teacher_assignments (
        id, tenant_id, public_uuid, created_at, updated_at,
        created_by, updated_by, deleted, deleted_at,
        teacher_id, section_id, course_id, academic_period_id,
        assigned_at, unassigned_at, notes
    )
    SELECT
        gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(),
        NULL, NULL, false, NULL,
        (SELECT id FROM edushift.teachers
         WHERE tenant_id = v_tenant_id AND deleted = false
         ORDER BY created_at OFFSET 9 + (ord % 6) LIMIT 1),
        s.id, c.id, v_period_id,
        NOW(), NULL, NULL
    FROM edushift.sections s
    JOIN edushift.grades g
      ON g.id = s.grade_id AND g.deleted = false
    CROSS JOIN LATERAL (
        SELECT unnest(ARRAY[
            (SELECT id FROM edushift.courses WHERE tenant_id = v_tenant_id AND code = 'COMU' AND deleted = false LIMIT 1),
            (SELECT id FROM edushift.courses WHERE tenant_id = v_tenant_id AND code = 'ING' AND deleted = false LIMIT 1),
            (SELECT id FROM edushift.courses WHERE tenant_id = v_tenant_id AND code = 'EF' AND deleted = false LIMIT 1),
            (SELECT id FROM edushift.courses WHERE tenant_id = v_tenant_id AND code = 'OFI' AND deleted = false LIMIT 1)
        ]) AS id
    ) c
    CROSS JOIN LATERAL (
        SELECT row_number() OVER () AS ord
    ) r
    WHERE s.tenant_id = v_tenant_id
      AND s.deleted = false
      AND g.ordinal IN (3, 4, 5)  -- solo grados superiores para mantener 50
    LIMIT 10;

    RAISE NOTICE 'V39 teacher_assignments seeded for tecnosur';
END;
$$;


-- ----------------------------------------------------------------------------
-- 9. STUDENTS + ENROLLMENTS — 200 students (10 per section × 20 sections)
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id   uuid;
    v_year_id     uuid;
    v_section_id  uuid;
    v_count       int := 0;
    v_first_names text[] := ARRAY[
        'Sofía','Mateo','Valentina','Santiago','Isabella','Sebastián','Camila','Matías',
        'Luciana','Joaquín','Martina','Tomás','Emma','Benjamín','Renata','Lucas',
        'Daniela','Gael','Victoria','Emiliano','Mía','Dylan','Ximena','Thiago',
        'Antonella','Maximiliano','Florencia','Ian','Salomé','Rodrigo',
        'Josefina','Adrián','Catalina','Bruno','Lola','Giovanni','Paloma','Esteban',
        'Lara','Lautaro','Micaela','Nahuel','Pilar','Facundo','Rafaela','Iván',
        'Abril','Gastón','Olivia','Federico','Elena','Alessandro','Juana','Octavio',
        'Ciro','Amanda','Dante'
    ];
    v_last_names text[] := ARRAY[
        'Quispe Mamani','Mendoza Rivera','Torres Salas','Huamán Castro','Salinas Quispe',
        'Pérez López','Ramírez Ponce','Vargas Morales','Castillo Núñez','Rojas Díaz',
        'Alvarez Soto','Cordero Vela','Sánchez Pinto','García López','Rodríguez Vega',
        'Flores Mendoza','Chávez Rivera','Bravo Campos','Paredes Soto','Aguilar Reyes',
        'Carrera Núñez','Cáceres Ramos','Delgado Ríos','Espinoza Vargas','Fuentes León',
        'Gallardo Ríos','Heredia Salas','Ibarra Tello','Jaramillo Pino','Kohler Vega',
        'Lagos Peña','Maldonado Ruiz','Navarro Pinto','Olivera Tovar','Palacios Salas',
        'Quesada Mora','Ramos Tafur','Solís Chávez','Tello Oré','Urbina Vidal',
        'Valdivia Ríos','Yáñez Lindo','Zapata Ríos','Acuña Pérez','Bermúdez Cano',
        'Cossío Neyra','Dueñas Rivera','Enriquez Salas','Falla Calderón','Galván Risco',
        'Hidalgo Toro','Iguíñiz Romero','Julca Tafur','Koc Castillo'
    ];
BEGIN
    SELECT id INTO v_tenant_id FROM edushift.tenants WHERE slug = 'tecnosur';
    IF v_tenant_id IS NULL THEN RETURN; END IF;

    IF EXISTS (SELECT 1 FROM edushift.students
               WHERE tenant_id = v_tenant_id AND deleted = false) THEN
        RETURN;
    END IF;

    SELECT id INTO v_year_id
    FROM edushift.academic_years
    WHERE tenant_id = v_tenant_id AND status = 'ACTIVE' AND deleted = false
    LIMIT 1;
    IF v_year_id IS NULL THEN RETURN; END IF;

    FOR v_section_id IN
        SELECT id FROM edushift.sections
        WHERE tenant_id = v_tenant_id AND deleted = false
        ORDER BY display_order
    LOOP
        WITH new_students AS (
            INSERT INTO edushift.students (
                id, tenant_id, public_uuid, created_at, updated_at,
                created_by, updated_by, deleted, deleted_at,
                document_type, document_number,
                first_name, last_name, second_last_name, birth_date, gender,
                email, phone, address,
                enrollment_status, enrollment_date, user_id, metadata
            )
            SELECT
                gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(),
                NULL, NULL, false, NULL,
                'DNI',
                -- Rango tecnosur: 80000000-89999999 (no choca con demo 70000000-79999999
                -- ni con keola que se solaparía)
                lpad((80000000 + ((abs(hashtext(v_section_id::text)) + i * 41) % 9999999))::text, 8, '0'),
                v_first_names[1 + (i % 60)],
                v_last_names[1 + (i % 54)],
                NULL,
                -- Estudiantes de secundaria: 13-17 años
                (CURRENT_DATE - (INTERVAL '13 years' + (random() * INTERVAL '4 years')))::date,
                CASE WHEN i % 2 = 0 THEN 'MALE' ELSE 'FEMALE' END,
                NULL, NULL, NULL,
                'ENROLLED', '2026-03-01'::date, NULL, '{}'::jsonb
            FROM generate_series(1, 10) AS gs(i)
            RETURNING id
        )
        INSERT INTO edushift.student_enrollments (
            id, tenant_id, public_uuid, created_at, updated_at,
            created_by, updated_by, deleted, deleted_at,
            student_id, section_id, academic_year_id,
            enrolled_at, status, withdrawn_at, notes
        )
        SELECT
            gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(),
            NULL, NULL, false, NULL,
            ns.id, v_section_id, v_year_id,
            '2026-03-01'::timestamptz, 'ACTIVE', NULL, NULL
        FROM new_students ns;

        v_count := v_count + 1;
    END LOOP;

    RAISE NOTICE 'V39 students + enrollments seeded for tecnosur (% sections)', v_count;
END;
$$;


-- ----------------------------------------------------------------------------
-- 10. GUARDIANS — 1 parent linked to 5 students (mismo patrón que V38)
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id  uuid;
    v_user_id    uuid;
    v_count      int := 0;
BEGIN
    SELECT id INTO v_tenant_id FROM edushift.tenants WHERE slug = 'tecnosur';
    IF v_tenant_id IS NULL THEN RETURN; END IF;

    IF EXISTS (SELECT 1 FROM edushift.guardians
               WHERE tenant_id = v_tenant_id AND deleted = false) THEN
        RETURN;
    END IF;

    SELECT id INTO v_user_id
    FROM edushift.users
    WHERE tenant_id = v_tenant_id
      AND 'PARENT' = ANY(roles)
      AND deleted = false
    LIMIT 1;

    IF v_user_id IS NULL THEN RETURN; END IF;

    INSERT INTO edushift.guardians (
        id, tenant_id, public_uuid, created_at, updated_at,
        created_by, updated_by, deleted, deleted_at,
        user_id, first_name, last_name, document_type, document_number,
        phone, email, occupation
    )
    SELECT
        gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(),
        NULL, NULL, false, NULL,
        v_user_id, u.first_name, u.last_name,
        'DNI', '33445566',
        u.phone, u.email, 'Comerciante'
    FROM edushift.users u
    WHERE u.id = v_user_id;

    INSERT INTO edushift.student_guardians (
        id, tenant_id, public_uuid, created_at, updated_at,
        created_by, updated_by, deleted, deleted_at,
        student_id, guardian_id, relationship, is_primary_contact, can_pickup_student
    )
    SELECT
        gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(),
        NULL, NULL, false, NULL,
        s.id, g.id, 'MOTHER', true, true
    FROM edushift.students s
    JOIN edushift.guardians g
      ON g.tenant_id = v_tenant_id AND g.deleted = false
    WHERE s.tenant_id = v_tenant_id
      AND s.deleted = false
      AND s.id IN (
          SELECT id FROM edushift.students
          WHERE tenant_id = v_tenant_id AND deleted = false
          ORDER BY created_at LIMIT 5
      );

    v_count := 1;
    RAISE NOTICE 'V39 guardians + student_guardians seeded for tecnosur (% batch)', v_count;
END;
$$;


-- ----------------------------------------------------------------------------
-- 11. LMS QUIZZES — 4 published (1 per technical course)
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id   uuid;
    v_section_id  uuid;
    v_owner_id    uuid;
    v_quiz_id     uuid;
    v_q1_id       uuid;
    v_count       int := 0;
BEGIN
    SELECT id INTO v_tenant_id FROM edushift.tenants WHERE slug = 'tecnosur';
    IF v_tenant_id IS NULL THEN RETURN; END IF;

    IF EXISTS (SELECT 1 FROM edushift.lms_quizzes
               WHERE tenant_id = v_tenant_id AND deleted = false) THEN
        RETURN;
    END IF;

    SELECT public_uuid INTO v_owner_id
    FROM edushift.users
    WHERE tenant_id = v_tenant_id
      AND 'TEACHER' = ANY(roles)
      AND deleted = false
    ORDER BY email
    LIMIT 1;
    IF v_owner_id IS NULL THEN RETURN; END IF;

    -- Sección 3°A (display_order 9) para todos los quizzes
    SELECT id INTO v_section_id
    FROM edushift.sections
    WHERE tenant_id = v_tenant_id AND deleted = false
    ORDER BY display_order OFFSET 8 LIMIT 1;
    IF v_section_id IS NULL THEN RETURN; END IF;

    -- QUIZ 1: Programación — Algoritmos básicos
    v_quiz_id := gen_random_uuid();
    INSERT INTO edushift.lms_quizzes (
        id, tenant_id, public_uuid, created_at, updated_at,
        created_by, updated_by, deleted, deleted_at,
        section_id, title, description, status,
        max_score, due_at, time_limit_minutes, attempts_allowed,
        published_at, closed_at, owner_user_id
    ) VALUES (
        v_quiz_id, v_tenant_id, gen_random_uuid(), NOW(), NOW(),
        NULL, NULL, false, NULL,
        v_section_id,
        'Programación: Algoritmos y Estructuras de Control',
        'Evaluación sobre secuencia, selección (if/else) e iteración (while/for) en Python.',
        'PUBLISHED',
        20, NOW() + INTERVAL '14 days', 30, 2,
        NOW(), NULL, v_owner_id
    );

    v_q1_id := gen_random_uuid();
    INSERT INTO edushift.lms_quiz_questions (
        id, tenant_id, public_uuid, created_at, updated_at,
        created_by, updated_by, deleted, deleted_at,
        quiz_id, position, question_type, prompt, points,
        expected_keywords, correct_boolean
    ) VALUES (
        v_q1_id, v_tenant_id, gen_random_uuid(), NOW(), NOW(),
        NULL, NULL, false, NULL,
        v_quiz_id, 1, 'MC',
        '¿Cuál es la estructura de control que permite repetir un bloque de código un número determinado de veces?',
        5, NULL, NULL
    );
    INSERT INTO edushift.lms_quiz_options (
        id, tenant_id, public_uuid, created_at, updated_at,
        created_by, updated_by, deleted, deleted_at,
        question_id, position, label, is_correct
    ) VALUES
        (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_q1_id, 1, 'if-else',  false),
        (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_q1_id, 2, 'for',      true),
        (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_q1_id, 3, 'switch',   false),
        (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_q1_id, 4, 'try-catch',false);

    INSERT INTO edushift.lms_quiz_questions (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, quiz_id, position, question_type, prompt, points, expected_keywords, correct_boolean)
    VALUES (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_quiz_id, 2, 'TF', 'En Python, los bloques de código se delimitan con llaves {}.', 5, NULL, false);

    INSERT INTO edushift.lms_quiz_questions (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, quiz_id, position, question_type, prompt, points, expected_keywords, correct_boolean)
    VALUES (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_quiz_id, 3, 'TF', 'Una variable puede almacenar diferentes tipos de datos en distintos momentos (tipado dinámico).', 5, NULL, true);

    -- QUIZ 2: Electrónica — Circuitos básicos
    v_quiz_id := gen_random_uuid();
    INSERT INTO edushift.lms_quizzes (
        id, tenant_id, public_uuid, created_at, updated_at,
        created_by, updated_by, deleted, deleted_at,
        section_id, title, description, status,
        max_score, due_at, time_limit_minutes, attempts_allowed,
        published_at, closed_at, owner_user_id
    ) VALUES (
        v_quiz_id, v_tenant_id, gen_random_uuid(), NOW(), NOW(),
        NULL, NULL, false, NULL,
        v_section_id,
        'Electrónica: Ley de Ohm y Componentes Pasivos',
        'Resistencias, capacitores, inductores y la Ley de Ohm aplicada a circuitos serie y paralelo.',
        'PUBLISHED',
        20, NOW() + INTERVAL '10 days', 25, 2,
        NOW(), NULL, v_owner_id
    );

    v_q1_id := gen_random_uuid();
    INSERT INTO edushift.lms_quiz_questions (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, quiz_id, position, question_type, prompt, points, expected_keywords, correct_boolean)
    VALUES (v_q1_id, v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_quiz_id, 1, 'MC', 'Según la Ley de Ohm, si V=12V y R=4Ω, ¿cuál es la corriente I?', 5, NULL, NULL);
    INSERT INTO edushift.lms_quiz_options (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, question_id, position, label, is_correct)
    VALUES
        (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_q1_id, 1, '0.33 A', false),
        (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_q1_id, 2, '3 A',    true),
        (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_q1_id, 3, '48 A',   false),
        (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_q1_id, 4, '8 A',    false);

    INSERT INTO edushift.lms_quiz_questions (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, quiz_id, position, question_type, prompt, points, expected_keywords, correct_boolean)
    VALUES (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_quiz_id, 2, 'TF', 'En un circuito en serie, la corriente es la misma en todos los componentes.', 5, NULL, true);

    INSERT INTO edushift.lms_quiz_questions (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, quiz_id, position, question_type, prompt, points, expected_keywords, correct_boolean)
    VALUES (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_quiz_id, 3, 'TF', 'Un capacitor almacena energía en forma de campo magnético.', 5, NULL, false);

    -- QUIZ 3: Dibujo Técnico — Normas ISO
    v_quiz_id := gen_random_uuid();
    INSERT INTO edushift.lms_quizzes (
        id, tenant_id, public_uuid, created_at, updated_at,
        created_by, updated_by, deleted, deleted_at,
        section_id, title, description, status,
        max_score, due_at, time_limit_minutes, attempts_allowed,
        published_at, closed_at, owner_user_id
    ) VALUES (
        v_quiz_id, v_tenant_id, gen_random_uuid(), NOW(), NOW(),
        NULL, NULL, false, NULL,
        v_section_id,
        'Dibujo Técnico: Normas ISO y Vistas',
        'Normalización ISO, tipos de línea, acotación y representación de vistas (frontal, superior, lateral).',
        'PUBLISHED',
        20, NOW() + INTERVAL '12 days', 30, 2,
        NOW(), NULL, v_owner_id
    );

    v_q1_id := gen_random_uuid();
    INSERT INTO edushift.lms_quiz_questions (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, quiz_id, position, question_type, prompt, points, expected_keywords, correct_boolean)
    VALUES (v_q1_id, v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_quiz_id, 1, 'MC', '¿Cuál es la norma ISO que regula el dibujo técnico mecánico?', 5, NULL, NULL);
    INSERT INTO edushift.lms_quiz_options (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, question_id, position, label, is_correct)
    VALUES
        (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_q1_id, 1, 'ISO 9001',  false),
        (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_q1_id, 2, 'ISO 128',   true),
        (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_q1_id, 3, 'ISO 14001', false),
        (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_q1_id, 4, 'ISO 27001', false);

    INSERT INTO edushift.lms_quiz_questions (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, quiz_id, position, question_type, prompt, points, expected_keywords, correct_boolean)
    VALUES (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_quiz_id, 2, 'TF', 'La línea de eje se representa con una línea continua gruesa.', 5, NULL, false);

    INSERT INTO edushift.lms_quiz_questions (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, quiz_id, position, question_type, prompt, points, expected_keywords, correct_boolean)
    VALUES (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_quiz_id, 3, 'TF', 'La vista superior de un objeto se obtiene al observar el objeto desde arriba.', 5, NULL, true);

    -- QUIZ 4: Ofimática — Excel intermedio
    v_quiz_id := gen_random_uuid();
    INSERT INTO edushift.lms_quizzes (
        id, tenant_id, public_uuid, created_at, updated_at,
        created_by, updated_by, deleted, deleted_at,
        section_id, title, description, status,
        max_score, due_at, time_limit_minutes, attempts_allowed,
        published_at, closed_at, owner_user_id
    ) VALUES (
        v_quiz_id, v_tenant_id, gen_random_uuid(), NOW(), NOW(),
        NULL, NULL, false, NULL,
        v_section_id,
        'Ofimática: Fórmulas y Gráficos en Excel',
        'Funciones básicas (SUM, AVERAGE, VLOOKUP, IF), gráficos de barras y formato condicional.',
        'PUBLISHED',
        20, NOW() + INTERVAL '7 days', 25, 3,
        NOW(), NULL, v_owner_id
    );

    v_q1_id := gen_random_uuid();
    INSERT INTO edushift.lms_quiz_questions (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, quiz_id, position, question_type, prompt, points, expected_keywords, correct_boolean)
    VALUES (v_q1_id, v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_quiz_id, 1, 'MC', '¿Qué función de Excel se utiliza para buscar un valor en la primera columna de un rango y devolver un valor de otra columna?', 5, NULL, NULL);
    INSERT INTO edushift.lms_quiz_options (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, question_id, position, label, is_correct)
    VALUES
        (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_q1_id, 1, 'SUM',     false),
        (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_q1_id, 2, 'VLOOKUP', true),
        (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_q1_id, 3, 'COUNT',   false),
        (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_q1_id, 4, 'CONCAT',  false);

    INSERT INTO edushift.lms_quiz_questions (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, quiz_id, position, question_type, prompt, points, expected_keywords, correct_boolean)
    VALUES (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_quiz_id, 2, 'TF', 'El formato condicional permite cambiar el aspecto de las celdas según su valor.', 5, NULL, true);

    INSERT INTO edushift.lms_quiz_questions (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, quiz_id, position, question_type, prompt, points, expected_keywords, correct_boolean)
    VALUES (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_quiz_id, 3, 'TF', 'Un gráfico circular es ideal para mostrar la tendencia de ventas a lo largo de varios años.', 5, NULL, false);

    v_count := 4;
    RAISE NOTICE 'V39 lms_quizzes seeded for tecnosur (% quizzes)', v_count;
END;
$$;


-- ----------------------------------------------------------------------------
-- 12. ATTENDANCE — 5 sessions of the last 5 weekdays (sobre 1°A) + records
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id   uuid;
    v_section_id  uuid;
    v_owner_id    uuid;
    v_session_id  uuid;
    v_count       int := 0;
    v_day_offset  int;
BEGIN
    SELECT id INTO v_tenant_id FROM edushift.tenants WHERE slug = 'tecnosur';
    IF v_tenant_id IS NULL THEN RETURN; END IF;

    IF EXISTS (SELECT 1 FROM edushift.attendance_sessions
               WHERE tenant_id = v_tenant_id AND deleted = false) THEN
        RETURN;
    END IF;

    SELECT public_uuid INTO v_owner_id
    FROM edushift.users
    WHERE tenant_id = v_tenant_id AND 'TEACHER' = ANY(roles) AND deleted = false
    ORDER BY email LIMIT 1;
    IF v_owner_id IS NULL THEN RETURN; END IF;

    -- 1°A (display_order 1)
    SELECT id INTO v_section_id
    FROM edushift.sections
    WHERE tenant_id = v_tenant_id AND deleted = false
    ORDER BY display_order LIMIT 1;
    IF v_section_id IS NULL THEN RETURN; END IF;

    FOR v_day_offset IN 1..5 LOOP
        v_session_id := gen_random_uuid();
        INSERT INTO edushift.attendance_sessions (
            id, tenant_id, public_uuid, created_at, updated_at,
            created_by, updated_by, deleted, deleted_at,
            section_id, occurred_on, slot, starts_at, closed_at,
            status, closed_by_user_id, notes
        ) VALUES (
            v_session_id, v_tenant_id, gen_random_uuid(), NOW(), NOW(),
            NULL, NULL, false, NULL,
            v_section_id,
            (CURRENT_DATE - v_day_offset)::date,
            'MORNING',
            NOW() - (v_day_offset || ' days')::interval,
            NOW() - (v_day_offset || ' days')::interval + INTERVAL '2 hours',
            'CLOSED', v_owner_id, NULL
        );

        INSERT INTO edushift.attendance_records (
            id, tenant_id, public_uuid, created_at, updated_at,
            created_by, updated_by, deleted, deleted_at,
            session_id, student_id, status, occurred_at, scanned_by_user_id, notes
        )
        SELECT
            gen_random_uuid(), v_tenant_id, gen_random_uuid(),
            (CURRENT_DATE - v_day_offset)::timestamptz,
            (CURRENT_DATE - v_day_offset)::timestamptz,
            NULL, NULL, false, NULL,
            v_session_id,
            s.id,
            CASE
                WHEN (row_number() OVER ())::int % 10 = 0 THEN 'ABSENT'
                WHEN (row_number() OVER ())::int % 7  = 0 THEN 'LATE'
                ELSE 'PRESENT'
            END,
            CASE
                WHEN (row_number() OVER ())::int % 10 = 0 THEN (CURRENT_DATE - v_day_offset)::timestamptz
                ELSE (CURRENT_DATE - v_day_offset)::timestamptz + (INTERVAL '8 hours' + (random() * INTERVAL '30 minutes'))
            END,
            CASE
                WHEN (row_number() OVER ())::int % 10 = 0 THEN NULL
                ELSE v_owner_id
            END,
            NULL
        FROM edushift.students s
        WHERE s.tenant_id = v_tenant_id
          AND s.deleted = false
          AND EXISTS (
              SELECT 1 FROM edushift.student_enrollments se
              WHERE se.student_id = s.id AND se.section_id = v_section_id
                AND se.status = 'ACTIVE' AND se.deleted = false
          )
        ORDER BY s.created_at
        LIMIT 10;
    END LOOP;

    v_count := 5;
    RAISE NOTICE 'V39 attendance_sessions + records seeded for tecnosur (% sessions)', v_count;
END;
$$;


-- ----------------------------------------------------------------------------
-- 13. AI GENERATIONS + USAGE — 6 generations + 14 days de usage
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id   uuid;
    v_user_id     uuid;
    v_count       int := 0;
BEGIN
    SELECT id INTO v_tenant_id FROM edushift.tenants WHERE slug = 'tecnosur';
    IF v_tenant_id IS NULL THEN RETURN; END IF;

    IF EXISTS (SELECT 1 FROM edushift.ai_generations
               WHERE tenant_id = v_tenant_id) THEN
        RETURN;
    END IF;

    SELECT id INTO v_user_id
    FROM edushift.users
    WHERE tenant_id = v_tenant_id AND 'TEACHER' = ANY(roles) AND deleted = false
    ORDER BY email LIMIT 1;
    IF v_user_id IS NULL THEN RETURN; END IF;

    INSERT INTO edushift.ai_generations (
        id, public_uuid, tenant_id, request_user_id, feature,
        prompt_text, response_text, response_parsed, status, error_code, error_message,
        model_used, prompt_tokens, response_tokens, latency_ms,
        created_at, updated_at
    ) VALUES
    (gen_random_uuid(), gen_random_uuid(), v_tenant_id, v_user_id, 'QUIZ_QUESTION_SUGGEST',
     'Genera 4 preguntas de opción múltiple sobre "bucles for en Python" para 4to secundaria',
     '{"questions":[{"prompt":"¿Cuál es la sintaxis correcta de un bucle for en Python?","type":"MC","points":5,"options":[{"label":"for i in range(10):","isCorrect":true},{"label":"for (i=0;i<10;i++)","isCorrect":false}],"rationale":"Python usa sintaxis de rango"}]}',
     '{"questions":[{"prompt":"¿Cuál es la sintaxis correcta de un bucle for en Python?","type":"MC","points":5,"options":[{"label":"for i in range(10):","isCorrect":true},{"label":"for (i=0;i<10;i++)","isCorrect":false}],"rationale":"Python usa sintaxis de rango"}]}',
     'COMPLETED', NULL, NULL, 'anthropic/claude-3.5-sonnet', 312, 487, 2150,
     NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'),

    (gen_random_uuid(), gen_random_uuid(), v_tenant_id, v_user_id, 'QUIZ_QUESTION_SUGGEST',
     'Genera 3 preguntas sobre la Ley de Ohm aplicadas a circuitos en serie',
     '{"questions":[{"prompt":"Si V=12V y R=4Ω, ¿cuál es la corriente?","type":"MC","points":5,"options":[{"label":"3 A","isCorrect":true},{"label":"48 A","isCorrect":false}],"rationale":"V=IR => I=V/R=3A"}]}',
     '{"questions":[{"prompt":"Si V=12V y R=4Ω, ¿cuál es la corriente?","type":"MC","points":5,"options":[{"label":"3 A","isCorrect":true},{"label":"48 A","isCorrect":false}],"rationale":"V=IR => I=V/R=3A"}]}',
     'COMPLETED', NULL, NULL, 'anthropic/claude-3.5-sonnet', 245, 378, 1680,
     NOW() - INTERVAL '4 days', NOW() - INTERVAL '4 days'),

    (gen_random_uuid(), gen_random_uuid(), v_tenant_id, v_user_id, 'QUIZ_QUESTION_SUGGEST',
     'Genera 5 preguntas sobre normas ISO en dibujo técnico',
     '{"questions":[{"prompt":"¿Qué norma ISO regula el dibujo técnico mecánico?","type":"MC","points":5,"options":[{"label":"ISO 128","isCorrect":true},{"label":"ISO 9001","isCorrect":false}],"rationale":"ISO 128 es la norma de dibujo técnico"}]}',
     '{"questions":[{"prompt":"¿Qué norma ISO regula el dibujo técnico mecánico?","type":"MC","points":5,"options":[{"label":"ISO 128","isCorrect":true},{"label":"ISO 9001","isCorrect":false}],"rationale":"ISO 128 es la norma de dibujo técnico"}]}',
     'COMPLETED', NULL, NULL, 'anthropic/claude-3.5-sonnet', 198, 312, 1420,
     NOW() - INTERVAL '6 days', NOW() - INTERVAL '6 days'),

    (gen_random_uuid(), gen_random_uuid(), v_tenant_id, v_user_id, 'QUIZ_QUESTION_SUGGEST',
     'Genera 3 preguntas sobre funciones VLOOKUP en Excel',
     '{"questions":[{"prompt":"¿Qué hace la función VLOOKUP?","type":"MC","points":5,"options":[{"label":"Busca un valor y devuelve otro de la misma fila","isCorrect":true},{"label":"Suma un rango de celdas","isCorrect":false}],"rationale":"VLOOKUP es búsqueda vertical"}]}',
     '{"questions":[{"prompt":"¿Qué hace la función VLOOKUP?","type":"MC","points":5,"options":[{"label":"Busca un valor y devuelve otro de la misma fila","isCorrect":true},{"label":"Suma un rango de celdas","isCorrect":false}],"rationale":"VLOOKUP es búsqueda vertical"}]}',
     'COMPLETED', NULL, NULL, 'anthropic/claude-3.5-sonnet', 267, 401, 1980,
     NOW() - INTERVAL '8 days', NOW() - INTERVAL '8 days'),

    (gen_random_uuid(), gen_random_uuid(), v_tenant_id, v_user_id, 'QUIZ_QUESTION_SUGGEST',
     'Genera 10 preguntas avanzadas sobre programación orientada a objetos en Java',
     '{"choices":[{"finish_reason":"length","index":0,"message":{"role":"assistant","content":""}}]}',
     NULL,
     'FAILED', 'AI_PARSE_ERROR', 'LLM returned no choices (truncated by max_tokens)',
     'anthropic/claude-3.5-sonnet', 542, 0, 5200,
     NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day'),

    (gen_random_uuid(), gen_random_uuid(), v_tenant_id, v_user_id, 'QUIZ_QUESTION_SUGGEST',
     'Genera 4 preguntas sobre trigonometría aplicadas a dibujo técnico',
     '{"questions":[{"prompt":"¿Cuál es la relación entre sen(θ) y cos(90°-θ)?","type":"MC","points":5,"options":[{"label":"Son iguales (identidad trigonométrica fundamental)","isCorrect":true},{"label":"Son opuestos","isCorrect":false}],"rationale":"sen(θ) = cos(90°-θ)"}]}',
     '{"questions":[{"prompt":"¿Cuál es la relación entre sen(θ) y cos(90°-θ)?","type":"MC","points":5,"options":[{"label":"Son iguales (identidad trigonométrica fundamental)","isCorrect":true},{"label":"Son opuestos","isCorrect":false}],"rationale":"sen(θ) = cos(90°-θ)"}]}',
     'COMPLETED', NULL, NULL, 'anthropic/claude-3.5-sonnet', 287, 423, 1750,
     NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days');

    INSERT INTO edushift.tenant_ai_usage (
        id, tenant_id, usage_day, request_count, success_count, failed_count,
        tokens_in_total, tokens_out_total, created_at, updated_at
    )
    SELECT
        gen_random_uuid(), v_tenant_id,
        (CURRENT_DATE - d)::date,
        sub.succ + sub.fail,
        sub.succ,
        sub.fail,
        2000 + (sub.r1 * 4000)::bigint,
        1000 + (sub.r2 * 3000)::bigint,
        (CURRENT_DATE - d)::timestamptz,
        (CURRENT_DATE - d)::timestamptz
    FROM generate_series(0, 13) AS d,
    LATERAL (
        SELECT
            CASE WHEN d = 0 THEN 5 ELSE (3 + (random() * 6)::int) END AS succ,
            CASE WHEN d = 0 THEN 1 ELSE (random() * 2)::int END    AS fail,
            random() AS r1,
            random() AS r2
    ) sub;

    v_count := 1;
    RAISE NOTICE 'V39 ai_generations + tenant_ai_usage seeded for tecnosur';
END;
$$;


-- ----------------------------------------------------------------------------
-- 14. SUMMARY (tecnosur only)
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id uuid;
    v_users     int;
    v_students  int;
    v_teachers  int;
    v_sections  int;
    v_courses   int;
    v_grades    int;
    v_enrolls   int;
    v_quizzes   int;
    v_attend    int;
    v_ai        int;
    v_usage     int;
BEGIN
    SELECT id INTO v_tenant_id FROM edushift.tenants WHERE slug = 'tecnosur';
    IF v_tenant_id IS NULL THEN RETURN; END IF;

    SELECT count(*) INTO v_users    FROM edushift.users       WHERE tenant_id = v_tenant_id AND deleted = false;
    SELECT count(*) INTO v_students FROM edushift.students    WHERE tenant_id = v_tenant_id AND deleted = false;
    SELECT count(*) INTO v_teachers FROM edushift.teachers    WHERE tenant_id = v_tenant_id AND deleted = false;
    SELECT count(*) INTO v_sections FROM edushift.sections    WHERE tenant_id = v_tenant_id AND deleted = false;
    SELECT count(*) INTO v_courses  FROM edushift.courses     WHERE tenant_id = v_tenant_id AND deleted = false;
    SELECT count(*) INTO v_grades   FROM edushift.grades      WHERE tenant_id = v_tenant_id AND deleted = false;
    SELECT count(*) INTO v_enrolls  FROM edushift.student_enrollments WHERE tenant_id = v_tenant_id AND deleted = false;
    SELECT count(*) INTO v_quizzes  FROM edushift.lms_quizzes WHERE tenant_id = v_tenant_id AND deleted = false;
    SELECT count(*) INTO v_attend   FROM edushift.attendance_sessions WHERE tenant_id = v_tenant_id AND deleted = false;
    SELECT count(*) INTO v_ai       FROM edushift.ai_generations WHERE tenant_id = v_tenant_id;
    SELECT count(*) INTO v_usage    FROM edushift.tenant_ai_usage WHERE tenant_id = v_tenant_id;

    RAISE NOTICE '========================================';
    RAISE NOTICE 'V39 SEED COMPLETE — tecnosur row counts:';
    RAISE NOTICE '  users:          %', v_users;
    RAISE NOTICE '  students:       %', v_students;
    RAISE NOTICE '  teachers:       %', v_teachers;
    RAISE NOTICE '  sections:       %', v_sections;
    RAISE NOTICE '  courses:        %', v_courses;
    RAISE NOTICE '  grades:         %', v_grades;
    RAISE NOTICE '  enrollments:    %', v_enrolls;
    RAISE NOTICE '  lms_quizzes:    %', v_quizzes;
    RAISE NOTICE '  attendance:     % sessions', v_attend;
    RAISE NOTICE '  ai_generations: %', v_ai;
    RAISE NOTICE '  ai_usage days:  %', v_usage;
    RAISE NOTICE '========================================';
END;
$$;
