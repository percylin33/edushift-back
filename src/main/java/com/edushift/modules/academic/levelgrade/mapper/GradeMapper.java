package com.edushift.modules.academic.levelgrade.mapper;

import com.edushift.modules.academic.levelgrade.dto.CreateGradeRequest;
import com.edushift.modules.academic.levelgrade.dto.GradeResponse;
import com.edushift.modules.academic.levelgrade.dto.UpdateGradeRequest;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper for {@link Grade}. Same convention as the rest of
 * the codebase (no MapStruct).
 */
@Component
public class GradeMapper {

	public GradeResponse toResponse(Grade grade) {
		return new GradeResponse(
				grade.getPublicUuid(),
				grade.getLevel() != null ? grade.getLevel().getPublicUuid() : null,
				grade.getName(),
				grade.getOrdinal(),
				grade.getCreatedAt(),
				grade.getUpdatedAt()
		);
	}

	public Grade fromCreate(CreateGradeRequest request, AcademicLevel level) {
		Grade grade = new Grade();
		grade.setLevel(level);
		grade.setName(request.name());
		grade.setOrdinal(request.ordinal());
		return grade;
	}

	public void applyUpdate(UpdateGradeRequest patch, Grade grade) {
		if (patch.name() != null) {
			grade.setName(patch.name().trim());
		}
		if (patch.ordinal() != null) {
			grade.setOrdinal(patch.ordinal());
		}
	}
}
