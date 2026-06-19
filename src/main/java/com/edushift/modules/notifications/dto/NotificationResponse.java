package com.edushift.modules.notifications.dto;

import com.edushift.modules.notifications.entity.Notification;
import com.edushift.modules.notifications.entity.Notification.Category;
import com.edushift.modules.notifications.entity.Notification.Channel;
import com.edushift.modules.notifications.entity.Notification.Status;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a single notification (Sprint 9 / BE-9.1).
 * Exposed via the {@code /api/v1/notifications} endpoints. Includes
 * the rendered subject + body (so the FE doesn't have to call the
 * template engine).
 */
public record NotificationResponse(
        UUID publicUuid,
        String templateKey,
        Category category,
        Channel channel,
        Status status,
        String subject,
        String bodyHtml,
        Instant sentAt,
        Instant readAt
) {
    public static NotificationResponse from(Notification n, String subject, String body) {
        return new NotificationResponse(
                n.getPublicUuid(),
                n.getTemplateKey(),
                n.getCategory(),
                n.getChannel(),
                n.getStatus(),
                subject,
                body,
                n.getSentAt(),
                n.getReadAt()
        );
    }
}
