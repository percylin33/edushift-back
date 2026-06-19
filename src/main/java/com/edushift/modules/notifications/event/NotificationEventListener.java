package com.edushift.modules.notifications.event;

import com.edushift.modules.notifications.entity.Notification.Channel;
import com.edushift.modules.notifications.service.NotificationService;
import com.edushift.modules.notifications.service.NotificationService.NotifyCommand;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens to {@link NotificationEvent} published by source modules
 * (Sprint 9 / BE-9.3) and dispatches via {@link NotificationService}.
 *
 * <h3>Async + post-commit</h3>
 * We use {@link TransactionalEventListener} with
 * {@code AFTER_COMMIT} so the notification is sent only after the
 * source transaction commits. This prevents "phantom notifications"
 * (a notification for an event that was rolled back). We also mark
 * the listener {@code @Async} so the publishing thread is not
 * blocked by the email outbox enqueue.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationEvent(NotificationEvent event) {
        try {
            List<NotifyCommand> cmds = event.recipients().stream()
                    .map(r -> NotifyCommand.builder()
                            .recipient(r.userId())
                            .email(r.email())
                            .template(event.templateKey())
                            .category(event.category())
                            .channel(Channel.BOTH)
                            .payload(event.payload())
                            .build())
                    .toList();
            var sent = notificationService.notifyAll(cmds);
            log.info("[NotificationEvent] template={} recipients={} sent={} source={}",
                    event.templateKey(), event.recipients().size(), sent.size(), event.sourceId());
        } catch (Exception ex) {
            // Never let a notification failure roll back the source
            // transaction. We log + swallow; the outbox has the row
            // and a future retry can pick it up.
            log.error("[NotificationEvent] failed to dispatch template={} source={}: {}",
                    event.templateKey(), event.sourceId(), ex.getMessage(), ex);
        }
    }

    /** Fallback sync listener for in-test scenarios (no transaction). */
    @EventListener(condition = "#event != null")
    public void onNotificationEventSync(NotificationEvent event) {
        // No-op; the @TransactionalEventListener above handles the
        // production path. This exists so unit tests that don't have
        // a transaction context can still observe the event.
    }
}
