package com.edushift.modules.me.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

/**
 * Detail projection of a section as seen by a STUDENT in
 * {@code GET /api/v1/me/sections/{publicUuid}} (DEBT-STUDENT-PRIVACY /
 * Fase 2).
 *
 * <p>Combines the section header (grade, year, teacher, course) with
 * three nested lists: materials, tasks, quizzes. The lists are
 * paginated and ordered on the server so the FE can render a
 * consistent "Mis cursos" page regardless of how many rows exist.</p>
 *
 * <p>Authorization is enforced at the service: the caller must have an
 * ACTIVE enrollment on this section, otherwise the service throws
 * {@code NotFoundException} (anti-enumeration). Cross-tenant requests
 * resolve to 404 the same way.</p>
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
        List<MeQuizItem> quizzes
) {

    /**
     * Compact material row used by the section detail. Mirrors
     * {@code MaterialSummary} but is owned by the {@code /me} module to
     * keep the contract decoupled from the LMS internal changes.
     */
    public record MeMaterialItem(
            UUID publicUuid,
            String title,
            String kind,
            UUID ownerPublicUuid,
            String createdAt
    ) {
    }

    /**
     * Compact task row used by the section detail. Carries the due date
     * and a derived {@code overdue} flag so the FE can render a badge
     * without computing dates client-side.
     */
    public record MeTaskItem(
            UUID publicUuid,
            String title,
            String dueAt,
            boolean overdue,
            UUID ownerPublicUuid,
            boolean hasAttachment
    ) {
    }

    /**
     * Compact quiz row used by the section detail. {@code status}
     * comes straight from {@code QuizStatus} (DRAFT / PUBLISHED /
     * CLOSED) so the FE can grey out drafts and quizzes the student
     * already finished.
     */
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
