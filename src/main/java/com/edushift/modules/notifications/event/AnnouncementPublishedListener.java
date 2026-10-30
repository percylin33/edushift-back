package com.edushift.modules.notifications.event;

import com.edushift.modules.notifications.entity.Notification.Category;
import com.edushift.modules.notifications.entity.Notification.Channel;
import com.edushift.modules.notifications.service.NotificationService;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * AFTER_COMMIT fan-out of in-app {@code ANNOUNCEMENT} notifications.
 * Keeps {@code AnnouncementService#publish} transactions short.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnnouncementPublishedListener {

	/** Cap per {@link NotificationService#notifyAll} call to bound TX size. */
	private static final int NOTIFY_BATCH = 200;

	private final NotificationService notificationService;

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onAnnouncementPublished(AnnouncementPublishedEvent event) {
		if (event == null || event.recipientUserIds() == null || event.recipientUserIds().isEmpty()) {
			return;
		}
		if (event.tenantId() == null) {
			log.warn("[Announcement] AnnouncementPublishedEvent missing tenantId — skip fan-out publicUuid={}",
					event.announcementPublicUuid());
			return;
		}

		TenantContext.runAs(event.tenantId(), () -> {
			List<UUID> recipients = event.recipientUserIds();
			int sent = 0;
			for (int i = 0; i < recipients.size(); i += NOTIFY_BATCH) {
				List<UUID> slice = recipients.subList(i, Math.min(i + NOTIFY_BATCH, recipients.size()));
				List<NotificationService.NotifyCommand> cmds = new ArrayList<>(slice.size());
				for (UUID uid : slice) {
					cmds.add(NotificationService.NotifyCommand.builder()
							.recipient(uid)
							.template("ANNOUNCEMENT")
							.category(Category.ANNOUNCEMENT)
							// IN_APP only: email fan-out would rate-limit Gmail App Password
							.channel(Channel.IN_APP)
							.put("title", event.title())
							.put("body", event.bodyExcerpt() == null ? "" : event.bodyExcerpt())
							.put("senderName", event.senderName() == null ? "" : event.senderName())
							.put("tenantName", event.tenantName() == null ? "" : event.tenantName())
							.put("announcementPublicUuid", event.announcementPublicUuid() == null
									? ""
									: event.announcementPublicUuid().toString())
							.build());
				}
				sent += notificationService.notifyAll(cmds).size();
			}
			log.info(
					"[Announcement] fan-out (after-commit) -- publicUuid={} recipients={} notified={}",
					event.announcementPublicUuid(),
					recipients.size(),
					sent);
			return Boolean.TRUE;
		});
	}
}
