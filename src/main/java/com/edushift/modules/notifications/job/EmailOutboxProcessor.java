package com.edushift.modules.notifications.job;

import com.edushift.modules.notifications.email.EmailBrandingContext;
import com.edushift.modules.notifications.email.EmailBrandingResolver;
import com.edushift.modules.notifications.entity.EmailOutbox;
import com.edushift.modules.notifications.entity.Notification;
import com.edushift.modules.notifications.repository.EmailOutboxRepository;
import com.edushift.modules.notifications.repository.NotificationRepository;
import com.edushift.modules.notifications.service.EmailSender;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Email outbox processor (Sprint 9 / BE-9.1, ADR-9.1, SEC-9.1).
 *
 * <p>Scheduled job that picks up {@code PENDING} rows from
 * {@code email_outbox} (whose {@code next_retry_at <= now}) and
 * dispatches them via {@link EmailSender}. On failure, the row is
 * re-queued with exponential backoff up to {@link EmailOutbox#MAX_ATTEMPTS}
 * retries, then marked {@code FAILED}.</p>
 *
 * <h3>Sprint 9 / SEC-9.1 — unsubscribe link</h3>
 * We look up the originating {@link Notification} (via the outbox's
 * {@code notification_id}) and pass the userId / channel / category
 * to the sender so it can build the per-recipient HMAC-signed
 * unsubscribe link.
 *
 * <h3>Why {@code @ConditionalOnProperty} (Sprint 12 / debug-12)</h3>
 * <p>This bean depends on {@link EmailSender}, which is itself gated
 * on {@code app.notifications.email.enabled=true}. Without the same
 * gate, this bean's constructor would fail to autowire
 * {@link EmailSender} in environments where email is disabled.
 * Keep both gates in sync: any change to one needs the other.</p>
 */
@Component
@ConditionalOnProperty(name = "app.notifications.email.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class EmailOutboxProcessor {

    private final EmailOutboxRepository outboxRepo;
    private final NotificationRepository notificationRepo;
    private final EmailSender emailSender;
    private final EmailBrandingResolver brandingResolver;

    @Value("${app.notifications.email.batch-size:50}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${app.notifications.email.process-interval:30000}",
               initialDelay = 10_000)
    public void process() {
        Instant now = Instant.now();
        List<EmailOutbox> batch = outboxRepo.pickPending(now, batchSize);
        if (batch.isEmpty()) return;

        log.info("[EmailOutbox] processing batch of {} emails", batch.size());
        Map<UUID, Notification> notifications = loadNotifications(batch);
        Map<UUID, EmailBrandingContext> brandingByTenant = loadBranding(batch);
        for (EmailOutbox row : batch) {
            processOne(row.getId(), notifications, brandingByTenant);
        }
    }

    /**
     * One transaction per row so a failure does not roll back the rest
     * of the batch. The notification and branding maps were loaded once
     * for the whole batch.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processOne(
            UUID id,
            Map<UUID, Notification> notifications,
            Map<UUID, EmailBrandingContext> brandingByTenant) {
        outboxRepo.findById(id).ifPresent(row -> attemptSend(row, notifications, brandingByTenant));
    }

    private Map<UUID, Notification> loadNotifications(List<EmailOutbox> batch) {
        List<UUID> ids = batch.stream()
                .map(EmailOutbox::getNotificationId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Notification> byId = new HashMap<>();
        for (Notification notification : notificationRepo.findAllById(ids)) {
            byId.put(notification.getId(), notification);
        }
        return byId;
    }

    private Map<UUID, EmailBrandingContext> loadBranding(List<EmailOutbox> batch) {
        Map<UUID, EmailBrandingContext> branding = new HashMap<>();
        for (EmailOutbox row : batch) {
            UUID tenantId = row.getTenantId();
            if (tenantId != null && !branding.containsKey(tenantId)) {
                branding.put(tenantId, brandingResolver.forTenantId(tenantId));
            }
        }
        return branding;
    }

    private void attemptSend(
            EmailOutbox row,
            Map<UUID, Notification> notifications,
            Map<UUID, EmailBrandingContext> brandingByTenant) {
        EmailBrandingContext branding = brandingByTenant.get(row.getTenantId());
        if (log.isDebugEnabled() && branding != null) {
            log.debug("[EmailOutbox] using branding snapshot tenant={}", branding.nameOrDefault());
        }
        Notification n = row.getNotificationId() == null
                ? null
                : notifications.get(row.getNotificationId());
        try {
            if (n == null) {
                emailSender.send(row.getToEmail(), row.getSubject(), row.getBodyHtml());
            } else {
                emailSender.send(row.getToEmail(), row.getSubject(), row.getBodyHtml(),
                        n.getRecipientUserId(), n.getChannel(), n.getCategory());
            }
            row.setStatus(EmailOutbox.Status.SENT);
            row.setSentAt(Instant.now());
            row.setLastError(null);
            outboxRepo.save(row);
        } catch (Exception ex) {
            row.setAttempts(row.getAttempts() + 1);
            row.setLastError(truncate(ex.getMessage(), 2000));
            if (row.getAttempts() >= EmailOutbox.MAX_ATTEMPTS) {
                row.setStatus(EmailOutbox.Status.FAILED);
                log.error("[EmailOutbox] giving up after {} attempts for outbox id={}: {}",
                        row.getAttempts(), row.getId(), ex.getMessage());
            } else {
                row.setNextRetryAt(row.computeNextRetryAt());
                log.warn("[EmailOutbox] attempt {} failed for outbox id={}, retry at {}: {}",
                        row.getAttempts(), row.getId(), row.getNextRetryAt(), ex.getMessage());
            }
            outboxRepo.save(row);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
