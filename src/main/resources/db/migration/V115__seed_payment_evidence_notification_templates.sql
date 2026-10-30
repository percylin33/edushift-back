-- =============================================================================
-- V115__seed_payment_evidence_notification_templates.sql
-- Templates for voucher submit / reject / payment received (manual recon).
-- Mirrors NotificationTemplateSeed.java keys. Upserts per active tenant.
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
             'PAYMENT_EVIDENCE_SUBMITTED', 'es-PE',
             'Voucher recibido — {{invoiceNumber}}',
             '<h2>Voucher de pago por revisar</h2><p>Se envió un comprobante para la cuota <b>{{invoiceNumber}}</b> de <b>{{studentName}}</b>.</p><p>Monto: <b>{{amount}}</b></p><p>Nota: {{notes}}</p>',
             1, true, now(), now(), NULL, NULL, false, NULL),
            (gen_random_uuid(), t_id, gen_random_uuid(),
             'PAYMENT_EVIDENCE_REJECTED', 'es-PE',
             'Voucher rechazado — {{invoiceNumber}}',
             '<h2>Voucher rechazado</h2><p>Estimado/a <b>{{parentName}}</b>,</p><p>El comprobante de la cuota <b>{{invoiceNumber}}</b> fue rechazado.</p><p>Motivo: {{reason}}</p>',
             1, true, now(), now(), NULL, NULL, false, NULL),
            (gen_random_uuid(), t_id, gen_random_uuid(),
             'PAYMENT_RECEIVED', 'es-PE',
             'Pago registrado — {{invoiceNumber}}',
             '<h2>Pago registrado</h2><p>Estimado/a <b>{{parentName}}</b>,</p><p>Confirmamos el pago de la cuota <b>{{invoiceNumber}}</b> por <b>{{amount}}</b> ({{studentName}}).</p>',
             1, true, now(), now(), NULL, NULL, false, NULL)
        ON CONFLICT (tenant_id, template_key, locale)
        DO UPDATE SET
            subject    = EXCLUDED.subject,
            body_html  = EXCLUDED.body_html,
            version    = edushift.notification_templates.version + 1,
            updated_at = now();
    END LOOP;
END$$;
