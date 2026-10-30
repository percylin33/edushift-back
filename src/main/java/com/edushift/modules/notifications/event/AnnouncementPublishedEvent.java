package com.edushift.modules.notifications.event;

import java.util.List;
import java.util.UUID;

/**
 * Fired after an announcement is marked {@code PUBLISHED} and recipient
 * rows are persisted. Listeners run {@code AFTER_COMMIT} so in-app
 * notification fan-out does not extend the HTTP publish transaction
 * (school-wide audiences can be thousands of users).
 */
public record AnnouncementPublishedEvent(
		UUID tenantId,
		UUID announcementPublicUuid,
		UUID announcementId,
		String title,
		String bodyHtml,
		String bodyExcerpt,
		String senderName,
		String tenantName,
		UUID authorUserId,
		List<UUID> recipientUserIds
) {
}
