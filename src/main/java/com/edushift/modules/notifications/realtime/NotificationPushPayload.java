package com.edushift.modules.notifications.realtime;

import com.edushift.modules.notifications.entity.Notification.Category;
import com.edushift.modules.notifications.entity.Notification.Channel;
import java.time.Instant;
import java.util.UUID;

/**
 * Payload pushed to STOMP subscribers when a new notification
 * arrives (Sprint 10 / BE-10.4, ADR-10.4).
 *
 * <p>This is the BE-equivalent of the
 * {@code Notification} in-app row: just enough info for the FE to
 * update the bell badge and the dropdown without re-fetching the
 * whole list. The FE then calls {@code GET /notifications} to
 * hydrate the dropdown on click.</p>
 */
public record NotificationPushPayload(
        UUID notificationUuid,
        UUID recipientUserId,
        String templateKey,
        Category category,
        Channel channel,
        String subject,
        Instant occurredAt
) {}
