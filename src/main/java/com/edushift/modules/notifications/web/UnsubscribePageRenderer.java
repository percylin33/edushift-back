package com.edushift.modules.notifications.web;

import com.edushift.modules.notifications.entity.Notification.Category;
import com.edushift.modules.notifications.security.UnsubscribeTokenSigner.Parsed;
import org.springframework.stereotype.Component;

/**
 * Renders the post-unsubscribe HTML pages (Sprint 10 / DEBT-9-SEC-2).
 *
 * <p>Extracted from {@code UnsubscribeController} so the controller
 * only orchestrates verify → apply → render. Pure utility: no state,
 * easy to test in isolation.</p>
 */
@Component
public class UnsubscribePageRenderer {

    public String renderSuccess(Parsed parsed) {
        return """
                <!doctype html>
                <html><head><meta charset="utf-8"><title>Listo</title></head>
                <body style="font-family:system-ui;max-width:480px;margin:60px auto;text-align:center;color:#0f172a">
                  <h1 style="color:#059669">Listo.</h1>
                  <p>Has desactivado las notificaciones de <b>%s</b> por este canal.</p>
                  <p>Si cambias de opini&oacute;n, puedes activarlas de nuevo en tu perfil.</p>
                </body></html>
                """.formatted(parsed.category().name());
    }

    public String renderError(String message) {
        return """
                <!doctype html>
                <html><head><meta charset="utf-8"><title>Link expirado</title></head>
                <body style="font-family:system-ui;max-width:480px;margin:60px auto;text-align:center;color:#0f172a">
                  <h1 style="color:#dc2626">Link expirado o inv&aacute;lido</h1>
                  <p>%s</p>
                  <p>Por seguridad, los links de desuscripci&oacute;n caducan a las 24 horas.</p>
                </body></html>
                """.formatted(message);
    }
}
