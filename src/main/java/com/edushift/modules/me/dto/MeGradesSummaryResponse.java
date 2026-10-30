package com.edushift.modules.me.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Student grade summary for one academic period.
 * Averages use the same formula as the teacher grade book.
 * {@code overallAverage} is the unweighted mean of numeric course
 * averages. CNEB courses contribute a letter and stay out of that mean.
 */
public record MeGradesSummaryResponse(
        Period period,
        BigDecimal overallAverage,
        int courseCount,
        int gradedCourseCount,
        int gradedCount,
        List<Course> courses,
        List<Item> items
) {

    public record Period(
            UUID publicUuid,
            String name,
            boolean current
    ) {
    }

    public record Course(
            UUID assignmentPublicUuid,
            UUID coursePublicUuid,
            String courseName,
            String sectionName,
            String teacherName,
            String scale,
            BigDecimal average,
            String literalAverage,
            List<Category> categories
    ) {
    }

    public record Category(
            UUID publicUuid,
            String name,
            BigDecimal weight,
            BigDecimal average,
            String literalAverage
    ) {
    }

    /**
     * @param source {@code REGISTER} for a grade record, {@code TASK} for an LMS submission
     * @param role   {@code OFFICIAL} moves the register average, {@code EVIDENCE} is a
     *               published score outside the template, {@code CLASSWORK} is an LMS task
     */
    public record Item(
            UUID publicUuid,
            UUID assignmentPublicUuid,
            UUID coursePublicUuid,
            String courseName,
            String sectionName,
            String evaluationName,
            String kind,
            BigDecimal weight,
            String scale,
            BigDecimal score,
            BigDecimal maxScore,
            String literal,
            String comments,
            String teacherName,
            UUID periodPublicUuid,
            Instant scheduledDate,
            Instant recordedAt,
            boolean countsTowardAverage,
            String source,
            String role
    ) {
    }
}
