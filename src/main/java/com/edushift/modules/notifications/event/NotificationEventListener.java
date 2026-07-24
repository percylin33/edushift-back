package com.edushift.modules.notifications.event;

import com.edushift.modules.notifications.entity.Notification;
import com.edushift.modules.notifications.entity.Notification.Channel;
import com.edushift.modules.notifications.realtime.RealtimeService;
import com.edushift.modules.notifications.service.NotificationService;
import com.edushift.modules.notifications.service.NotificationService.NotifyCommand;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.List;
import java.util.function.Supplier;
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
 * blocked by the email outbox enqueue.
 *
 * <h3>Tenant scope (Sprint 5 / DEBT-NOTIF-3, closed 2026-07-22)</h3>
 * {@code @Async + @TransactionalEventListener(AFTER_COMMIT)} means the
 * listener runs on a different thread AFTER the source tx has
 * committed — the {@code TenantContext} is therefore empty by the
 * time we enter this method, and any tenant-scoped JPA call (e.g.
 * {@code NotificationTemplateRepository.findByKeyAndLocale(...)})
 * fails with {@code Tenant context is required for this operation}.
 *
 * <p>The fix is a {@code TenantContext.runAs(event.tenantId(), …)}
 * wrapper. Publishers that fail to set {@link NotificationEvent#tenantId()}
 * fall back to the legacy behaviour: log + swallow — older modules
 * (attendance, evaluations, quizzes, tasks, AI) have not yet been
 * patched to pass the tenant id, so the warning is expected and the
 * notification is dropped (with audit trail via the {@code errorMessage}
 * column on the new failure path).
 *
 * <h3>Sprint 10 / BE-10.4 — realtime push</h3>
 * After persisting the in-app rows, we push each one to the
 * recipient's STOMP topic so the FE bell updates instantly
 * (replacing the 30s polling from Sprint 9).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

	private final NotificationService notificationService;
	private final RealtimeService realtime;

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onNotificationEvent(NotificationEvent event) {
		if (event.tenantId() == null) {
			// Legacy publisher (pre-Sprint-5) forgot to set the tenant
			// id. We refuse to dispatch without tenant scope because
			// every downstream call (template lookup, tenant filter,
			// realtime push) requires it. Surfaced loudly so the gap
			// is visible during the migration window.
			log.warn("[NotificationEvent] skipping dispatch — publisher did not set "
					+ "event.tenantId() (template={} source={}). This is a legacy "
					+ "publisher that needs to call .tenantId(...) on the builder.",
					event.templateKey(), event.sourceId());
			return;
		}
		runAsTenant(event.tenantId(), () -> {
			try {
				List<NotifyCommand> cmds = event.recipients().stream()
						.map(r -> NotifyCommand.builder()
								// DEBT-NOTIF-4 (Sprint 9A): publishers pass
								// publicUuid directly now — the defensive
								// resolver was removed. Recipient.userId is
								// users(public_uuid), the FK target.
								.recipient(r.userId())
								.email(r.email())
								.template(event.templateKey())
								.category(event.category())
								.channel(Channel.BOTH)
								.payload(event.payload())
								.build())
						.toList();
				List<Notification> rows = notificationService.notifyAllAndReturnRows(cmds);
				// Push each to the recipient's STOMP topic. If the user
				// is offline, the message is dropped (no offline queue
				// for MVP — they re-fetch on next login).
				for (Notification n : rows) {
					try {
						realtime.pushToUser(n);
					} catch (Exception pushEx) {
						log.warn("[Realtime] push failed for notification {}: {}",
								n.getPublicUuid(), pushEx.getMessage());
					}
				}
				log.info("[NotificationEvent] template={} recipients={} sent={} source={}",
						event.templateKey(), event.recipients().size(), rows.size(), event.sourceId());
			} catch (Exception ex) {
				// Never let a notification failure roll back the source
				// transaction. We log + swallow; the outbox has the row
				// and a future retry can pick it up.
				log.error("[NotificationEvent] failed to dispatch template={} source={}: {}",
						event.templateKey(), event.sourceId(), ex.getMessage(), ex);
			}
			return null;
		});
	}

	/**
	 * Convenience wrapper that delegates to {@link TenantContext#runAs}
	 * when a tenant id is available, or runs plain otherwise. Kept
	 * here as a single point to instrument (logging, metrics) without
	 * touching every call site.
	 */
	private static <T> T runAsTenant(java.util.UUID tenantId, Supplier<T> action) {
		return TenantContext.runAs(tenantId, action);
	}

	/** Fallback sync listener for in-test scenarios (no transaction). */
	@EventListener(condition = "#event != null")
	public void onNotificationEventSync(NotificationEvent event) {
		// No-op; the @TransactionalEventListener above handles the
		// production path. This exists so unit tests that don't have
		// a transaction context can still observe the event.
	}
}