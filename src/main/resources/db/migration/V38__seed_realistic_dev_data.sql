-- =============================================================================
-- V38__seed_realistic_dev_data.sql
-- Realistic development seed for the `demo` and `keola-networks` tenants.
--
-- ⚠️  DEVELOPMENT-ORIENTED SEED.
-- Idempotent: each block guards on existence; re-runs are safe. Skips
-- production-like databases (guarded by current_database()).
--
-- Scope (per tenant, repeated for `demo` AND `keola-networks`):
--   * Tenant AI settings with ai_enabled=true + quotas
--   * 1 active academic year 2026 with 4 bimestres + 1 ANUAL
--   * 3 academic levels (INICIAL / PRIMARIA / SECUNDARIA)
--   * 6 grades (3 primaria, 3 secundaria)
--   * 6 courses (MAT, COMU, CCNN, CCSS, ING, EF) linked to PRIM + SEC
--   * 6 sections (1°A, 1°B, 2°A, 2°B, 3°A, 5°A)
--   * 8 teachers (3 with user account, 5 without)
--   * 12 teacher_assignments
--   * 60 students (10 per section) + 60 active enrollments
--   * 1 parent/guardian per tenant linked to 5 students
--   * 2 published quizzes with MC + TF questions
--   * 5 attendance_sessions of the last 5 weekdays with records
--   * 3 ai_generations (mix COMPLETED + FAILED) + 7 days of usage
--
-- ⚠️  PASSWORD WARNING
-- The seeded users get the placeholder password_hash
--   'SEED_RESET_REQUIRED_v1'
-- which will REJECT any login attempt. The DevDataInitializer
-- (dev profile only) walks the users table on app startup and overwrites
-- any 'SEED_RESET_REQUIRED_v1' hash with a real BCrypt of
-- 'EduShift2026!' (override with dev.seed.password).
-- This is intentional: we cannot pre-compute a real BCrypt hash from
-- a SQL migration (no JVM runtime), and committing a hand-crafted hash
-- is a security risk.
-- Note: we deliberately avoid the prefix '$2a$10$' here because
-- PostgreSQL's dollar-quoting parser would truncate the literal at the
-- first $/tag/ pair inside a '...'-delimited string.
--
-- Multi-tenant FK notes (confusing!):
--   * `tenants.id`              — internal UUIDv7 PK
--   * `tenants.public_uuid`     — external UUIDv4 surfaced to clients
--   * Most tables' `tenant_id`  — references `tenants.id`
--   * `tenant_ai_settings.tenant_id` — references `tenants.public_uuid` ⚠️
--   * `lms_file_objects.tenant_id`   — references `tenants.id`
--   * `refresh_tokens.tenant_id`     — references `tenants.id`
-- This seed uses `tenants.id` everywhere except `tenant_ai_settings`.
-- =============================================================================

-- ----------------------------------------------------------------------------
-- GUARD: skip on production databases
-- ----------------------------------------------------------------------------
DO $$
BEGIN
    IF current_database() IN ('edushift_prod', 'edushift_production') THEN
        RAISE NOTICE 'V38 seed skipped on production database %', current_database();
        RETURN;
    END IF;
END;
$$;


-- ----------------------------------------------------------------------------
-- 1. USERS — admin / staff / teachers / parent per tenant
-- Password hash is a sentinel that REJECTS login. Use
--   POST /dev/seed-reset-passwords
-- after this migration to apply the real password.
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_demo_id     uuid;
    v_keola_id    uuid;
    v_now         timestamptz := NOW() AT TIME ZONE 'UTC';
    v_pwd_hash    varchar(255) := 'SEED_RESET_REQUIRED_v1';
    v_count       int := 0;
BEGIN
    SELECT id INTO v_demo_id   FROM edushift.tenants WHERE slug = 'demo';
    SELECT id INTO v_keola_id  FROM edushift.tenants WHERE slug = 'keola-networks';

    IF v_demo_id IS NULL OR v_keola_id IS NULL THEN
        RAISE NOTICE 'V38 user seed skipped: demo or keola tenant missing';
        RETURN;
    END IF;

    -- DEMO tenant users ---------------------------------------------------------
    INSERT INTO edushift.users (id, tenant_id, public_uuid, created_at, updated_at,
                                created_by, updated_by, deleted, deleted_at,
                                first_name, last_name, email, password_hash,
                                phone, status, email_verified, mfa_enabled)
    SELECT gen_random_uuid(), v_demo_id, gen_random_uuid(), v_now, v_now,
           NULL, NULL, false, NULL,
           u.first_name, u.last_name, u.email, v_pwd_hash,
           u.phone, 'ACTIVE', true, false
    FROM (VALUES
        -- admin (preserved: skip if admin@demo already exists)
        ('Admin',           'Demo',          'admin@demo.edushift.pe',  '+51999000001'),
        -- staff
        ('Coordinador',     'Académico',     'coordinador@demo.edushift.pe', '+51999000002'),
        ('Secretaria',      'Pérez',         'secretaria@demo.edushift.pe',  '+51999000003'),
        -- 3 teachers WITH user account
        ('María',           'García López',  'maria.garcia@demo.edushift.pe',  '+51999000010'),
        ('Juan',            'Rodríguez Vega', 'juan.rodriguez@demo.edushift.pe', '+51999000011'),
        ('Ana',             'Torres Salas',   'ana.torres@demo.edushift.pe',   '+51999000012'),
        -- 1 parent/guardian
        ('Carlos',          'Mendoza Rivera', 'padre.demo@demo.edushift.pe',  '+51999000020')
    ) AS u(first_name, last_name, email, phone)
    WHERE NOT EXISTS (
        SELECT 1 FROM edushift.users WHERE email = u.email AND deleted = false
    );

    -- KEOLA tenant users --------------------------------------------------------
    INSERT INTO edushift.users (id, tenant_id, public_uuid, created_at, updated_at,
                                created_by, updated_by, deleted, deleted_at,
                                first_name, last_name, email, password_hash,
                                phone, status, email_verified, mfa_enabled)
    SELECT gen_random_uuid(), v_keola_id, gen_random_uuid(), v_now, v_now,
           NULL, NULL, false, NULL,
           u.first_name, u.last_name, u.email, v_pwd_hash,
           u.phone, 'ACTIVE', true, false
    FROM (VALUES
        ('Patricia',        'Valderrama',     'admin@keola-networks.edushift.pe', '+51988000001'),
        ('Roberto',         'Salinas Quispe', 'coordinador@keola-networks.edushift.pe', '+51988000002'),
        ('Lucía',           'Huamán Castro',  'secretaria@keola-networks.edushift.pe',  '+51988000003'),
        ('Pedro',           'Castillo Núñez',  'pedro.castillo@keola-networks.edushift.pe', '+51988000010'),
        ('Sofía',           'Ramírez Ponce',   'sofia.ramirez@keola-networks.edushift.pe', '+51988000011'),
        ('Diego',           'Vargas Morales',  'diego.vargas@keola-networks.edushift.pe',  '+51988000012'),
        ('Lucía',           'Paredes Soto',    'padre.keola@keola-networks.edushift.pe',   '+51988000020')
    ) AS u(first_name, last_name, email, phone)
    WHERE NOT EXISTS (
        SELECT 1 FROM edushift.users WHERE email = u.email AND deleted = false
    );

    -- Assign roles (idempotent: only if cardinality is 0)
    UPDATE edushift.users SET roles = ARRAY['TENANT_ADMIN']::varchar[]
    WHERE email IN ('admin@demo.edushift.pe', 'admin@keola-networks.edushift.pe')
      AND cardinality(roles) = 0;

    UPDATE edushift.users SET roles = ARRAY['STAFF']::varchar[]
    WHERE email IN (
        'coordinador@demo.edushift.pe', 'secretaria@demo.edushift.pe',
        'coordinador@keola-networks.edushift.pe', 'secretaria@keola-networks.edushift.pe'
    ) AND cardinality(roles) = 0;

    UPDATE edushift.users SET roles = ARRAY['TEACHER']::varchar[]
    WHERE email IN (
        'maria.garcia@demo.edushift.pe', 'juan.rodriguez@demo.edushift.pe', 'ana.torres@demo.edushift.pe',
        'pedro.castillo@keola-networks.edushift.pe', 'sofia.ramirez@keola-networks.edushift.pe',
        'diego.vargas@keola-networks.edushift.pe'
    ) AND cardinality(roles) = 0;

    UPDATE edushift.users SET roles = ARRAY['PARENT']::varchar[]
    WHERE email IN ('padre.demo@demo.edushift.pe', 'padre.keola@keola-networks.edushift.pe')
      AND cardinality(roles) = 0;

    RAISE NOTICE 'V38 users seeded';
END;
$$;


-- ----------------------------------------------------------------------------
-- 2. TENANT AI SETTINGS — enable AI for both dev tenants.
-- ⚠️  This table's tenant_id references tenants(public_uuid), not tenants(id).
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_public  uuid;
    v_count          int := 0;
BEGIN
    FOR v_tenant_public IN
        SELECT public_uuid FROM edushift.tenants
        WHERE slug IN ('demo', 'keola-networks')
    LOOP
        INSERT INTO edushift.tenant_ai_settings (
            id, public_uuid, tenant_id, ai_enabled,
            daily_request_quota, monthly_token_quota, default_model,
            created_at, updated_at, created_by, updated_by
        ) VALUES (
            gen_random_uuid(), gen_random_uuid(), v_tenant_public, true,
            100, 1000000, 'anthropic/claude-3.5-sonnet',
            NOW(), NOW(), NULL, NULL
        )
        ON CONFLICT (tenant_id) DO UPDATE
        SET ai_enabled = EXCLUDED.ai_enabled,
            daily_request_quota = EXCLUDED.daily_request_quota,
            monthly_token_quota = EXCLUDED.monthly_token_quota,
            default_model = EXCLUDED.default_model,
            updated_at = NOW();

        v_count := v_count + 1;
    END LOOP;

    RAISE NOTICE 'V38 tenant_ai_settings seeded/updated for % tenants', v_count;
END;
$$;


-- ----------------------------------------------------------------------------
-- 3. ACADEMIC YEAR + PERIODS — 1 ACTIVE year 2026 with 4 bimestres + 1 ANUAL
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id  uuid;
    v_year_id    uuid;
    v_count      int := 0;
BEGIN
    FOR v_tenant_id IN
        SELECT id FROM edushift.tenants
        WHERE slug IN ('demo', 'keola-networks')
    LOOP
        SELECT id INTO v_year_id
        FROM edushift.academic_years
        WHERE tenant_id = v_tenant_id AND status = 'ACTIVE' AND deleted = false
        LIMIT 1;

        IF v_year_id IS NULL THEN
            v_year_id := gen_random_uuid();
            INSERT INTO edushift.academic_years (
                id, tenant_id, public_uuid, created_at, updated_at,
                created_by, updated_by, deleted, deleted_at,
                name, status, start_date, end_date
            ) VALUES (
                v_year_id, v_tenant_id, gen_random_uuid(), NOW(), NOW(),
                NULL, NULL, false, NULL,
                '2026', 'ACTIVE', '2026-03-01', '2026-12-15'
            );
        END IF;

        -- 4 bimestres (idempotent)
        IF NOT EXISTS (
            SELECT 1 FROM edushift.academic_periods
            WHERE academic_year_id = v_year_id
              AND period_type = 'BIMESTRE' AND deleted = false
        ) THEN
            INSERT INTO edushift.academic_periods (
                id, tenant_id, public_uuid, created_at, updated_at,
                created_by, updated_by, deleted, deleted_at,
                academic_year_id, period_type, ordinal, name,
                start_date, end_date
            ) VALUES
                (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(),
                 NULL, NULL, false, NULL,
                 v_year_id, 'BIMESTRE', 1, 'I Bimestre', '2026-03-01', '2026-05-15'),
                (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(),
                 NULL, NULL, false, NULL,
                 v_year_id, 'BIMESTRE', 2, 'II Bimestre', '2026-05-16', '2026-08-01'),
                (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(),
                 NULL, NULL, false, NULL,
                 v_year_id, 'BIMESTRE', 3, 'III Bimestre', '2026-08-02', '2026-10-15'),
                (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(),
                 NULL, NULL, false, NULL,
                 v_year_id, 'BIMESTRE', 4, 'IV Bimestre', '2026-10-16', '2026-12-15');
        END IF;

        -- 1 ANUAL period
        IF NOT EXISTS (
            SELECT 1 FROM edushift.academic_periods
            WHERE academic_year_id = v_year_id
              AND period_type = 'ANUAL' AND deleted = false
        ) THEN
            INSERT INTO edushift.academic_periods (
                id, tenant_id, public_uuid, created_at, updated_at,
                created_by, updated_by, deleted, deleted_at,
                academic_year_id, period_type, ordinal, name,
                start_date, end_date
            ) VALUES (
                gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(),
                NULL, NULL, false, NULL,
                v_year_id, 'ANUAL', 1, 'Anual 2026', '2026-03-01', '2026-12-15'
            );
        END IF;

        v_count := v_count + 1;
    END LOOP;

    RAISE NOTICE 'V38 academic years + periods seeded for % tenants', v_count;
END;
$$;


-- ----------------------------------------------------------------------------
-- 4. ACADEMIC LEVELS + GRADES — 3 levels + 6 grades per tenant
-- (3 PRIMARIA: 1°, 2°, 3° + 3 SECUNDARIA: 1°, 2°, 5°)
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id  uuid;
    v_count      int := 0;
BEGIN
    FOR v_tenant_id IN
        SELECT id FROM edushift.tenants
        WHERE slug IN ('demo', 'keola-networks')
    LOOP
        -- Levels (idempotent via partial unique idx on (tenant_id, lower(code)))
        INSERT INTO edushift.academic_levels (
            id, tenant_id, public_uuid, created_at, updated_at,
            created_by, updated_by, deleted, deleted_at,
            code, name, ordinal
        ) VALUES
            (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(),
             NULL, NULL, false, NULL,
             'INICIAL',    'Inicial',     1),
            (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(),
             NULL, NULL, false, NULL,
             'PRIMARIA',   'Primaria',    2),
            (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(),
             NULL, NULL, false, NULL,
             'SECUNDARIA', 'Secundaria',  3)
        ON CONFLICT DO NOTHING;

        -- Grades: 3 primaria + 3 secundaria (V14 has no code/short_name cols
        -- — the actual shape is id, level_id, name, ordinal only).
        INSERT INTO edushift.grades (
            id, tenant_id, public_uuid, created_at, updated_at,
            created_by, updated_by, deleted, deleted_at,
            level_id, name, ordinal
        )
        SELECT
            gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(),
            NULL, NULL, false, NULL,
            (SELECT id FROM edushift.academic_levels
             WHERE tenant_id = v_tenant_id AND code = g.level_code AND deleted = false),
            g.name, g.ordinal
        FROM (VALUES
            ('PRIMARIA',   '1ro Primaria',  1),
            ('PRIMARIA',   '2do Primaria',  2),
            ('PRIMARIA',   '3ro Primaria',  3),
            ('SECUNDARIA', '1ro Secundaria', 1),
            ('SECUNDARIA', '2do Secundaria', 2),
            ('SECUNDARIA', '5to Secundaria', 5)
        ) AS g(level_code, name, ordinal)
        ON CONFLICT DO NOTHING;

        v_count := v_count + 1;
    END LOOP;

    RAISE NOTICE 'V38 academic levels + grades seeded for % tenants', v_count;
END;
$$;


-- ----------------------------------------------------------------------------
-- 5. COURSES + COURSE_LEVELS — 6 courses (MAT, COMU, CCNN, CCSS, ING, EF)
--    linked to PRIMARIA and SECUNDARIA levels
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id  uuid;
    v_count      int := 0;
BEGIN
    FOR v_tenant_id IN
        SELECT id FROM edushift.tenants
        WHERE slug IN ('demo', 'keola-networks')
    LOOP
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
            ('MAT',  'Matemática',          'Razonamiento lógico y numérico', 4,  5),
            ('COMU', 'Comunicación',        'Lengua materna: lectura y escritura', 4,  5),
            ('CCNN', 'Ciencia y Ambiente',  'Ciencias naturales', 3,  3),
            ('CCSS', 'Personal Social',     'Historia, geografía y cívica', 3,  3),
            ('ING',  'Inglés',              'Idioma extranjero', 2,  3),
            ('EF',   'Educación Física',    'Actividad física y deportes', 2,  2)
        ) AS c(code, name, description, credits, hours_per_week)
        ON CONFLICT DO NOTHING;

        -- Link each course to PRIMARIA + SECUNDARIA
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
          AND lv.code IN ('PRIMARIA', 'SECUNDARIA')
          AND lv.deleted = false
        ON CONFLICT DO NOTHING;

        v_count := v_count + 1;
    END LOOP;

    RAISE NOTICE 'V38 courses + course_levels seeded for % tenants', v_count;
END;
$$;


-- ----------------------------------------------------------------------------
-- 6. SECTIONS — 6 sections per tenant (1°A, 1°B, 2°A, 2°B, 3°A, 5°A)
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id  uuid;
    v_year_id    uuid;
    v_count      int := 0;
BEGIN
    FOR v_tenant_id IN
        SELECT id FROM edushift.tenants
        WHERE slug IN ('demo', 'keola-networks')
    LOOP
        SELECT id INTO v_year_id
        FROM edushift.academic_years
        WHERE tenant_id = v_tenant_id AND status = 'ACTIVE' AND deleted = false
        LIMIT 1;

        IF v_year_id IS NULL THEN
            CONTINUE;
        END IF;

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
            ('PRIMARIA',   1, 'A', 1),
            ('PRIMARIA',   1, 'B', 2),
            ('PRIMARIA',   2, 'A', 3),
            ('PRIMARIA',   2, 'B', 4),
            ('PRIMARIA',   3, 'A', 5),
            ('SECUNDARIA', 5, 'A', 6)
        ) AS s(level_code, grade_ordinal, name, display_order)
        JOIN edushift.grades g
          ON g.tenant_id = v_tenant_id
         AND g.ordinal = s.grade_ordinal
         AND g.deleted = false
        JOIN edushift.academic_levels lv
          ON lv.id = g.level_id
         AND lv.tenant_id = v_tenant_id
         AND lv.code = s.level_code
         AND lv.deleted = false
        ON CONFLICT DO NOTHING;

        v_count := v_count + 1;
    END LOOP;

    RAISE NOTICE 'V38 sections seeded for % tenants', v_count;
END;
$$;


-- ----------------------------------------------------------------------------
-- 7. TEACHERS — 8 per tenant (3 linked to TEACHER users, 5 unlinked)
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id  uuid;
    v_count      int := 0;
BEGIN
    FOR v_tenant_id IN
        SELECT id FROM edushift.tenants
        WHERE slug IN ('demo', 'keola-networks')
    LOOP
        -- First, link any existing teachers to TEACHER users by email
        UPDATE edushift.teachers t
        SET    user_id = u.id
        FROM   edushift.users u
        WHERE  u.tenant_id = t.tenant_id
          AND  u.deleted = false
          AND  'TEACHER' = ANY(u.roles)
          AND  t.user_id IS NULL
          AND  t.deleted = false
          AND  t.email = u.email;

        -- Then insert 8 teachers (3 with user, 5 without) per tenant
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
            -- 3 with user account (email matches a TEACHER user)
            ('DNI', '45678901', 'María',  'García',  'López',    '1985-03-15', 'FEMALE', 'maria.garcia@demo.edushift.pe',  '+51999000010', 'Lic.', '["Matemática","Física"]',           '2018-03-01', 'ACTIVE'),
            ('DNI', '45678902', 'Juan',   'Rodríguez','Vega',    '1980-07-22', 'MALE',   'juan.rodriguez@demo.edushift.pe','+51999000011', 'Mg.',  '["Comunicación","Literatura"]',     '2015-03-01', 'ACTIVE'),
            ('DNI', '45678903', 'Ana',    'Torres',  'Salas',    '1990-11-30', 'FEMALE', 'ana.torres@demo.edushift.pe',   '+51999000012', 'Lic.', '["Ciencias Naturales","Biología"]', '2020-03-01', 'ACTIVE'),
            -- 5 without user account (no email match)
            ('DNI', '45678904', 'Luis',   'Pérez',   'Mendoza',  '1978-05-12', 'MALE',   NULL, NULL, 'Lic.', '["Historia","Geografía"]',          '2010-03-01', 'ACTIVE'),
            ('DNI', '45678905', 'Carmen', 'Vega',    'Ruiz',     '1982-09-08', 'FEMALE', NULL, NULL, 'Mg.',  '["Inglés","Literatura"]',           '2012-03-01', 'ACTIVE'),
            ('DNI', '45678906', 'Jorge',  'Salazar', 'Quispe',   '1988-01-25', 'MALE',   NULL, NULL, 'Lic.', '["Educación Física","Deportes"]',   '2017-03-01', 'ACTIVE'),
            ('DNI', '45678907', 'Rosa',   'Huamán',  'Castro',   '1992-04-18', 'FEMALE', NULL, NULL, 'Lic.', '["Matemática","Computación"]',      '2019-03-01', 'ACTIVE'),
            ('DNI', '45678908', 'Miguel', 'Cordero', 'Vela',     '1986-12-03', 'MALE',   NULL, NULL, 'Lic.', '["Arte","Música"]',                 '2014-03-01', 'ON_LEAVE')
        ) AS t(document_type, document_number, first_name, last_name, second_last_name, birth_date, gender, email, phone, title, specializations, hire_date, employment_status)
        WHERE NOT EXISTS (
            SELECT 1 FROM edushift.teachers th
            WHERE th.tenant_id = v_tenant_id
              AND th.document_number = t.document_number
              AND th.deleted = false
        );

        v_count := v_count + 1;
    END LOOP;

    RAISE NOTICE 'V38 teachers seeded for % tenants', v_count;
END;
$$;


-- ----------------------------------------------------------------------------
-- 8. TEACHER ASSIGNMENTS — 12 per tenant (2 courses × 6 sections)
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id  uuid;
    v_period_id  uuid;
    v_count      int := 0;
BEGIN
    FOR v_tenant_id IN
        SELECT id FROM edushift.tenants
        WHERE slug IN ('demo', 'keola-networks')
    LOOP
        SELECT id INTO v_period_id
        FROM edushift.academic_periods
        WHERE tenant_id = v_tenant_id AND period_type = 'ANUAL' AND deleted = false
        LIMIT 1;

        IF v_period_id IS NULL THEN
            CONTINUE;
        END IF;

        IF EXISTS (SELECT 1 FROM edushift.teacher_assignments
                   WHERE tenant_id = v_tenant_id AND deleted = false) THEN
            CONTINUE;
        END IF;

        -- For each section, pick 2 teachers (first 2 by created_at) and assign
        -- a course based on the section's level: MAT/COMU for primaria, CCNN/CCSS for secundaria.
        INSERT INTO edushift.teacher_assignments (
            id, tenant_id, public_uuid, created_at, updated_at,
            created_by, updated_by, deleted, deleted_at,
            teacher_id, section_id, course_id, academic_period_id,
            assigned_at, unassigned_at, notes
        )
        SELECT
            gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(),
            NULL, NULL, false, NULL,
            t.teacher_id, s.id, c.course_id, v_period_id,
            NOW(), NULL, NULL
        FROM edushift.sections s
        JOIN edushift.grades g
          ON g.id = s.grade_id AND g.deleted = false
        JOIN edushift.academic_levels lv
          ON lv.id = g.level_id AND lv.deleted = false
        CROSS JOIN LATERAL (
            SELECT id AS teacher_id,
                   ROW_NUMBER() OVER (ORDER BY created_at) AS rn
            FROM edushift.teachers
            WHERE tenant_id = v_tenant_id AND deleted = false
            ORDER BY created_at
            LIMIT 2
        ) t
        CROSS JOIN LATERAL (
            SELECT id AS course_id
            FROM edushift.courses
            WHERE tenant_id = v_tenant_id
              AND code = CASE
                  WHEN lv.code = 'PRIMARIA'   AND t.rn = 1 THEN 'MAT'
                  WHEN lv.code = 'PRIMARIA'   AND t.rn = 2 THEN 'COMU'
                  WHEN lv.code = 'SECUNDARIA' AND t.rn = 1 THEN 'CCNN'
                  WHEN lv.code = 'SECUNDARIA' AND t.rn = 2 THEN 'CCSS'
                  ELSE NULL
              END
              AND deleted = false
        ) c
        WHERE s.tenant_id = v_tenant_id
          AND s.deleted = false;

        v_count := v_count + 1;
    END LOOP;

    RAISE NOTICE 'V38 teacher_assignments seeded for % tenants', v_count;
END;
$$;


-- ----------------------------------------------------------------------------
-- 9. STUDENTS + ENROLLMENTS — 60 students per tenant (10 per section)
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
        'Renata','Ciro','Amanda','Dante'
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
    FOR v_tenant_id IN
        SELECT id FROM edushift.tenants
        WHERE slug IN ('demo', 'keola-networks')
    LOOP
        IF EXISTS (SELECT 1 FROM edushift.students
                   WHERE tenant_id = v_tenant_id AND deleted = false) THEN
            CONTINUE;
        END IF;

        SELECT id INTO v_year_id
        FROM edushift.academic_years
        WHERE tenant_id = v_tenant_id AND status = 'ACTIVE' AND deleted = false
        LIMIT 1;

        IF v_year_id IS NULL THEN
            CONTINUE;
        END IF;

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
                    -- Deterministic per (section_id, student_index) to avoid
                    -- duplicate DNI per tenant under uk_students_tenant_document_active.
                    lpad((70000000 + ((abs(hashtext(v_section_id::text)) + i * 37) % 9999999))::text, 8, '0'),
                    v_first_names[1 + (i % 60)],
                    v_last_names[1 + (i % 54)],
                    NULL,
                    (CURRENT_DATE - (INTERVAL '8 years' + (random() * INTERVAL '4 years')))::date,
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
        END LOOP;

        v_count := v_count + 1;
    END LOOP;

    RAISE NOTICE 'V38 students + enrollments seeded for % tenants', v_count;
END;
$$;


-- ----------------------------------------------------------------------------
-- 10. GUARDIANS — 1 parent per tenant linked to 5 students
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id  uuid;
    v_user_id    uuid;
    v_count      int := 0;
BEGIN
    FOR v_tenant_id IN
        SELECT id FROM edushift.tenants
        WHERE slug IN ('demo', 'keola-networks')
    LOOP
        IF EXISTS (SELECT 1 FROM edushift.guardians
                   WHERE tenant_id = v_tenant_id AND deleted = false) THEN
            CONTINUE;
        END IF;

        SELECT id INTO v_user_id
        FROM edushift.users
        WHERE tenant_id = v_tenant_id
          AND 'PARENT' = ANY(roles)
          AND deleted = false
        LIMIT 1;

        IF v_user_id IS NULL THEN
            CONTINUE;
        END IF;

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
            'DNI', '12345678',
            u.phone, u.email, 'Independiente'
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
            s.id, g.id, 'FATHER', true, true
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

        v_count := v_count + 1;
    END LOOP;

    RAISE NOTICE 'V38 guardians + student_guardians seeded for % tenants', v_count;
END;
$$;


-- ----------------------------------------------------------------------------
-- 11. LMS QUIZZES — 2 published quizzes per tenant with MC + TF questions
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
    FOR v_tenant_id IN
        SELECT id FROM edushift.tenants
        WHERE slug IN ('demo', 'keola-networks')
    LOOP
        IF EXISTS (SELECT 1 FROM edushift.lms_quizzes
                   WHERE tenant_id = v_tenant_id AND deleted = false) THEN
            CONTINUE;
        END IF;

        SELECT public_uuid INTO v_owner_id
        FROM edushift.users
        WHERE tenant_id = v_tenant_id
          AND 'TEACHER' = ANY(roles)
          AND deleted = false
        LIMIT 1;

        IF v_owner_id IS NULL THEN CONTINUE; END IF;

        SELECT id INTO v_section_id
        FROM edushift.sections
        WHERE tenant_id = v_tenant_id AND deleted = false
        ORDER BY display_order LIMIT 1;

        IF v_section_id IS NULL THEN CONTINUE; END IF;

        -- Quiz 1: "Matemática — Fracciones"
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
            'Quiz: Fracciones — 5to grado',
            'Evaluación de comprensión sobre suma y resta de fracciones homogéneas y heterogéneas.',
            'PUBLISHED',
            20, NOW() + INTERVAL '14 days', 30, 2,
            NOW(), NULL, v_owner_id
        );

        -- Q1 (MC): ¿Cuál es el resultado de 1/2 + 1/4?
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
            '¿Cuál es el resultado de 1/2 + 1/4?',
            5, NULL, NULL
        );
        INSERT INTO edushift.lms_quiz_options (
            id, tenant_id, public_uuid, created_at, updated_at,
            created_by, updated_by, deleted, deleted_at,
            question_id, position, label, is_correct
        ) VALUES
            (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_q1_id, 1, '1/4', false),
            (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_q1_id, 2, '3/4', true),
            (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_q1_id, 3, '2/6', false),
            (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_q1_id, 4, '1/6', false);

        -- Q2 (TF): Toda fracción mayor que 1 se llama impropia
        INSERT INTO edushift.lms_quiz_questions (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, quiz_id, position, question_type, prompt, points, expected_keywords, correct_boolean)
        VALUES (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_quiz_id, 2, 'TF', 'Toda fracción mayor que 1 se llama impropia', 5, NULL, true);

        -- Q3 (TF): En una fracción propia el numerador es mayor que el denominador
        INSERT INTO edushift.lms_quiz_questions (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, quiz_id, position, question_type, prompt, points, expected_keywords, correct_boolean)
        VALUES (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_quiz_id, 3, 'TF', 'En una fracción propia el numerador es mayor que el denominador', 5, NULL, false);

        -- Quiz 2: "Comunicación — Textos narrativos"
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
            'Quiz: Textos narrativos',
            'Identifica los elementos de un texto narrativo: narrador, personajes, espacio, tiempo.',
            'PUBLISHED',
            20, NOW() + INTERVAL '7 days', 20, 3,
            NOW(), NULL, v_owner_id
        );

        -- Q1 (MC)
        v_q1_id := gen_random_uuid();
        INSERT INTO edushift.lms_quiz_questions (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, quiz_id, position, question_type, prompt, points, expected_keywords, correct_boolean)
        VALUES (v_q1_id, v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_quiz_id, 1, 'MC', '¿Qué elemento de la narración se encarga de contar la historia?', 5, NULL, NULL);
        INSERT INTO edushift.lms_quiz_options (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, question_id, position, label, is_correct)
        VALUES
            (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_q1_id, 1, 'El personaje principal', false),
            (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_q1_id, 2, 'El narrador',           true),
            (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_q1_id, 3, 'El espacio',            false),
            (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_q1_id, 4, 'El tiempo',             false);

        -- Q2 (MC)
        v_q1_id := gen_random_uuid();
        INSERT INTO edushift.lms_quiz_questions (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, quiz_id, position, question_type, prompt, points, expected_keywords, correct_boolean)
        VALUES (v_q1_id, v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_quiz_id, 2, 'MC', '¿Cómo se llama la etapa de la narración donde se presenta el conflicto?', 5, NULL, NULL);
        INSERT INTO edushift.lms_quiz_options (id, tenant_id, public_uuid, created_at, updated_at, created_by, updated_by, deleted, deleted_at, question_id, position, label, is_correct)
        VALUES
            (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_q1_id, 1, 'Planteamiento',   false),
            (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_q1_id, 2, 'Nudo',            true),
            (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_q1_id, 3, 'Desenlace',       false),
            (gen_random_uuid(), v_tenant_id, gen_random_uuid(), NOW(), NOW(), NULL, NULL, false, NULL, v_q1_id, 4, 'Introducción',    false);

        v_count := v_count + 1;
    END LOOP;

    RAISE NOTICE 'V38 lms_quizzes + questions + options seeded for % tenants', v_count;
END;
$$;


-- ----------------------------------------------------------------------------
-- 12. ATTENDANCE — 5 sessions of the last 5 weekdays with PRESENT/LATE/ABSENT
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
    FOR v_tenant_id IN
        SELECT id FROM edushift.tenants
        WHERE slug IN ('demo', 'keola-networks')
    LOOP
        IF EXISTS (SELECT 1 FROM edushift.attendance_sessions
                   WHERE tenant_id = v_tenant_id AND deleted = false) THEN
            CONTINUE;
        END IF;

        SELECT public_uuid INTO v_owner_id
        FROM edushift.users
        WHERE tenant_id = v_tenant_id AND 'TEACHER' = ANY(roles) AND deleted = false
        LIMIT 1;

        IF v_owner_id IS NULL THEN CONTINUE; END IF;

        SELECT id INTO v_section_id
        FROM edushift.sections
        WHERE tenant_id = v_tenant_id AND deleted = false
        ORDER BY display_order LIMIT 1;

        IF v_section_id IS NULL THEN CONTINUE; END IF;

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
                -- scanned_by_user_id is required when status=PRESENT or LATE
                -- (chk_attendance_records_scanned_by_present). Use the teacher
                -- who closed the session.
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

        v_count := v_count + 1;
    END LOOP;

    RAISE NOTICE 'V38 attendance_sessions + records seeded for % tenants', v_count;
END;
$$;


-- ----------------------------------------------------------------------------
-- 13. AI GENERATIONS + USAGE — 3 generations + 7 days of usage per tenant
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenant_id   uuid;
    v_user_id     uuid;
    v_count       int := 0;
BEGIN
    FOR v_tenant_id IN
        SELECT id FROM edushift.tenants
        WHERE slug IN ('demo', 'keola-networks')
    LOOP
        IF EXISTS (SELECT 1 FROM edushift.ai_generations
                   WHERE tenant_id = v_tenant_id) THEN
            CONTINUE;
        END IF;

        SELECT id INTO v_user_id
        FROM edushift.users
        WHERE tenant_id = v_tenant_id AND 'TEACHER' = ANY(roles) AND deleted = false
        LIMIT 1;

        INSERT INTO edushift.ai_generations (
            id, public_uuid, tenant_id, request_user_id, feature,
            prompt_text, response_text, response_parsed, status, error_code, error_message,
            model_used, prompt_tokens, response_tokens, latency_ms,
            created_at, updated_at
        ) VALUES
        (gen_random_uuid(), gen_random_uuid(), v_tenant_id, v_user_id, 'QUIZ_QUESTION_SUGGEST',
         'Genera 3 preguntas de opción múltiple sobre "suma de fracciones" para 5to de primaria',
         '{"questions":[{"prompt":"¿Cuál es el resultado de 1/2 + 1/4?","type":"MC","points":5,"options":[{"label":"3/4","isCorrect":true},{"label":"1/4","isCorrect":false}],"rationale":"Evalúa suma de fracciones homogéneas"}]}',
         '{"questions":[{"prompt":"¿Cuál es el resultado de 1/2 + 1/4?","type":"MC","points":5,"options":[{"label":"3/4","isCorrect":true},{"label":"1/4","isCorrect":false}],"rationale":"Evalúa suma de fracciones homogéneas"}]}',
         'COMPLETED', NULL, NULL, 'anthropic/claude-3.5-sonnet', 234, 412, 1820,
         NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'),

        (gen_random_uuid(), gen_random_uuid(), v_tenant_id, v_user_id, 'QUIZ_QUESTION_SUGGEST',
         'Genera 2 preguntas de verdadero/falso sobre "el sistema solar" para 4to de primaria',
         '{"questions":[{"prompt":"La Tierra es el tercer planeta del sistema solar","type":"TF","points":3,"options":[{"label":"Verdadero","isCorrect":true},{"label":"Falso","isCorrect":false}],"rationale":"Verificación directa del orden planetario"}]}',
         '{"questions":[{"prompt":"La Tierra es el tercer planeta del sistema solar","type":"TF","points":3,"options":[{"label":"Verdadero","isCorrect":true},{"label":"Falso","isCorrect":false}],"rationale":"Verificación directa del orden planetario"}]}',
         'COMPLETED', NULL, NULL, 'anthropic/claude-3.5-sonnet', 189, 287, 1340,
         NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days'),

        (gen_random_uuid(), gen_random_uuid(), v_tenant_id, v_user_id, 'QUIZ_QUESTION_SUGGEST',
         'Genera 5 preguntas de respuesta corta sobre "la Revolución Francesa"',
         '{"choices":[{"finish_reason":"length","index":0,"message":{"role":"assistant","content":""}}]}',
         NULL,
         'FAILED', 'AI_PARSE_ERROR', 'LLM returned no choices (truncated by max_tokens)',
         'anthropic/claude-3.5-sonnet', 312, 0, 4500,
         NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day');

        INSERT INTO edushift.tenant_ai_usage (
            id, tenant_id, usage_day, request_count, success_count, failed_count,
            tokens_in_total, tokens_out_total, created_at, updated_at
        )
        SELECT
            gen_random_uuid(), v_tenant_id,
            (CURRENT_DATE - d)::date,
            -- request_count = success_count + failed_count (the check requires
            -- success + failed <= request). We materialize a single random
            -- per row so the math is consistent.
            sub.succ + sub.fail,
            sub.succ,
            sub.fail,
            1000 + (sub.r1 * 3000)::bigint,
            500 + (sub.r2 * 2000)::bigint,
            (CURRENT_DATE - d)::timestamptz,
            (CURRENT_DATE - d)::timestamptz
        FROM generate_series(0, 6) AS d,
        LATERAL (
            SELECT
                CASE WHEN d = 0 THEN 4 ELSE (2 + (random() * 5)::int) END AS succ,
                CASE WHEN d = 0 THEN 1 ELSE (random() * 2)::int END    AS fail,
                random() AS r1,
                random() AS r2
        ) sub;

        v_count := v_count + 1;
    END LOOP;

    RAISE NOTICE 'V38 ai_generations + tenant_ai_usage seeded for % tenants', v_count;
END;
$$;


-- ----------------------------------------------------------------------------
-- 14. SUMMARY
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_tenants int;
    v_users   int;
    v_students int;
    v_teachers int;
    v_sections int;
    v_courses  int;
    v_grades   int;
    v_enrolls  int;
    v_quizzes  int;
    v_attend   int;
    v_ai       int;
    v_usage    int;
BEGIN
    SELECT count(*) INTO v_tenants  FROM edushift.tenants WHERE deleted = false;
    SELECT count(*) INTO v_users    FROM edushift.users WHERE deleted = false;
    SELECT count(*) INTO v_students FROM edushift.students WHERE deleted = false;
    SELECT count(*) INTO v_teachers FROM edushift.teachers WHERE deleted = false;
    SELECT count(*) INTO v_sections FROM edushift.sections WHERE deleted = false;
    SELECT count(*) INTO v_courses  FROM edushift.courses WHERE deleted = false;
    SELECT count(*) INTO v_grades   FROM edushift.grades WHERE deleted = false;
    SELECT count(*) INTO v_enrolls  FROM edushift.student_enrollments WHERE deleted = false;
    SELECT count(*) INTO v_quizzes  FROM edushift.lms_quizzes WHERE deleted = false;
    SELECT count(*) INTO v_attend   FROM edushift.attendance_sessions WHERE deleted = false;
    SELECT count(*) INTO v_ai       FROM edushift.ai_generations;
    SELECT count(*) INTO v_usage    FROM edushift.tenant_ai_usage;

    RAISE NOTICE '========================================';
    RAISE NOTICE 'V38 SEED COMPLETE — current row counts:';
    RAISE NOTICE '  tenants:        %', v_tenants;
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
