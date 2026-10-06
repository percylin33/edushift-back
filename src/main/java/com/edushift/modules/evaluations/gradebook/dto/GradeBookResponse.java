package com.edushift.modules.evaluations.gradebook.dto;

import com.edushift.modules.academic.period.entity.PeriodType;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import java.util.List;
import java.util.UUID;

public record GradeBookResponse(
        UUID assignmentPublicUuid,
        UUID sectionPublicUuid,
        String sectionName,
        UUID gradePublicUuid,
        String gradeName,
        Integer gradeOrdinal,
        String levelCode,
        UUID coursePublicUuid,
        String courseName,
        String teacherFullName,
        UUID academicPeriodPublicUuid,
        PeriodType periodType,
        Integer periodOrdinal,
        String periodName,
        UUID templatePublicUuid,
        String templateName,
        EvaluationScale gradingScale,
        List<GradeBookCategoryEntry> categories,
        List<GradeBookStudentEntry> students,
        List<GradeBookEvaluationEntry> evaluations,
        List<GradeBookCellEntry> cells
) {
}
