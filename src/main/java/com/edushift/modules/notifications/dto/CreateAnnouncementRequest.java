package com.edushift.modules.notifications.dto;

import com.edushift.modules.notifications.entity.Announcement.AudienceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * Create / update request for an announcement (Sprint 9 / BE-9.4).
 * The author is the current authenticated user (from JWT).
 */
public record CreateAnnouncementRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String bodyHtml,
        @NotNull AudienceType audienceType,
        List<String> audienceIds,
        boolean pinned,
        Instant publishAt
) {}
