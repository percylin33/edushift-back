-- =============================================================================
-- V117__seed_payment_overdue_reminder_templates.sql
-- =============================================================================

DO $$
DECLARE
    t_id uuid;
BEGIN
    FOR t_id IN
        SELECT id FROM edushift.tenants
        WHERE deleted = false
          AND slug IS DISTINCT FROM 'edushift-system'
    LOOP
        INSERT INTO edushift.notification_templates (
            id, tenant_id, public_uuid, template_key, locale,
            subject, body_html, version, is_system,
            created_at, updated_at, created_by, updated_by, deleted, deleted_at
        ) VALUES
            (gen_random_uuid(), t_id, gen_random_uuid(),
             'PAYMENT_OVERDUE', 'es-PE',
             'Cuota vencida — {{invoiceNumber}}',
             '<h2>Cuota vencida</h2><p>Estimado/a <b>{{parentName}}</b>,</p><p>La cuota <b>{{invoiceNumber}}</b> de <b>{{studentName}}</b> está vencida. Monto: <b>{{amount}}</b>.</p><p>Regulariza el pago desde Pagos en el portal.</p>',
             1, true, now(), now(), NULL, NULL, false, NULL),
            (gen_random_uuid(), t_id, gen_random_uuid(),
             'PAYMENT_REMINDER', 'es-PE',
             'Recordatorio de pago — {{invoiceNumber}}',
             '<h2>Recordatorio de pago</h2><p>Estimado/a <b>{{parentName}}</b>,</p><p>La cuota <b>{{invoiceNumber}}</b> vence el <b>{{dueDate}}</b>. Monto: <b>{{amount}}</b>.</p>',
             1, true, now(), now(), NULL, NULL, false, NULL)
        ON CONFLICT (tenant_id, template_key, locale)
        DO UPDATE SET
            subject    = EXCLUDED.subject,
            body_html  = EXCLUDED.body_html,
            version    = edushift.notification_templates.version + 1,
            updated_at = now();
    END LOOP;
END$$;
