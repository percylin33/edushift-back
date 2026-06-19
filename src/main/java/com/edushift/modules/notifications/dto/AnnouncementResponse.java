package com.edushift.modules.notifications.dto;

import com.edushift.modules.notifications.entity.Announcement;
import com.edushift.modules.notifications.entity.Announcement.AudienceType;
import com.edushift.modules.notifications.entity.Announcement.Status;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
                a.getBodyHtml(),
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
