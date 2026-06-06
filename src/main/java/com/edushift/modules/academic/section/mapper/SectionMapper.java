package com.edushift.modules.academic.section.mapper;

import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.section.dto.CreateSectionRequest;
import com.edushift.modules.academic.section.dto.SectionListItem;
import com.edushift.modules.academic.section.dto.SectionResponse;
import com.edushift.modules.academic.section.dto.UpdateSectionRequest;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.year.entity.AcademicYear;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper for {@link Section}. Same convention as the rest
 * of the codebase (no MapStruct).
 *
 * <p>The mapper assumes the entity's parent associations
 * ({@code academicYear}, {@code grade}, {@code grade.level}) are
 * <em>initialised</em> before mapping. The service layer takes care of
 * loading them eagerly inside the same transaction.</p>
 */
@Component
public class SectionMapper {

	public SectionResponse toResponse(Section section) {
		AcademicYear year = section.getAcademicYear();
		Grade grade = section.getGrade();
		AcademicLevel level = grade != null ? grade.getLevel() : null;

		return new SectionResponse(
				section.getPublicUuid(),
				year != null ? year.getPublicUuid() : null,
				year != null ? year.getName() : null,
				year != null && year.getStatus() != null ? year.getStatus().name() : null,
				grade != null ? grade.getPublicUuid() : null,
				grade != null ? grade.getName() : null,
				grade != null ? grade.getOrdinal() : null,
				level != null ? level.getPublicUuid() : null,
				level != null ? level.getCode() : null,
				level != null ? level.getName() : null,
				section.getName(),
				section.getCapacity(),
				section.getDisplayOrder(),
				section.getCreatedAt(),
				section.getUpdatedAt()
		);
	}

	public SectionListItem toListItem(Section section) {
		AcademicYear year = section.getAcademicYear();
		Grade grade = section.getGrade();
		AcademicLevel level = grade != null ? grade.getLevel() : null;

		return new SectionListItem(
				section.getPublicUuid(),
				year != null ? year.getPublicUuid() : null,
				year != null ? year.getName() : null,
				year != null && year.getStatus() != null ? year.getStatus().name() : null,
				grade != null ? grade.getPublicUuid() : null,
				grade != null ? grade.getName() : null,
				grade != null ? grade.getOrdinal() : null,
				level != null ? level.getPublicUuid() : null,
				level != null ? level.getCode() : null,
				section.getName(),
				section.getCapacity(),
				section.getDisplayOrder()
		);
	}

	public Section fromCreate(CreateSectionRequest request, AcademicYear year, Grade grade) {
		Section section = new Section();
		section.setAcademicYear(year);
		section.setGrade(grade);
		section.setName(request.name());
		section.setCapacity(request.capacity());
		section.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : 1);
		return section;
	}

	public void applyUpdate(UpdateSectionRequest patch, Section section) {
		if (patch.name() != null) {
			section.setName(patch.name().trim());
		}
		if (patch.capacity() != null) {
			section.setCapacity(patch.capacity());
		}
		if (patch.displayOrder() != null) {
			section.setDisplayOrder(patch.displayOrder());
		}
	}
}
