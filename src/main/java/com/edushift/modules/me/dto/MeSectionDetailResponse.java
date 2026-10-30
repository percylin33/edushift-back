package com.edushift.modules.me.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

/**
 * Detail projection of a course as seen by a STUDENT
 * ({@code GET /api/v1/me/sections/{publicUuid}?assignment=}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeSectionDetailResponse(
        UUID publicUuid,
        String name,
        String gradeName,
        String gradeLevelName,
        String academicYearLabel,
        String teacherName,
        String courseName,
        List<MeMaterialItem> materials,
        List<MeTaskItem> tasks,
        List<MeQuizItem> quizzes,
        UUID teacherAssignmentPublicUuid,
        String periodLabel
) {

    public record MeMaterialItem(
            UUID publicUuid,
            String title,
            String kind,
            UUID ownerPublicUuid,
            String createdAt,
            UUID filePublicUuid,
            String filename,
            String externalUrl
    ) {
    }

    public record MeTaskItem(
            UUID publicUuid,
            String title,
            String dueAt,
            boolean overdue,
            UUID ownerPublicUuid,
            boolean hasAttachment
    ) {
    }

    public record MeQuizItem(
            UUID publicUuid,
            String title,
            String status,
            String dueAt,
            Integer maxScore,
            Integer timeLimitMinutes,
            Integer attemptsAllowed,
            UUID ownerPublicUuid,
            int questionCount
    ) {
    }
}
