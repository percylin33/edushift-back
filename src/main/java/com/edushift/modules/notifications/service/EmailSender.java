package com.edushift.modules.notifications.service;

import com.edushift.modules.ai.safety.PiiSafetyFilter;
import com.edushift.modules.notifications.entity.Notification.Category;
import com.edushift.modules.notifications.entity.Notification.Channel;
import com.edushift.modules.notifications.security.UnsubscribeTokenSigner;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Email sender (Sprint 9 / BE-9.1 + SEC-9.1).
 *
 * <p>Thin wrapper over {@link JavaMailSender} that applies three
 * EduShift-specific safety nets on the way out:</p>
 * <ol>
 *   <li><b>PII filter</b> (SEC-8.1) on subject + body, in case the
 *       template author forgot to avoid PII in the payload.</li>
 *   <li><b>From-address</b> is locked to a configured value; we never
 *       let the template set its own {@code From}.</li>
 *   <li><b>One-click unsubscribe</b> (SEC-9.1): a footer link with
 *       an HMAC-signed token. Lets users opt out without logging
 *       in (10× better unsubscribe rate).</li>
 * </ol>
 *
 * <h3>Failure handling</h3>
 * On any {@code MailException} or {@code MessagingException}, the
 * caller ({@code EmailOutboxProcessor}) catches and re-queues with
 * backoff. We deliberately don't throw checked exceptions to keep
 * the processor code simple.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailSender {

    private final JavaMailSender mailSender;
    private final PiiSafetyFilter piiFilter;
    private final UnsubscribeTokenSigner unsubscribeSigner;

    @Value("${app.notifications.email.from:noreply@edushift.local}")
    private String fromAddress;

    @Value("${app.notifications.email.enabled:false}")
    private boolean enabled;

    /**
     * Send a single email. Returns {@code true} on success, throws on failure.
     *
     * @param to        recipient address (must be a valid email; not validated here).
     * @param subject   email subject (PII-masked before send).
     * @param bodyHtml  HTML body (PII-masked before send).
     */
    public void send(String to, String subject, String bodyHtml) {
        send(to, subject, bodyHtml, null, null, null);
    }

    /**
     * Send with a per-recipient unsubscribe link (Sprint 9 / SEC-9.1).
     *
     * @param userId   recipient user id (for the HMAC token); if null, no
     *                 unsubscribe link is added.
     * @param channel  notification channel (so the link disables the right one).
     * @param category notification category (so the link disables the right one).
     */
    public void send(String to, String subject, String bodyHtml,
                     UUID userId, Channel channel, Category category) {
        // 1) PII mask.
        String safeSubject = piiFilter.mask(subject);
        String safeBody    = piiFilter.mask(bodyHtml);

        // 2) Inject the unsubscribe footer (Sprint 9 / SEC-9.1,
        //    refactored Sprint 10 / DEBT-9-SEC-2: signer owns the URL).
        if (userId != null && channel != null && category != null) {
            String unsubUrl = unsubscribeSigner.buildUrl(userId, channel, category);
            safeBody = injectUnsubscribeFooter(safeBody, unsubUrl);
        }

        if (!enabled) {
            log.info("[EmailSender] DISABLED — would send to={} subject='{}' bodyLen={}",
                    to, safeSubject, safeBody.length());
            return;
        }

        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(safeSubject);
            helper.setText(safeBody, true);
            mailSender.send(msg);
        } catch (MessagingException e) {
            throw new EmailSendException("Failed to render email: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new EmailSendException("SMTP send failed: " + e.getMessage(), e);
        }
    }

    /**
     * Inject a small footer with the unsubscribe link before
     * {@code </body>}, or appended to the end if the body has no
     * {@code </body>} tag. Keeps the link visible (CAN-SPAM / GDPR
     * requirement).
     */
    private static String injectUnsubscribeFooter(String body, String unsubUrl) {
        String footer = """
                <div style="margin-top:32px;padding-top:16px;border-top:1px solid #e2e8f0;
                            font-size:11px;color:#64748b;text-align:center">
                  <p>Recibes este correo porque tienes una cuenta activa en EduShift.</p>
                  <p><a href="%s" style="color:#0d9488">Cancelar suscripci&oacute;n</a></p>
                </div>
                """.formatted(unsubUrl);
        int idx = body.toLowerCase().lastIndexOf("</body>");
        if (idx >= 0) {
            return body.substring(0, idx) + footer + body.substring(idx);
        }
        return body + footer;
    }

    /** Thrown by {@link #send} on any failure. Caller catches and re-queues. */
    public static class EmailSendException extends RuntimeException {
        public EmailSendException(String msg, Throwable cause) { super(msg, cause); }
    }
}
