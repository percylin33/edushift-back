package com.edushift.modules.notifications.controller;

import com.edushift.modules.notifications.entity.Notification.Category;
import com.edushift.modules.notifications.entity.Notification.Channel;
import com.edushift.modules.notifications.security.UnsubscribeTokenSigner;
import com.edushift.modules.notifications.service.NotificationService;
import com.edushift.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * One-click unsubscribe (Sprint 9 / SEC-9.1).
 *
 * <p>Public endpoint (no auth) — the HMAC token is the auth. When
 * a user clicks the unsubscribe link in an email footer, the FE
 * (or directly the link) calls {@code GET /api/v1/unsubscribe} and
 * the BE disables the matching {@link com.edushift.modules.notifications.entity.NotificationPreference}
 * row. The token expires after 24 hours (defense in depth).</p>
 *
 * <h3>Why public</h3>
 * If we required authentication, the user would have to log in
 * before unsubscribing — a 10× drop in unsubscribe rate. The HMAC
 * is the auth; the 24h expiry caps the blast radius if the link
 * leaks (e.g. via email forwarders).
 */
@RestController
@RequestMapping("/api/v1/unsubscribe")
@RequiredArgsConstructor
public class UnsubscribeController {

    private final UnsubscribeTokenSigner signer;
    private final NotificationService notificationService;

    /** Base URL of the FE (for the "go to settings" link). */
    @Value("${app.notifications.email.frontend-url:https://app.edushift.com}")
    private String frontendUrl;

    /** Render a friendly HTML page rather than a JSON 200. */
    @GetMapping
    public void unsubscribe(@RequestParam("token") String token,
                            HttpServletResponse response) throws IOException {
        try {
            var parsed = signer.verify(token);
            notificationService.setPreference(
                    parsed.userId(), parsed.channel(), parsed.category(), false);
            response.setContentType("text/html;charset=UTF-8");
            response.setStatus(200);
            response.getWriter().write(unsubscribedPage(parsed.category()));
        } catch (SecurityException ex) {
            response.setContentType("text/html;charset=UTF-8");
            response.setStatus(410);
            response.getWriter().write(errorPage(ex.getMessage()));
        }
    }

    /** Build a signed token for a user / channel / category combo. */
    public String buildToken(java.util.UUID userId, Channel channel, Category category) {
        long expiry = Instant.now().plus(24, ChronoUnit.HOURS).toEpochMilli();
        return signer.sign(userId, channel, category, expiry);
    }

    public String buildUnsubscribeUrl(java.util.UUID userId, Channel channel, Category category) {
        return frontendUrl + "/api/v1/unsubscribe?token=" + buildToken(userId, channel, category);
    }

    private static String unsubscribedPage(Category c) {
        return """
                <!doctype html>
                <html><head><meta charset="utf-8"><title>Listo</title></head>
                <body style="font-family:system-ui;max-width:480px;margin:60px auto;text-align:center;color:#0f172a">
                  <h1 style="color:#059669">Listo.</h1>
                  <p>Has desactivado las notificaciones de <b>%s</b> por este canal.</p>
                  <p>Si cambias de opinión, puedes activarlas de nuevo en tu perfil.</p>
                </body></html>
                """.formatted(c.name());
    }

    private static String errorPage(String msg) {
        return """
                <!doctype html>
                <html><head><meta charset="utf-8"><title>Link expirado</title></head>
                <body style="font-family:system-ui;max-width:480px;margin:60px auto;text-align:center;color:#0f172a">
                  <h1 style="color:#dc2626">Link expirado o inválido</h1>
                  <p>%s</p>
                  <p>Por seguridad, los links de desuscripción caducan a las 24 horas.</p>
                </body></html>
                """.formatted(msg);
    }
}
