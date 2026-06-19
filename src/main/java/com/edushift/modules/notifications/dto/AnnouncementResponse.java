package com.edushift.modules.notifications.dto;

import com.edushift.modules.notifications.entity.Announcement;
import com.edushift.modules.notifications.entity.Announcement.AudienceType;
import com.edushift.modules.notifications.entity.Announcement.Status;
import com.edushift.modules.notifications.service.NotificationTemplateEngine;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Announcement response DTO.
 *
 * <p><b>Sprint 10 / DEBT-9-FE-1 (defense in depth):</b> the body
 * is re-sanitized at the DTO boundary using the same
 * {@link NotificationTemplateEngine#sanitizeBody} that the service
 * uses on save. This way:</p>
 * <ul>
 *   <li>Even if a row was inserted bypassing the service
 *       (migration, direct SQL, legacy import), the FE never sees
 *       raw {@code <script>} or {@code onerror=...} payloads.</li>
 *   <li>Frontend can keep using {@code [innerHTML]} safely and
 *       {@code DomSanitizer} becomes a second layer of defense.</li>
 * </ul>
 *
 * <p>The engine is a singleton Spring component; we get it via the
 * static {@link NotificationTemplateEngine#sanitizeBody(String)} so
 * the DTO doesn't need a Spring context (it's a record, no DI).</p>
 */
public record AnnouncementResponse(
        UUID publicUuid,
        UUID authorUserId,
        String title,
        String bodyHtml,
        AudienceType audienceType,
        List<String> audienceIds,
        Status status,
        boolean pinned,
        Instant publishAt,
        Instant publishedAt,
        Instant createdAt
) {
    public static AnnouncementResponse from(Announcement a) {
        return new AnnouncementResponse(
                a.getPublicUuid(),
                a.getAuthorUserId(),
                a.getTitle(),
                NotificationTemplateEngine.sanitizeBodyStatic(a.getBodyHtml()),
                a.getAudienceType(),
                a.getAudienceIds(),
                a.getStatus(),
                a.isPinned(),
                a.getPublishAt(),
                a.getPublishedAt(),
                a.getCreatedAt()
        );
    }
}
