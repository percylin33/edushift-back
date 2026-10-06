package com.edushift.modules.evaluations.graderegister.mapper;

import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.graderegister.dto.GradeCategoryResponse;
import com.edushift.modules.evaluations.graderegister.dto.GradeRegisterTemplateListItem;
import com.edushift.modules.evaluations.graderegister.dto.GradeRegisterTemplateResponse;
import com.edushift.modules.evaluations.graderegister.entity.GradeCategory;
import com.edushift.modules.evaluations.graderegister.entity.GradeCategoryColumnMode;
import com.edushift.modules.evaluations.graderegister.entity.GradeRegisterTemplate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GradeRegisterTemplateMapper {

	public GradeCategoryResponse toCategoryResponse(GradeCategory c) {
		return new GradeCategoryResponse(
				c.getPublicUuid(),
				c.getName(),
				c.getColorToken(),
				c.getSortOrder(),
				c.getWeight(),
				Boolean.TRUE.equals(c.getContributesToAverage()),
				Boolean.TRUE.equals(c.getAllowsTeacherColumns()),
				c.getColumnMode() != null ? c.getColumnMode() : GradeCategoryColumnMode.OPEN,
				c.getSlotCount(),
				c.getSlotLabelPrefix(),
				c.getSlotLabels());
	}

	public GradeRegisterTemplateResponse toResponse(
			GradeRegisterTemplate t,
			List<GradeCategory> categories
	) {
		var year = t.getAcademicYear();
		var course = t.getCourse();
		var level = t.getAcademicLevel();
		var source = t.getSourceTemplate();
		return new GradeRegisterTemplateResponse(
				t.getPublicUuid(),
				t.getName(),
				t.getDescription(),
				t.getScope(),
				Boolean.TRUE.equals(t.getIsSystem()),
				Boolean.TRUE.equals(t.getIsDefault()),
				t.getCatalogCode(),
				year != null ? year.getPublicUuid() : null,
				year != null ? year.getName() : null,
				course != null ? course.getPublicUuid() : null,
				course != null ? course.getName() : null,
				level != null ? level.getPublicUuid() : null,
				level != null ? level.getName() : null,
				level != null ? level.getCode() : null,
				source != null ? source.getPublicUuid() : null,
				categories.stream().map(this::toCategoryResponse).toList(),
				levelsOf(t),
				t.getCreatedAt(),
				t.getUpdatedAt(),
				scaleOf(t));
	}

	public GradeRegisterTemplateListItem toListItem(
			GradeRegisterTemplate t,
			int categoryCount
	) {
		var year = t.getAcademicYear();
		var level = t.getAcademicLevel();
		var source = t.getSourceTemplate();
		return new GradeRegisterTemplateListItem(
				t.getPublicUuid(),
				t.getName(),
				t.getDescription(),
				t.getScope(),
				Boolean.TRUE.equals(t.getIsSystem()),
				Boolean.TRUE.equals(t.getIsDefault()),
				t.getCatalogCode(),
				year != null ? year.getPublicUuid() : null,
				categoryCount,
				levelsOf(t),
				level != null ? level.getPublicUuid() : null,
				level != null ? level.getName() : null,
				level != null ? level.getCode() : null,
				source != null ? source.getPublicUuid() : null,
				scaleOf(t));
	}

	private static EvaluationScale scaleOf(GradeRegisterTemplate t) {
		return t.getGradingScale() != null ? t.getGradingScale() : EvaluationScale.SCORE_0_20;
	}

	private static List<String> levelsOf(GradeRegisterTemplate t) {
		return t.getRecommendedLevels() != null ? List.copyOf(t.getRecommendedLevels()) : List.of();
	}
}
