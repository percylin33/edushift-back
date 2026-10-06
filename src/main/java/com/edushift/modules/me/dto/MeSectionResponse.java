package com.edushift.modules.me.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight projection of a course card as seen by a STUDENT in
 * {@code GET /api/v1/me/sections} (DEBT-STUDENT-PRIVACY / Fase 2 + V105).
 *
 * <p>One row per active {@code TeacherAssignment} in the enrolled section.
 * Counts are scoped to that assignment (not the whole section).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeSectionResponse(
        UUID publicUuid,
        String name,
        String gradeName,
        String gradeLevelName,
        String academicYearLabel,
        String teacherName,
        String courseName,
        long materialCount,
        long taskCount,
        long quizCount,
        Instant enrolledAt,
        UUID teacherAssignmentPublicUuid,
        String periodLabel
) {
}
