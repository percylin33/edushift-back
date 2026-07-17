package com.edushift.modules.help.dto;

import com.edushift.modules.help.entity.HelpFeedback;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * One feedback record returned to the FE.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HelpFeedbackResponse(
        UUID publicUuid,
        String role,
        String chapterFile,
        String body,
        String status,
        Instant createdAt
) {
    public static HelpFeedbackResponse from(HelpFeedback f) {
        return new HelpFeedbackResponse(
                f.getPublicUuid(),
                f.getRole(),
                f.getChapterFile(),
                f.getBody(),
                f.getStatus().name(),
                f.getCreatedAt());
    }
}