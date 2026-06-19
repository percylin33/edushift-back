package com.edushift.modules.notifications.controller;

import com.edushift.modules.notifications.security.UnsubscribeTokenSigner;
import com.edushift.modules.notifications.service.NotificationService;
import com.edushift.modules.notifications.web.UnsubscribePageRenderer;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * One-click unsubscribe (Sprint 9 / SEC-9.1, refactored Sprint 10 / DEBT-9-SEC-2).
 *
 * <p>Public endpoint (no auth) — the HMAC token is the auth. When
 * a user clicks the unsubscribe link in an email footer, the FE
 * (or directly the link) calls {@code GET /api/v1/unsubscribe} and
 * the BE disables the matching
 * {@link com.edushift.modules.notifications.entity.NotificationPreference}
 * row. The token expires after 24 hours (defense in depth).</p>
 *
 * <h3>Why public</h3>
 * If we required authentication, the user would have to log in
 * before unsubscribing — a 10× drop in unsubscribe rate. The HMAC
 * is the auth; the 24h expiry caps the blast radius if the link
 * leaks (e.g. via email forwarders).
 *
 * <h3>Sprint 10 refactor (DEBT-9-SEC-2)</h3>
 * Single responsibility: verify + apply + render. URL construction
 * moved to {@link UnsubscribeTokenSigner#buildUrl} (no more circular
 * dependency with {@code EmailSender}). HTML rendering moved to
 * {@link UnsubscribePageRenderer}.
 */
@RestController
@RequestMapping("/api/v1/unsubscribe")
@RequiredArgsConstructor
public class UnsubscribeController {

    private final UnsubscribeTokenSigner signer;
    private final NotificationService notificationService;
    private final UnsubscribePageRenderer pageRenderer;

    /** Render a friendly HTML page rather than a JSON 200. */
    @GetMapping
    public void unsubscribe(@RequestParam("token") String token,
                            HttpServletResponse response) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        try {
            var parsed = signer.verify(token);
            notificationService.setPreference(
                    parsed.userId(), parsed.channel(), parsed.category(), false);
            response.setStatus(200);
            response.getWriter().write(pageRenderer.renderSuccess(parsed));
        } catch (SecurityException ex) {
            response.setStatus(410);
            response.getWriter().write(pageRenderer.renderError(ex.getMessage()));
        }
    }
}
