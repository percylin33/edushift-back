package com.edushift.modules.notifications.seed;

import com.edushift.modules.notifications.entity.NotificationTemplate;
import java.util.List;

/**
 * Built-in notification templates (Sprint 9 / BE-9.1).
 *
 * <p>Seeded for every new tenant by {@code DevDataInitializer}. Each
 * template uses {@code {{key}}} placeholders that the
 * {@code NotificationTemplateEngine} expands from the payload JSON
 * at send time.</p>
 *
 * <h3>Why these 9</h3>
 * <ol>
 *   <li><b>WELCOME_TENANT</b> — onboarding email when a tenant is
 *       created (system category).</li>
 *   <li><b>STUDENT_ABSENT</b> — to the parent when their child is
 *       marked absent (ABSENCE category). Triggered by BE-9.3 hook.</li>
 *   <li><b>GRADE_PUBLISHED</b> — to the student when an evaluation
 *       goes from DRAFT to PUBLISHED (GRADE category).</li>
 *   <li><b>AI_FEEDBACK_READY</b> — to the student when an AI tutor
 *       finishes grading their submission (AI_FEEDBACK category).</li>
 *   <li><b>TASK_RETURNED</b> — to the student when the teacher
 *       returns a graded task (TASK category).</li>
 *   <li><b>QUIZ_PUBLISHED</b> — to the section when a new quiz goes
 *       live (QUIZ category).</li>
 *   <li><b>PAYMENT_DUE</b> — to the parent when a tuition invoice
 *       is generated (PAYMENT category, future Sprint 10).</li>
 *   <li><b>ANNOUNCEMENT</b> — generic announcement from
 *       TENANT_ADMIN (ANNOUNCEMENT category, BE-9.4).</li>
 *   <li><b>PASSWORD_RESET</b> — to the user who clicked "forgot
 *       password" (SYSTEM category, Sprint 17 / BE-17.1).</li>
 *   <li><b>TEACHER_ASSIGNED</b> — to the teacher when a new
 *       TeacherAssignment row is created (SYSTEM category, Sprint 5
 *       / DEBT-TEA-1 cascade).</li>
 *   <li><b>SECTION_NEW_TEACHER</b> — to every enrolled student of
 *       the section when a new teacher is assigned (ANNOUNCEMENT
 *       category, Sprint 5 / DEBT-TEA-1 cascade).</li>
 * </ol>
 */
public final class NotificationTemplateSeed {

    private NotificationTemplateSeed() {}

    public static List<NotificationTemplate> all(String locale) {
        return List.of(
                build("WELCOME_TENANT", "SYSTEM",
                        "Bienvenido a {{tenantName}}",
                        """
                        <h1 style="margin:0 0 16px;font-size:22px;color:#0f172a">¡Bienvenido a {{tenantName}}!</h1>
                        <p style="margin:0 0 12px;font-size:15px;line-height:1.55;color:#334155">Tu cuenta EduShift está activa. Accede con tu correo <strong>{{userEmail}}</strong>.</p>
                        <p style="margin:0;font-size:14px;color:#64748b">Si tienes dudas, contacta al administrador de tu colegio.</p>
                        """),
                build("STUDENT_ABSENT", "ABSENCE",
                        "Ausencia registrada — {{studentName}}",
                        """
                        <h1 style="margin:0 0 16px;font-size:20px;color:#0f172a">Ausencia registrada</h1>
                        <p style="margin:0 0 12px;font-size:15px;line-height:1.55;color:#334155">Estimado/a {{parentName}},</p>
                        <p style="margin:0 0 12px;font-size:15px;line-height:1.55;color:#334155">Le informamos que <strong>{{studentName}}</strong> no asistió a la sesión de <strong>{{courseName}}</strong>
                           el día <strong>{{date}}</strong>.</p>
                        <p style="margin:0 0 12px;font-size:15px;color:#334155">Motivo registrado: {{reason}}</p>
                        <p style="margin:0;font-size:14px;color:#64748b">Si requiere justificación, responda desde el portal o contacte a la institución.</p>
                        """),
                build("GRADE_PUBLISHED", "GRADE",
                        "Nueva calificación publicada — {{evaluationTitle}}",
                        """
                        <h1 style="margin:0 0 16px;font-size:20px;color:#0f172a">Calificación publicada</h1>
                        <p style="margin:0 0 12px;font-size:15px;line-height:1.55;color:#334155">Hola <strong>{{studentName}}</strong>,</p>
                        <p style="margin:0 0 12px;font-size:15px;line-height:1.55;color:#334155">Tu docente ha publicado la calificación de
                           <strong>{{evaluationTitle}}</strong> en <strong>{{courseName}}</strong>.</p>
                        <p style="margin:0;font-size:16px;color:#0f172a"><strong>Nota:</strong> {{grade}} / {{maxGrade}}</p>
                        """),
                build("AI_FEEDBACK_READY", "AI_FEEDBACK",
                                "Retroalimentación de tu entrega en {{taskTitle}}",
                                """
                                <h1 style="margin:0 0 16px;font-size:20px;color:#0f172a">Retroalimentación disponible</h1>
                                <p style="margin:0 0 12px;font-size:15px;line-height:1.55;color:#334155">Hola <strong>{{studentName}}</strong>,</p>
                                <p style="margin:0;font-size:15px;line-height:1.55;color:#334155">El asistente IA ha generado una retroalimentación para tu entrega
                                   en <strong>{{taskTitle}}</strong>. Revísala en la plataforma.</p>
                                """),
                build("TASK_RETURNED", "TASK",
                        "Tu tarea fue devuelta — {{taskTitle}}",
                        """
                        <h1 style="margin:0 0 16px;font-size:20px;color:#0f172a">Tarea devuelta</h1>
                        <p style="margin:0 0 12px;font-size:15px;line-height:1.55;color:#334155">Hola <strong>{{studentName}}</strong>,</p>
                        <p style="margin:0 0 12px;font-size:15px;line-height:1.55;color:#334155">Tu docente ha revisado tu entrega de <strong>{{taskTitle}}</strong>.</p>
                        <p style="margin:0 0 8px;font-size:16px;color:#0f172a"><strong>Nota:</strong> {{grade}} / {{maxGrade}}</p>
                        <p style="margin:0;font-size:14px;color:#64748b">Comentario: {{teacherComment}}</p>
                        """),
                build("QUIZ_PUBLISHED", "QUIZ",
                                "Nuevo quiz disponible — {{quizTitle}}",
                                """
                                <h1 style="margin:0 0 16px;font-size:20px;color:#0f172a">Nuevo quiz publicado</h1>
                                <p style="margin:0 0 12px;font-size:15px;line-height:1.55;color:#334155">Hola <strong>{{studentName}}</strong>,</p>
                                <p style="margin:0 0 12px;font-size:15px;line-height:1.55;color:#334155">Hay un nuevo quiz en
                                   <strong>{{courseName}}</strong>: <strong>{{quizTitle}}</strong>.</p>
                                <p style="margin:0;font-size:14px;color:#64748b">Fecha límite: <strong>{{dueDate}}</strong></p>
                                """),
                build("PAYMENT_DUE", "PAYMENT",
                                "Pago pendiente — {{invoiceNumber}}",
                                """
                                <h1 style="margin:0 0 16px;font-size:20px;color:#0f172a">Pago pendiente</h1>
                                <p style="margin:0 0 12px;font-size:15px;line-height:1.55;color:#334155">Estimado/a <strong>{{parentName}}</strong>,</p>
                                <p style="margin:0 0 12px;font-size:15px;line-height:1.55;color:#334155">La factura <strong>{{invoiceNumber}}</strong> por
                                   <strong>{{concept}}</strong> vence el <strong>{{dueDate}}</strong>.</p>
                                <p style="margin:0;font-size:16px;color:#0f172a"><strong>Monto:</strong> {{amount}}</p>
                                """),
                build("ANNOUNCEMENT", "ANNOUNCEMENT",
                                "{{title}}",
                                """
                                <h1 style="margin:0 0 12px;font-size:20px;color:#0f172a">{{title}}</h1>
                                <div style="font-size:15px;line-height:1.55;color:#334155">{{body}}</div>
                                <p style="margin:16px 0 0;font-size:12px;color:#94a3b8">{{senderName}} · {{tenantName}}</p>
                                """),
                build("PASSWORD_RESET", "SYSTEM",
                                "Restablece tu contraseña — {{tenantName}}",
                                """
                                <h1 style="margin:0 0 16px;font-size:20px;color:#0f172a">Restablece tu contraseña</h1>
                                <p style="margin:0 0 12px;font-size:15px;line-height:1.55;color:#334155">Hola <strong>{{userFirstName}}</strong>,</p>
                                <p style="margin:0 0 16px;font-size:15px;line-height:1.55;color:#334155">Recibimos una solicitud para restablecer la contraseña de tu cuenta en
                                   <strong>{{tenantName}}</strong>. Si no fuiste tú, ignora este mensaje.</p>
                                <p style="margin:0 0 20px"><a href="{{resetLink}}" style="display:inline-block;padding:12px 20px;background:#0e7490;color:#ffffff;border-radius:10px;text-decoration:none;font-weight:600">Restablecer contraseña</a></p>
                                <p style="margin:0 0 8px;font-size:13px;color:#64748b">Válido por <strong>{{ttlMinutes}} minutos</strong>.</p>
                                <p style="margin:0;font-size:12px;color:#94a3b8;word-break:break-all">{{resetLink}}</p>
                                """),
                build("TEACHER_ASSIGNED", "SYSTEM",
                                "Has sido asignado(a) — {{sectionName}}",
                                """
                                <h1 style="margin:0 0 16px;font-size:20px;color:#0f172a">Nueva asignación académica</h1>
                                <p style="margin:0 0 12px;font-size:15px;line-height:1.55;color:#334155">Hola <strong>{{teacherName}}</strong>,</p>
                                <p style="margin:0;font-size:15px;line-height:1.55;color:#334155">Has sido asignado(a) al nivel <strong>{{courseCode}}</strong>, sección
                                   <strong>{{sectionName}}</strong>. Revisa los detalles en tu panel "Mis cursos".</p>
                                """),
                build("SECTION_NEW_TEACHER", "ANNOUNCEMENT",
                                "Nuevo docente en tu sección — {{sectionName}}",
                                """
                                <h1 style="margin:0 0 16px;font-size:20px;color:#0f172a">Nuevo docente asignado</h1>
                                <p style="margin:0 0 12px;font-size:15px;line-height:1.55;color:#334155">Te informamos que <strong>{{teacherName}}</strong> ha sido asignado(a)
                                   a tu sección <strong>{{sectionName}}</strong> del nivel <strong>{{levelCode}}</strong>.</p>
                                """)
        );
    }

    private static NotificationTemplate build(String key, String category, String subject, String body) {
        NotificationTemplate t = new NotificationTemplate();
        t.setTemplateKey(key);
        // category is stored in the entity Notification row, not in the template;
        // we still record it as a key prefix for debugging.
        t.setSubject(subject);
        t.setBodyHtml(body);
        t.setLocale("es-PE");
        t.setSystem(true);
        t.setVersion(1);
        return t;
    }
}
