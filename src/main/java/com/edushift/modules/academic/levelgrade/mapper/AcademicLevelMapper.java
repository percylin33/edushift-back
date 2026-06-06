package com.edushift.modules.academic.levelgrade.mapper;

import com.edushift.modules.academic.levelgrade.dto.AcademicLevelResponse;
import com.edushift.modules.academic.levelgrade.dto.CreateAcademicLevelRequest;
import com.edushift.modules.academic.levelgrade.dto.GradeResponse;
import com.edushift.modules.academic.levelgrade.dto.UpdateAcademicLevelRequest;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper for {@link AcademicLevel}.
 */
@Component
@RequiredArgsConstructor
public class AcademicLevelMapper {

	private final GradeMapper gradeMapper;

	public AcademicLevelResponse toResponse(AcademicLevel level, List<Grade> grades) {
		List<GradeResponse> gradeProjections = (grades == null) ? List.of()
				: grades.stream()
						.sorted(Comparator.comparing(Grade::getOrdinal))
						.map(gradeMapper::toResponse)
						.toList();
		return new AcademicLevelResponse(
				level.getPublicUuid(),
				level.getCode(),
				level.getName(),
				level.getOrdinal(),
				gradeProjections,
				level.getCreatedAt(),
				level.getUpdatedAt()
		);
	}

	public AcademicLevel fromCreate(CreateAcademicLevelRequest request) {
		AcademicLevel level = new AcademicLevel();
		level.setCode(request.code());
		level.setName(request.name().trim());
		level.setOrdinal(request.ordinal());
		return level;
	}

	public void applyUpdate(UpdateAcademicLevelRequest patch, AcademicLevel level) {
		if (patch.code() != null) {
			level.setCode(patch.code());
		}
		if (patch.name() != null) {
			level.setName(patch.name().trim());
		}
		if (patch.ordinal() != null) {
			level.setOrdinal(patch.ordinal());
		}
	}
}
