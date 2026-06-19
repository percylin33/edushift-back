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
 * <h3>Why these 8</h3>
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
 * </ol>
 */
public final class NotificationTemplateSeed {

    private NotificationTemplateSeed() {}

    public static List<NotificationTemplate> all(String locale) {
        return List.of(
                build("WELCOME_TENANT", "SYSTEM",
                        "Bienvenido a {{tenantName}}",
                        """
                        <h1>¡Bienvenido a {{tenantName}}!</h1>
                        <p>Tu cuenta EduShift está activa. Accede con tu correo <b>{{userEmail}}</b>.</p>
                        <p>Si tienes dudas, contacta al administrador.</p>
                        """),
                build("STUDENT_ABSENT", "ABSENCE",
                        "Ausencia registrada — {{studentName}}",
                        """
                        <h2>Ausencia registrada</h2>
                        <p>Estimado/a {{parentName}},</p>
                        <p>Le informamos que <b>{{studentName}}</b> no asistió a la sesión de <b>{{courseName}}</b>
                           el día <b>{{date}}</b>.</p>
                        <p>Motivo registrado: {{reason}}</p>
                        <p>Si requiere justificación, por favor responder este correo.</p>
                        """),
                build("GRADE_PUBLISHED", "GRADE",
                        "Nueva calificación publicada — {{evaluationTitle}}",
                        """
                        <h2>Calificación publicada</h2>
                        <p>Hola <b>{{studentName}}</b>,</p>
                        <p>Tu docente ha publicado la calificación de la evaluación
                           <b>{{evaluationTitle}}</b> en el curso <b>{{courseName}}</b>.</p>
                        <p>Nota: <b>{{grade}}</b> / {{maxGrade}}</p>
                        """),
                build("AI_FEEDBACK_READY", "AI_FEEDBACK",
                                "Retroalimentación de tu entrega en {{taskTitle}}",
                                """
                                <h2>Retroalimentación disponible</h2>
                                <p>Hola <b>{{studentName}}</b>,</p>
                                <p>El asistente IA ha generado una retroalimentación para tu entrega
                                   en la tarea <b>{{taskTitle}}</b>.</p>
                                <p>Revísala en la plataforma y conversa con el asistente si quieres profundizar.</p>
                                """),
                build("TASK_RETURNED", "TASK",
                        "Tu tarea fue devuelta — {{taskTitle}}",
                        """
                        <h2>Tarea devuelta</h2>
                        <p>Hola <b>{{studentName}}</b>,</p>
                        <p>Tu docente ha revisado tu entrega de <b>{{taskTitle}}</b>.</p>
                        <p>Nota: <b>{{grade}}</b> / {{maxGrade}}</p>
                        <p>Comentario del docente: {{teacherComment}}</p>
                        """),
                build("QUIZ_PUBLISHED", "QUIZ",
                                "Nuevo quiz disponible — {{quizTitle}}",
                                """
                                <h2>Nuevo quiz publicado</h2>
                                <p>Hola <b>{{studentName}}</b>,</p>
                                <p>Tu docente ha publicado un nuevo quiz en el curso
                                   <b>{{courseName}}</b>: <b>{{quizTitle}}</b>.</p>
                                <p>Fecha límite: <b>{{dueDate}}</b></p>
                                """),
                build("PAYMENT_DUE", "PAYMENT",
                                "Pago pendiente — {{invoiceNumber}}",
                                """
                                <h2>Pago pendiente</h2>
                                <p>Estimado/a <b>{{parentName}}</b>,</p>
                                <p>Le recordamos que la factura <b>{{invoiceNumber}}</b> por concepto
                                   de <b>{{concept}}</b> vence el <b>{{dueDate}}</b>.</p>
                                <p>Monto: <b>{{amount}}</b></p>
                                """),
                build("ANNOUNCEMENT", "ANNOUNCEMENT",
                                "{{title}}",
                                """
                                <h2>{{title}}</h2>
                                <p>{{body}}</p>
                                <hr/>
                                <p style="color:#888;font-size:0.85em">Enviado por {{senderName}} — {{tenantName}}</p>
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
