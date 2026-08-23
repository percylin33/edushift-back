-- =============================================================================
-- V102__email_template_layout_v2.sql
--
-- Professional body HTML for transactional notification templates.
-- The responsive layout wrapper (logo + primary color) is applied at send
-- time by EmailLayoutRenderer — bodies here remain content fragments.
--
-- ANNOUNCEMENT stays in-app only (no SMTP fan-out on Gmail); body is still
-- refreshed for the notification bell.
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
        UPDATE edushift.notification_templates SET
            subject = 'Bienvenido a {{tenantName}}',
            body_html = '<h1 style="margin:0 0 16px;font-size:22px;color:#0f172a">¡Bienvenido a {{tenantName}}!</h1><p style="margin:0 0 12px;font-size:15px;line-height:1.55;color:#334155">Tu cuenta EduShift está activa. Accede con tu correo <strong>{{userEmail}}</strong>.</p><p style="margin:0;font-size:14px;color:#64748b">Si tienes dudas, contacta al administrador de tu colegio.</p>',
            version = version + 1,
            updated_at = now()
        WHERE tenant_id = t_id AND template_key = 'WELCOME_TENANT' AND locale = 'es-PE' AND deleted = false;

        UPDATE edushift.notification_templates SET
            subject = 'Ausencia registrada — {{studentName}}',
            body_html = '<h1 style="margin:0 0 16px;font-size:20px;color:#0f172a">Ausencia registrada</h1><p style="margin:0 0 12px;font-size:15px;line-height:1.55;color:#334155">Estimado/a {{parentName}},</p><p style="margin:0 0 12px;font-size:15px;line-height:1.55;color:#334155">Le informamos que <strong>{{studentName}}</strong> no asistió a la sesión de <strong>{{courseName}}</strong> el día <strong>{{date}}</strong>.</p><p style="margin:0 0 12px;font-size:15px;color:#334155">Motivo registrado: {{reason}}</p><p style="margin:0;font-size:14px;color:#64748b">Si requiere justificación, responda desde el portal o contacte a la institución.</p>',
            version = version + 1,
            updated_at = now()
        WHERE tenant_id = t_id AND template_key = 'STUDENT_ABSENT' AND locale = 'es-PE' AND deleted = false;

        UPDATE edushift.notification_templates SET
            subject = 'Nueva calificación publicada — {{evaluationTitle}}',
            body_html = '<h1 style="margin:0 0 16px;font-size:20px;color:#0f172a">Calificación publicada</h1><p style="margin:0 0 12px;font-size:15px;line-height:1.55;color:#334155">Hola <strong>{{studentName}}</strong>,</p><p style="margin:0 0 12px;font-size:15px;line-height:1.55;color:#334155">Tu docente ha publicado la calificación de <strong>{{evaluationTitle}}</strong> en <strong>{{courseName}}</strong>.</p><p style="margin:0;font-size:16px;color:#0f172a"><strong>Nota:</strong> {{grade}} / {{maxGrade}}</p>',
            version = version + 1,
            updated_at = now()
        WHERE tenant_id = t_id AND template_key = 'GRADE_PUBLISHED' AND locale = 'es-PE' AND deleted = false;

        UPDATE edushift.notification_templates SET
            subject = 'Retroalimentación de tu entrega en {{taskTitle}}',
            body_html = '<h1 style="margin:0 0 16px;font-size:20px;color:#0f172a">Retroalimentación disponible</h1><p style="margin:0 0 12px;font-size:15px;line-height:1.55;color:#334155">Hola <strong>{{studentName}}</strong>,</p><p style="margin:0;font-size:15px;line-height:1.55;color:#334155">El asistente IA ha generado una retroalimentación para tu entrega en <strong>{{taskTitle}}</strong>. Revísala en la plataforma.</p>',
            version = version + 1,
            updated_at = now()
        WHERE tenant_id = t_id AND template_key = 'AI_FEEDBACK_READY' AND locale = 'es-PE' AND deleted = false;

        UPDATE edushift.notification_templates SET
            subject = 'Tu tarea fue devuelta — {{taskTitle}}',
            body_html = '<h1 style="margin:0 0 16px;font-size:20px;color:#0f172a">Tarea devuelta</h1><p style="margin:0 0 12px;font-size:15px;line-height:1.55;color:#334155">Hola <strong>{{studentName}}</strong>,</p><p style="margin:0 0 12px;font-size:15px;line-height:1.55;color:#334155">Tu docente ha revisado tu entrega de <strong>{{taskTitle}}</strong>.</p><p style="margin:0 0 8px;font-size:16px;color:#0f172a"><strong>Nota:</strong> {{grade}} / {{maxGrade}}</p><p style="margin:0;font-size:14px;color:#64748b">Comentario: {{teacherComment}}</p>',
            version = version + 1,
            updated_at = now()
        WHERE tenant_id = t_id AND template_key = 'TASK_RETURNED' AND locale = 'es-PE' AND deleted = false;

        UPDATE edushift.notification_templates SET
            subject = 'Nuevo quiz disponible — {{quizTitle}}',
            body_html = '<h1 style="margin:0 0 16px;font-size:20px;color:#0f172a">Nuevo quiz publicado</h1><p style="margin:0 0 12px;font-size:15px;line-height:1.55;color:#334155">Hola <strong>{{studentName}}</strong>,</p><p style="margin:0 0 12px;font-size:15px;line-height:1.55;color:#334155">Hay un nuevo quiz en <strong>{{courseName}}</strong>: <strong>{{quizTitle}}</strong>.</p><p style="margin:0;font-size:14px;color:#64748b">Fecha límite: <strong>{{dueDate}}</strong></p>',
            version = version + 1,
            updated_at = now()
        WHERE tenant_id = t_id AND template_key = 'QUIZ_PUBLISHED' AND locale = 'es-PE' AND deleted = false;

        UPDATE edushift.notification_templates SET
            subject = 'Pago pendiente — {{invoiceNumber}}',
            body_html = '<h1 style="margin:0 0 16px;font-size:20px;color:#0f172a">Pago pendiente</h1><p style="margin:0 0 12px;font-size:15px;line-height:1.55;color:#334155">Estimado/a <strong>{{parentName}}</strong>,</p><p style="margin:0 0 12px;font-size:15px;line-height:1.55;color:#334155">La factura <strong>{{invoiceNumber}}</strong> por <strong>{{concept}}</strong> vence el <strong>{{dueDate}}</strong>.</p><p style="margin:0;font-size:16px;color:#0f172a"><strong>Monto:</strong> {{amount}}</p>',
            version = version + 1,
            updated_at = now()
        WHERE tenant_id = t_id AND template_key = 'PAYMENT_DUE' AND locale = 'es-PE' AND deleted = false;

        -- IN_APP only (no SMTP while on Gmail)
        UPDATE edushift.notification_templates SET
            subject = '{{title}}',
            body_html = '<h1 style="margin:0 0 12px;font-size:20px;color:#0f172a">{{title}}</h1><div style="font-size:15px;line-height:1.55;color:#334155">{{body}}</div><p style="margin:16px 0 0;font-size:12px;color:#94a3b8">{{senderName}} · {{tenantName}}</p>',
            version = version + 1,
            updated_at = now()
        WHERE tenant_id = t_id AND template_key = 'ANNOUNCEMENT' AND locale = 'es-PE' AND deleted = false;

        UPDATE edushift.notification_templates SET
            subject = 'Restablece tu contraseña — {{tenantName}}',
            body_html = '<h1 style="margin:0 0 16px;font-size:20px;color:#0f172a">Restablece tu contraseña</h1><p style="margin:0 0 12px;font-size:15px;line-height:1.55;color:#334155">Hola <strong>{{userFirstName}}</strong>,</p><p style="margin:0 0 16px;font-size:15px;line-height:1.55;color:#334155">Recibimos una solicitud para restablecer la contraseña de tu cuenta en <strong>{{tenantName}}</strong>. Si no fuiste tú, ignora este mensaje.</p><p style="margin:0 0 20px"><a href="{{resetLink}}" style="display:inline-block;padding:12px 20px;background:#0e7490;color:#ffffff;border-radius:10px;text-decoration:none;font-weight:600">Restablecer contraseña</a></p><p style="margin:0 0 8px;font-size:13px;color:#64748b">Válido por <strong>{{ttlMinutes}} minutos</strong>.</p><p style="margin:0;font-size:12px;color:#94a3b8;word-break:break-all">{{resetLink}}</p>',
            version = version + 1,
            updated_at = now()
        WHERE tenant_id = t_id AND template_key = 'PASSWORD_RESET' AND locale = 'es-PE' AND deleted = false;

        UPDATE edushift.notification_templates SET
            subject = 'Has sido asignado(a) — {{sectionName}}',
            body_html = '<h1 style="margin:0 0 16px;font-size:20px;color:#0f172a">Nueva asignación académica</h1><p style="margin:0 0 12px;font-size:15px;line-height:1.55;color:#334155">Hola <strong>{{teacherName}}</strong>,</p><p style="margin:0;font-size:15px;line-height:1.55;color:#334155">Has sido asignado(a) al nivel <strong>{{courseCode}}</strong>, sección <strong>{{sectionName}}</strong>. Revisa los detalles en tu panel &quot;Mis cursos&quot;.</p>',
            version = version + 1,
            updated_at = now()
        WHERE tenant_id = t_id AND template_key = 'TEACHER_ASSIGNED' AND locale = 'es-PE' AND deleted = false;

        UPDATE edushift.notification_templates SET
            subject = 'Nuevo docente en tu sección — {{sectionName}}',
            body_html = '<h1 style="margin:0 0 16px;font-size:20px;color:#0f172a">Nuevo docente asignado</h1><p style="margin:0 0 12px;font-size:15px;line-height:1.55;color:#334155">Te informamos que <strong>{{teacherName}}</strong> ha sido asignado(a) a tu sección <strong>{{sectionName}}</strong> del nivel <strong>{{levelCode}}</strong>.</p>',
            version = version + 1,
            updated_at = now()
        WHERE tenant_id = t_id AND template_key = 'SECTION_NEW_TEACHER' AND locale = 'es-PE' AND deleted = false;
    END LOOP;
END$$;
