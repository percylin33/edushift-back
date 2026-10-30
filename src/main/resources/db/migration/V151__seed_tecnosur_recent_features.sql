-- =============================================================================
-- V151__seed_tecnosur_recent_features.sql
-- Additional fictional QA fixtures for features added after V93.
-- Development/demo data only. Never seed production databases.
-- =============================================================================

DO $$
DECLARE
    v_tenant_id uuid;
    v_year_id uuid;
    v_level_id uuid;
    v_template_id uuid;
    v_grade_register_id uuid;
    v_session_id uuid;
    v_ai_document_id uuid;
    v_file_public_uuid uuid;
    v_student_uuid uuid;
    v_guardian_uuid uuid;
    v_invoice_id uuid;
BEGIN
    IF current_database() IN ('edushift_prod', 'edushift_production') THEN
        RAISE NOTICE 'V151 seed skipped on production database %', current_database();
        RETURN;
    END IF;

    SELECT id INTO v_tenant_id
    FROM edushift.tenants
    WHERE slug = 'tecnosur' AND deleted = false
    LIMIT 1;

    IF v_tenant_id IS NULL THEN
        RAISE NOTICE 'V151 seed skipped: tecnosur tenant not found';
        RETURN;
    END IF;

    SELECT id INTO v_year_id
    FROM edushift.academic_years
    WHERE tenant_id = v_tenant_id AND deleted = false
    ORDER BY (status = 'ACTIVE') DESC, start_date DESC
    LIMIT 1;

    SELECT id INTO v_level_id
    FROM edushift.academic_levels
    WHERE tenant_id = v_tenant_id AND deleted = false
    ORDER BY ordinal
    LIMIT 1;

    -- Day schedule / recess configuration.
    IF v_year_id IS NOT NULL AND v_level_id IS NOT NULL THEN
        SELECT id INTO v_template_id
        FROM edushift.day_schedule_templates
        WHERE tenant_id = v_tenant_id
          AND academic_year_id = v_year_id
          AND academic_level_id = v_level_id
          AND grade_id IS NULL
          AND shift = 'MORNING'
          AND deleted = false
        LIMIT 1;

        IF v_template_id IS NULL THEN
            INSERT INTO edushift.day_schedule_templates (
                id, tenant_id, public_uuid, created_at, updated_at,
                deleted, academic_year_id, academic_level_id, name, shift,
                recess_share_group
            ) VALUES (
                '15100000-0000-4000-8000-000000000001', v_tenant_id,
                '15100000-0000-4000-8000-000000000002',
                '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z',
                false, v_year_id, v_level_id,
                'Horario demo mañana 2026', 'MORNING', 'SECUNDARIA-MORNING'
            )
            RETURNING id INTO v_template_id;
        END IF;

        INSERT INTO edushift.day_schedule_blocks (
            id, tenant_id, public_uuid, created_at, updated_at, deleted,
            template_id, day_of_week, start_time, end_time, block_type, label
        )
        SELECT x.id, v_tenant_id, x.public_uuid, '2026-09-01T12:00:00Z',
               '2026-09-01T12:00:00Z', false, v_template_id, x.day_of_week,
               x.start_time, x.end_time, x.block_type, x.label
        FROM (VALUES
            ('15100000-0000-4000-8000-000000000003'::uuid,
             '15100000-0000-4000-8000-000000000013'::uuid, 1::smallint,
             '10:00'::time, '10:20'::time, 'RECESS'::varchar, 'Recreo mañana'::varchar),
            ('15100000-0000-4000-8000-000000000004'::uuid,
             '15100000-0000-4000-8000-000000000014'::uuid, 2::smallint,
             '10:00'::time, '10:20'::time, 'RECESS'::varchar, 'Recreo mañana'::varchar),
            ('15100000-0000-4000-8000-000000000005'::uuid,
             '15100000-0000-4000-8000-000000000015'::uuid, 3::smallint,
             '10:00'::time, '10:20'::time, 'RECESS'::varchar, 'Recreo mañana'::varchar),
            ('15100000-0000-4000-8000-000000000006'::uuid,
             '15100000-0000-4000-8000-000000000016'::uuid, 4::smallint,
             '10:00'::time, '10:20'::time, 'RECESS'::varchar, 'Recreo mañana'::varchar),
            ('15100000-0000-4000-8000-000000000007'::uuid,
             '15100000-0000-4000-8000-000000000017'::uuid, 5::smallint,
             '10:00'::time, '10:20'::time, 'RECESS'::varchar, 'Recreo mañana'::varchar)
        ) AS x(id, public_uuid, day_of_week, start_time, end_time, block_type, label)
        WHERE NOT EXISTS (
            SELECT 1 FROM edushift.day_schedule_blocks b
            WHERE b.tenant_id = v_tenant_id AND b.template_id = v_template_id
              AND b.day_of_week = x.day_of_week AND b.block_type = x.block_type
              AND b.deleted = false
        );

        -- Formal grade register with categories for the new gradebook flow.
        SELECT id INTO v_grade_register_id
        FROM edushift.grade_register_templates
        WHERE tenant_id = v_tenant_id AND academic_year_id = v_year_id
          AND name = 'Registro de notas demo 2026'
          AND deleted = false
        LIMIT 1;

        IF v_grade_register_id IS NULL THEN
            INSERT INTO edushift.grade_register_templates (
                id, tenant_id, public_uuid, created_at, updated_at, deleted,
                academic_year_id, name, description, scope, is_system,
                is_default, catalog_code
            ) VALUES (
                '15100000-0000-4000-8000-000000000070', v_tenant_id,
                '15100000-0000-4000-8000-000000000071',
                '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z', false,
                v_year_id, 'Registro de notas demo 2026',
                'Plantilla ficticia para probar el registro de calificaciones.',
                'TENANT', false, false, 'QA-TEC-2026'
            )
            RETURNING id INTO v_grade_register_id;
        END IF;

        INSERT INTO edushift.grade_categories (
            id, tenant_id, public_uuid, created_at, updated_at, deleted,
            template_id, name, color_token, sort_order, weight,
            contributes_to_average, allows_teacher_columns
        )
        SELECT x.id, v_tenant_id, x.public_uuid,
               '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z', false,
               v_grade_register_id, x.name, x.color_token, x.sort_order,
               x.weight, true, true
        FROM (VALUES
            ('15100000-0000-4000-8000-000000000072'::uuid,
             '15100000-0000-4000-8000-000000000082'::uuid,
             'Prácticas demo'::varchar, 'blue'::varchar, 1::int, 30.00::numeric),
            ('15100000-0000-4000-8000-000000000073'::uuid,
             '15100000-0000-4000-8000-000000000083'::uuid,
             'Evaluaciones demo'::varchar, 'amber'::varchar, 2::int, 40.00::numeric),
            ('15100000-0000-4000-8000-000000000074'::uuid,
             '15100000-0000-4000-8000-000000000084'::uuid,
             'Proyecto demo'::varchar, 'green'::varchar, 3::int, 30.00::numeric)
        ) AS x(id, public_uuid, name, color_token, sort_order, weight)
        WHERE NOT EXISTS (
            SELECT 1 FROM edushift.grade_categories c
            WHERE c.tenant_id = v_tenant_id
              AND c.template_id = v_grade_register_id
              AND lower(c.name) = lower(x.name)
              AND c.deleted = false
        );
    END IF;

    -- Printable learning sheet linked to an existing V93 session.
    SELECT id INTO v_session_id
    FROM edushift.learning_sessions
    WHERE tenant_id = v_tenant_id AND deleted = false
    ORDER BY created_at
    LIMIT 1;

    IF v_session_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM edushift.learning_session_application_sheets
        WHERE tenant_id = v_tenant_id AND learning_session_id = v_session_id
          AND kind = 'LEARNING' AND grade = '1ro Secundaria'
          AND deleted = false
    ) THEN
        INSERT INTO edushift.learning_session_application_sheets (
            id, tenant_id, public_uuid, created_at, updated_at, deleted,
            learning_session_id, kind, grade, sheet_number, title, content
        ) VALUES (
            '15100000-0000-4000-8000-000000000075', v_tenant_id,
            '15100000-0000-4000-8000-000000000076',
            '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z', false,
            v_session_id, 'LEARNING', '1ro Secundaria', 1,
            'Ficha demo: pensamiento computacional',
            '{"warmup":"Ordena los pasos de una receta sencilla.","purpose":"Reconocer una secuencia de instrucciones.","activities":[{"title":"Descompón el problema","items":[{"type":"LINE","label":"Escribe los pasos"},{"type":"CHECKBOX","label":"Revisé el orden"}]}]}'::jsonb
        );
    END IF;

    -- Tenant RAG source with synthetic extracted text. The referenced file is
    -- one of V93's local-only placeholder file objects; no external storage is
    -- contacted by this seed.
    SELECT public_uuid INTO v_file_public_uuid
    FROM edushift.lms_file_objects
    WHERE tenant_id = v_tenant_id AND deleted = false
    ORDER BY created_at, id
    LIMIT 1;

    IF v_file_public_uuid IS NOT NULL THEN
        SELECT id INTO v_ai_document_id
        FROM edushift.ai_source_documents
        WHERE tenant_id = v_tenant_id AND file_public_uuid = v_file_public_uuid
          AND deleted = false
        LIMIT 1;

        IF v_ai_document_id IS NULL THEN
            INSERT INTO edushift.ai_source_documents (
                id, tenant_id, public_uuid, file_public_uuid, title, kind,
                status, audiences, source_url, source_version, effective_at,
                review_after, checksum_sha256, text_bytes, created_at, updated_at,
                deleted
            ) VALUES (
                '15100000-0000-4000-8000-000000000090', v_tenant_id,
                '15100000-0000-4000-8000-000000000091', v_file_public_uuid,
                'Guía ficticia de convivencia escolar', 'INSTITUTIONAL',
                'PUBLISHED', '["TENANT_ADMIN","TEACHER","PARENT"]'::jsonb,
                'https://example.test/tecnosur/convivencia', 'qa-2026-01',
                DATE '2026-03-01', DATE '2027-03-01', repeat('a', 64),
                310, '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z', false
            )
            RETURNING id INTO v_ai_document_id;

            INSERT INTO edushift.ai_source_chunks (
                id, tenant_id, document_id, ordinal, page_number, content,
                created_at, updated_at, deleted
            ) VALUES
                ('15100000-0000-4000-8000-000000000092', v_tenant_id,
                 v_ai_document_id, 0, 1,
                 'Fixture ficticio de Tecnosur: los estudiantes deben mantener un trato respetuoso, cuidar los materiales y solicitar apoyo al docente cuando lo necesiten.',
                 '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z', false),
                ('15100000-0000-4000-8000-000000000093', v_tenant_id,
                 v_ai_document_id, 1, 2,
                 'Fixture ficticio de Tecnosur: las familias pueden comunicarse con el colegio por los canales institucionales durante el horario de atención publicado.',
                 '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z', false);
        END IF;
    END IF;

    -- Fictional payment method: no real bank account or wallet details.
    INSERT INTO edushift.tenant_payment_methods (
        id, tenant_id, public_uuid, method_type, display_name, bank_name,
        account_holder, account_number, cci, is_active, is_default,
        created_at, updated_at
    )
    SELECT '15100000-0000-4000-8000-000000000020', v_tenant_id,
           '15100000-0000-4000-8000-000000000021', 'BANK_TRANSFER',
           'Cuenta de prueba (datos ficticios)', 'Banco Demo',
           'Colegio Técnico Tecnosur', '000-000000-DEMO',
           '00000000000000000000', true, true,
           '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z'
    WHERE NOT EXISTS (
        SELECT 1 FROM edushift.tenant_payment_methods m
        WHERE m.tenant_id = v_tenant_id
          AND m.display_name = 'Cuenta de prueba (datos ficticios)'
          AND m.deleted_at IS NULL
    );

    -- One synthetic guardian consent and two fictional claims book cases.
    SELECT s.public_uuid, g.user_id
      INTO v_student_uuid, v_guardian_uuid
    FROM edushift.student_guardians sg
    JOIN edushift.guardians g
      ON g.id = sg.guardian_id AND g.tenant_id = v_tenant_id AND g.deleted = false
    JOIN edushift.students s
      ON s.id = sg.student_id AND s.tenant_id = v_tenant_id AND s.deleted = false
    JOIN edushift.users u
      ON u.public_uuid = g.user_id AND u.tenant_id = v_tenant_id
         AND u.deleted = false AND u.roles @> ARRAY['PARENT']::varchar[]
    WHERE sg.tenant_id = v_tenant_id AND sg.deleted = false
      AND g.user_id IS NOT NULL
    ORDER BY sg.is_primary_contact DESC, sg.created_at
    LIMIT 1;

    IF v_student_uuid IS NOT NULL AND v_guardian_uuid IS NOT NULL THEN
        INSERT INTO edushift.privacy_consents (
            id, tenant_id, public_uuid, guardian_user_id, student_id,
            policy_version, consent_granted, granted_at, ip_address,
            user_agent, created_at, updated_at, deleted
        )
        SELECT '15100000-0000-4000-8000-000000000030', v_tenant_id,
               '15100000-0000-4000-8000-000000000031', v_guardian_uuid,
               v_student_uuid, 'v1.0', true, '2026-09-01T12:00:00Z',
               '192.0.2.10', 'EduShift QA fixture',
               '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z', false
        WHERE NOT EXISTS (
            SELECT 1 FROM edushift.privacy_consents c
            WHERE c.tenant_id = v_tenant_id
              AND c.guardian_user_id = v_guardian_uuid
              AND c.student_id = v_student_uuid
              AND c.policy_version = 'v1.0' AND c.deleted = false
        );

        INSERT INTO edushift.claims_book_entries (
            id, tenant_id, claim_code, consumer_name, consumer_doc_type,
            consumer_doc_number, consumer_email, consumer_phone,
            consumer_address, is_minor, parent_guardian_name,
            contracted_service, claim_amount, claim_type, detail_description,
            consumer_request, status, due_date, created_at, updated_at, deleted
        )
        SELECT '15100000-0000-4000-8000-000000000040', v_tenant_id,
               'DEMO-TEC-2026-001', 'Apoderado de prueba', 'DNI', '00000000',
               'familia.demo@example.test', '+51 900 000 000',
               'Dirección ficticia de prueba', true, 'Apoderado de prueba',
               'Servicio educativo — fixture QA', 35.00, 'RECLAMO',
               'Registro ficticio para validar el flujo del libro de reclamaciones.',
               'Solicita revisión de un cobro de prueba.', 'PENDING',
               DATE '2026-10-30', '2026-09-10T12:00:00Z',
               '2026-09-10T12:00:00Z', false
        WHERE NOT EXISTS (
            SELECT 1 FROM edushift.claims_book_entries
            WHERE claim_code = 'DEMO-TEC-2026-001'
        );

        INSERT INTO edushift.claims_book_entries (
            id, tenant_id, claim_code, consumer_name, consumer_doc_type,
            consumer_doc_number, consumer_email, consumer_phone,
            consumer_address, is_minor, parent_guardian_name,
            contracted_service, claim_amount, claim_type, detail_description,
            consumer_request, status, due_date, response_text, responded_at,
            created_at, updated_at, deleted
        )
        SELECT '15100000-0000-4000-8000-000000000041', v_tenant_id,
               'DEMO-TEC-2026-002', 'Apoderado de prueba', 'DNI', '00000001',
               'familia.demo@example.test', '+51 900 000 000',
               'Dirección ficticia de prueba', true, 'Apoderado de prueba',
               'Servicio educativo — fixture QA', NULL, 'QUEJA',
               'Registro ficticio atendido para probar historial y respuesta.',
               'Solicita información sobre el horario escolar.', 'ATTENDED',
               DATE '2026-09-30',
               'Respuesta de ejemplo: consulta recibida y atendida por el colegio.',
               '2026-09-18T12:00:00Z', '2026-09-10T12:00:00Z',
               '2026-09-18T12:00:00Z', false
        WHERE NOT EXISTS (
            SELECT 1 FROM edushift.claims_book_entries
            WHERE claim_code = 'DEMO-TEC-2026-002'
        );

        -- A paid invoice and its matching manual payment for the family portal.
        SELECT id INTO v_invoice_id
        FROM edushift.invoices
        WHERE tenant_id = v_tenant_id
          AND idempotency_key = 'seed:tecnosur:2026-08:paid'
          AND deleted_at IS NULL;

        IF v_invoice_id IS NULL THEN
            INSERT INTO edushift.invoices (
                id, tenant_id, public_uuid, student_id, guardian_user_id,
                idempotency_key, period_label, currency, subtotal_cents,
                discount_cents, tax_cents, total_cents, status, issued_at,
                due_at, paid_at, notes, created_at, updated_at
            ) VALUES (
                '15100000-0000-4000-8000-000000000050', v_tenant_id,
                '15100000-0000-4000-8000-000000000051', v_student_uuid,
                v_guardian_uuid, 'seed:tecnosur:2026-08:paid', '2026-08',
                'PEN', 45000, 0, 0, 45000, 'PAID',
                '2026-08-01T12:00:00Z', '2026-08-10T12:00:00Z',
                '2026-08-05T12:00:00Z', 'Cuota ficticia pagada para QA',
                '2026-08-01T12:00:00Z', '2026-08-05T12:00:00Z'
            ) RETURNING id INTO v_invoice_id;

            INSERT INTO edushift.invoice_items (
                id, tenant_id, invoice_id, description, quantity,
                unit_amount_cents, line_total_cents, created_at
            ) VALUES (
                '15100000-0000-4000-8000-000000000052', v_tenant_id,
                v_invoice_id, 'Pensión de agosto (demo)', 1, 45000, 45000,
                '2026-08-01T12:00:00Z'
            );

            INSERT INTO edushift.payments (
                id, tenant_id, public_uuid, invoice_id, guardian_user_id,
                provider, external_reference, status, amount_cents, currency,
                payment_method, paid_at, raw_response, created_at, updated_at
            ) VALUES (
                '15100000-0000-4000-8000-000000000053', v_tenant_id,
                '15100000-0000-4000-8000-000000000054', v_invoice_id,
                v_guardian_uuid, 'MANUAL', 'QA-DEMO-TEC-2026-08',
                'APPROVED', 45000, 'PEN', 'BANK_TRANSFER',
                '2026-08-05T12:00:00Z',
                '{"fixture":true,"reference":"QA-DEMO-TEC-2026-08"}'::jsonb,
                '2026-08-05T12:00:00Z', '2026-08-05T12:00:00Z'
            );
        END IF;

        -- A second invoice stays unpaid so the family portal can exercise
        -- both the pending and settled states after every tenant refresh.
        IF NOT EXISTS (
            SELECT 1 FROM edushift.invoices
            WHERE tenant_id = v_tenant_id
              AND idempotency_key = 'seed:tecnosur:2026-10:pending'
              AND deleted_at IS NULL
        ) THEN
            INSERT INTO edushift.invoices (
                id, tenant_id, public_uuid, student_id, guardian_user_id,
                idempotency_key, period_label, currency, subtotal_cents,
                discount_cents, tax_cents, total_cents, status, issued_at,
                due_at, evidence_file_public_uuid, evidence_submitted_at,
                evidence_notes, notes, created_at, updated_at
            ) VALUES (
                '15100000-0000-4000-8000-000000000055', v_tenant_id,
                '15100000-0000-4000-8000-000000000056', v_student_uuid,
                v_guardian_uuid, 'seed:tecnosur:2026-10:pending-review', '2026-10',
                'PEN', 45000, 0, 0, 45000,
                CASE WHEN v_file_public_uuid IS NULL THEN 'PENDING' ELSE 'PENDING_REVIEW' END,
                '2026-10-01T12:00:00Z', '2026-10-15T12:00:00Z',
                v_file_public_uuid, '2026-10-02T12:00:00Z',
                'Voucher ficticio QA; no corresponde a una transacción real.',
                'Cuota ficticia con comprobante por revisar',
                '2026-10-01T12:00:00Z', '2026-10-01T12:00:00Z'
            ) RETURNING id INTO v_invoice_id;

            INSERT INTO edushift.invoice_items (
                id, tenant_id, invoice_id, description, quantity,
                unit_amount_cents, line_total_cents, created_at
            ) VALUES (
                '15100000-0000-4000-8000-000000000057', v_tenant_id,
                v_invoice_id, 'Pensión de octubre (demo)', 1, 45000, 45000,
                '2026-10-01T12:00:00Z'
            );
        END IF;

        -- Recreate V118's original unpaid family-portal invoice as well.
        IF NOT EXISTS (
            SELECT 1 FROM edushift.invoices
            WHERE tenant_id = v_tenant_id
              AND idempotency_key = 'emit:' || v_student_uuid::text || ':DEMO:2026-09'
              AND deleted_at IS NULL
        ) THEN
            INSERT INTO edushift.invoices (
                id, tenant_id, public_uuid, student_id, guardian_user_id,
                idempotency_key, period_label, currency, subtotal_cents,
                discount_cents, tax_cents, total_cents, status, issued_at,
                due_at, notes, created_at, updated_at
            ) VALUES (
                '15100000-0000-4000-8000-000000000058', v_tenant_id,
                '15100000-0000-4000-8000-000000000059', v_student_uuid,
                v_guardian_uuid,
                'emit:' || v_student_uuid::text || ':DEMO:2026-09',
                '2026-09', 'PEN', 35000, 0, 0, 35000, 'PENDING',
                '2026-09-01T12:00:00Z', '2026-09-16T12:00:00Z',
                'Cuota demo UAT ficticia pendiente',
                '2026-09-01T12:00:00Z', '2026-09-01T12:00:00Z'
            ) RETURNING id INTO v_invoice_id;

            INSERT INTO edushift.invoice_items (
                id, tenant_id, invoice_id, description, quantity,
                unit_amount_cents, line_total_cents, created_at
            ) VALUES (
                '15100000-0000-4000-8000-00000000005a', v_tenant_id,
                v_invoice_id, 'Cuota demo septiembre', 1, 35000, 35000,
                '2026-09-01T12:00:00Z'
            );
        END IF;
    ELSE
        RAISE NOTICE 'V151: guardian/student fixture pair missing; privacy, claims and payment samples skipped';
    END IF;

    RAISE NOTICE 'V151: recent-feature QA fixtures applied for tecnosur';
END;
$$;
