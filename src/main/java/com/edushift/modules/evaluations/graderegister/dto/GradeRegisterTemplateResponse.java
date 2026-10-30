package com.edushift.modules.evaluations.graderegister.dto;

import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.graderegister.entity.GradeRegisterTemplateScope;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GradeRegisterTemplateResponse(
		UUID publicUuid,
		String name,
		String description,
		GradeRegisterTemplateScope scope,
		boolean system,
		boolean isDefault,
		String catalogCode,
		UUID academicYearPublicUuid,
		String academicYearName,
		UUID coursePublicUuid,
		String courseName,
		UUID levelPublicUuid,
		String levelName,
		String levelCode,
		UUID sourceTemplatePublicUuid,
		List<GradeCategoryResponse> categories,
		List<String> recommendedLevels,
		Instant createdAt,
		Instant updatedAt,
		EvaluationScale gradingScale
) {
}
