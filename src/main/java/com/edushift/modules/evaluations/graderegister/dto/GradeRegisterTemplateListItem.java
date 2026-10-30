package com.edushift.modules.evaluations.graderegister.dto;

import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.graderegister.entity.GradeRegisterTemplateScope;
import java.util.List;
import java.util.UUID;

public record GradeRegisterTemplateListItem(
		UUID publicUuid,
		String name,
		String description,
		GradeRegisterTemplateScope scope,
		boolean system,
		boolean isDefault,
		String catalogCode,
		UUID academicYearPublicUuid,
		int categoryCount,
		List<String> recommendedLevels,
		UUID levelPublicUuid,
		String levelName,
		String levelCode,
		UUID sourceTemplatePublicUuid,
		EvaluationScale gradingScale
) {
}
