-- =============================================================================
-- V106__seed_task_published_template.sql
--
-- Seed TASK_PUBLISHED notification template for every active tenant.
-- Fired by TaskServiceImpl.publish when a task goes DRAFT → PUBLISHED,
-- fan-out to students with an active enrollment in the task's section
-- (same pattern as QUIZ_PUBLISHED).
-- =============================================================================

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
            (gen_random_uuid(), t_id, gen_random_uuid(),
             'TASK_PUBLISHED', 'es-PE',
             'Nueva tarea disponible — {{taskTitle}}',
             '<h1 style="margin:0 0 16px;font-size:20px;color:#0f172a">Nueva tarea publicada</h1>'
             || '<p style="margin:0 0 12px;font-size:15px;line-height:1.55;color:#334155">Hola <strong>{{studentName}}</strong>,</p>'
             || '<p style="margin:0 0 12px;font-size:15px;line-height:1.55;color:#334155">Hay una nueva tarea en '
             || '<strong>{{courseName}}</strong>: <strong>{{taskTitle}}</strong>.</p>'
             || '<p style="margin:0;font-size:14px;color:#64748b">Fecha límite: <strong>{{dueDate}}</strong></p>',
             1, true, now(), now(), NULL, NULL, false, NULL)
        ON CONFLICT (tenant_id, template_key, locale)
        DO UPDATE SET
            subject    = EXCLUDED.subject,
            body_html  = EXCLUDED.body_html,
            version    = edushift.notification_templates.version + 1,
            updated_at = now();
    END LOOP;
END$$;
