package com.edushift.modules.academic.levelgrade.mapper;

import com.edushift.modules.academic.levelgrade.dto.CreateGradeRequest;
import com.edushift.modules.academic.levelgrade.dto.GradeResponse;
import com.edushift.modules.academic.levelgrade.dto.UpdateGradeRequest;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.levelgrade.entity.TeachingMode;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper for {@link Grade}.
 */
@Component
public class GradeMapper {

	public GradeResponse toResponse(Grade grade) {
		return new GradeResponse(
				grade.getPublicUuid(),
				grade.getLevel() != null ? grade.getLevel().getPublicUuid() : null,
				grade.getName(),
				grade.getOrdinal(),
				grade.getTeachingMode() != null
						? grade.getTeachingMode()
						: TeachingMode.POLIDOCENTE,
				grade.getCreatedAt(),
				grade.getUpdatedAt()
		);
	}

	public Grade fromCreate(CreateGradeRequest request, AcademicLevel level) {
		Grade grade = new Grade();
		grade.setLevel(level);
		grade.setName(request.name());
		grade.setOrdinal(request.ordinal());
		grade.setTeachingMode(request.teachingMode() != null
				? request.teachingMode()
				: TeachingMode.POLIDOCENTE);
		return grade;
	}

	public void applyUpdate(UpdateGradeRequest patch, Grade grade) {
		if (patch.name() != null) {
			grade.setName(patch.name().trim());
		}
		if (patch.ordinal() != null) {
			grade.setOrdinal(patch.ordinal());
		}
		if (patch.teachingMode() != null) {
			grade.setTeachingMode(patch.teachingMode());
		}
	}
}
