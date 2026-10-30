package com.edushift.modules.notifications.dto;

import com.edushift.modules.notifications.entity.Announcement.AudienceType;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Dry-run audience counts before an announcement is published. */
public record AudiencePreviewRequest(
        @NotNull AudienceType audienceType,
        List<String> audienceIds,
        Boolean includeGuardians
) {}
