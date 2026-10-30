-- =============================================================================
-- V118__seed_demo_invoices_tecnosur.sql
--
-- 1) Retarget invoices/subscriptions student FKs to students.public_uuid
--    (app contract: invoices.student_id stores public UUID, not internal id).
-- 2) Seed one PENDING demo invoice for Tecnosur UAT voucher flow.
-- =============================================================================

-- Composite unique key required for FK (tenant_id, public_uuid)
CREATE UNIQUE INDEX IF NOT EXISTS uq_students_tenant_public_uuid
    ON edushift.students (tenant_id, public_uuid);

ALTER TABLE edushift.invoices
    DROP CONSTRAINT IF EXISTS fk_invoices_student;

ALTER TABLE edushift.subscriptions
    DROP CONSTRAINT IF EXISTS fk_subscriptions_student;

ALTER TABLE edushift.invoices
    ADD CONSTRAINT fk_invoices_student
        FOREIGN KEY (tenant_id, student_id)
        REFERENCES edushift.students (tenant_id, public_uuid)
        ON DELETE RESTRICT;

ALTER TABLE edushift.subscriptions
    ADD CONSTRAINT fk_subscriptions_student
        FOREIGN KEY (tenant_id, student_id)
        REFERENCES edushift.students (tenant_id, public_uuid)
        ON DELETE RESTRICT;

DO $$
DECLARE
    t_id uuid;
    guardian_uuid uuid;
    student_uuid uuid;
BEGIN
    SELECT id INTO t_id
    FROM edushift.tenants
    WHERE slug = 'tecnosur' AND deleted = false
    LIMIT 1;

    IF t_id IS NULL THEN
        RAISE NOTICE 'V118: tenant tecnosur not found — skip seed';
        RETURN;
    END IF;

    SELECT g.user_id, s.public_uuid
      INTO guardian_uuid, student_uuid
    FROM edushift.student_guardians sg
    JOIN edushift.guardians g ON g.id = sg.guardian_id AND g.deleted = false
    JOIN edushift.students s ON s.id = sg.student_id AND s.deleted = false
    JOIN edushift.users u ON u.public_uuid = g.user_id AND u.deleted = false
    WHERE sg.tenant_id = t_id
      AND sg.deleted = false
      AND g.user_id IS NOT NULL
      AND u.roles @> ARRAY['PARENT']::varchar[]
    ORDER BY sg.is_primary_contact DESC, sg.created_at ASC
    LIMIT 1;

    IF guardian_uuid IS NULL OR student_uuid IS NULL THEN
        RAISE NOTICE 'V118: no parent/student pair in tecnosur — skip seed';
        RETURN;
    END IF;

    IF EXISTS (
        SELECT 1 FROM edushift.invoices
        WHERE tenant_id = t_id
          AND idempotency_key = 'emit:' || student_uuid::text || ':DEMO:2026-09'
          AND deleted = false
    ) THEN
        RAISE NOTICE 'V118: demo invoice already present — skip seed';
        RETURN;
    END IF;

    INSERT INTO edushift.invoices (
        id, tenant_id, public_uuid,
        student_id, guardian_user_id,
        idempotency_key, period_label, currency,
        subtotal_cents, discount_cents, tax_cents, total_cents,
        status, issued_at, due_at, notes,
        mora_cents, mora_exempt,
        deleted, created_at, updated_at
    ) VALUES (
        gen_random_uuid(), t_id, gen_random_uuid(),
        student_uuid, guardian_uuid,
        'emit:' || student_uuid::text || ':DEMO:2026-09',
        '2026-09', 'PEN',
        35000, 0, 0, 35000,
        'PENDING', now(), now() + interval '15 days',
        'Cuota demo UAT (V118)',
        0, false,
        false, now(), now()
    );

    RAISE NOTICE 'V118: seeded demo invoice for student % guardian %', student_uuid, guardian_uuid;
END$$;
