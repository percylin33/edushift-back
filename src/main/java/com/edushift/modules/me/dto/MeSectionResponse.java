package com.edushift.modules.me.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight projection of a section as seen by a STUDENT in
 * {@code GET /api/v1/me/sections} (DEBT-STUDENT-PRIVACY / Fase 2).
 *
 * <p>Only carries the data the dashboard list needs: identifiers, the
 * display name, the grade, the academic year, the teacher (when known)
 * and three lightweight counters so the FE can render "5 materials · 3
 * tasks · 1 quiz" without a second round-trip per row.</p>
 *
 * <p>Counts are computed once and cached on the {@code MeAcademicService}
 * layer (no N+1). Hard delete-safe: rows whose parent section was
 * soft-deleted cannot reach this projection (the service filters them
 * out at the SQL level).</p>
 *
 * @param publicUuid         section public UUID
 * @param name               friendly section name (e.g. "A", "B")
 * @param gradeName          grade name (e.g. "5.° de primaria")
 * @param gradeLevelName     academic level name (e.g. "Primaria")
 * @param academicYearLabel  year label (e.g. "2026")
 * @param teacherName        primary teacher display name, or {@code null}
 * @param courseName         course display name (e.g. "Matemáticas")
 * @param materialCount      total materials visible to the student
 * @param taskCount          total tasks visible to the student
 * @param quizCount          total published quizzes visible to the student
 * @param enrolledAt         enrollment date for the active row
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
        Instant enrolledAt
) {
}
