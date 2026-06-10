package com.edushift.modules.evaluations.graderecord.dto;

import java.util.UUID;

/**
 * Optional filters applied to list endpoints. Any null field means
 * "don't filter on this dimension".
 *
 * <p>Used by {@code GET /v1/academic/evaluations/{uuid}/grade-records?...}
 * and the per-student endpoint.
 *
 * @param studentPublicUuid restrict to grades of a single student.
 * @param sectionPublicUuid restrict to grades whose evaluation's
 *                          assignment belongs to this section.
 * @param isActive          {@code true}/{@code false} matches the flag
 *                          exactly; {@code null} returns both.
 */
public record GradeRecordFilters(
        UUID studentPublicUuid,
        UUID sectionPublicUuid,
        Boolean isActive
) {
}
