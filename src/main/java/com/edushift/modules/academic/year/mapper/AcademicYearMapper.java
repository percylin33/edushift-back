package com.edushift.modules.academic.year.mapper;

import com.edushift.modules.academic.year.dto.AcademicYearListItem;
import com.edushift.modules.academic.year.dto.AcademicYearResponse;
import com.edushift.modules.academic.year.dto.CreateAcademicYearRequest;
import com.edushift.modules.academic.year.dto.UpdateAcademicYearRequest;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.academic.year.entity.AcademicYearStatus;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper for the academic year aggregate.
 *
 * <p>Same convention as the rest of the codebase (see {@code TenantMapper}'s
 * Javadoc): no MapStruct, plain Java methods kept close to the data.</p>
 *
 * <h3>Update semantics</h3>
 * {@link #applyUpdate(UpdateAcademicYearRequest, AcademicYear)} mutates the
 * entity in-place. Null fields in the patch are skipped (no clearing).
 */
@Component
public class AcademicYearMapper {

	public AcademicYearResponse toResponse(AcademicYear entity) {
		return new AcademicYearResponse(
				entity.getPublicUuid(),
				entity.getName(),
				entity.getStatus(),
				entity.getStartDate(),
				entity.getEndDate(),
				entity.getCreatedAt(),
				entity.getUpdatedAt()
		);
	}

	public AcademicYearListItem toListItem(AcademicYear entity) {
		return new AcademicYearListItem(
				entity.getPublicUuid(),
				entity.getName(),
				entity.getStatus(),
				entity.getStartDate(),
				entity.getEndDate()
		);
	}

	public AcademicYear fromCreate(CreateAcademicYearRequest request) {
		AcademicYear year = new AcademicYear();
		year.setName(request.name());
		year.setStartDate(request.startDate());
		year.setEndDate(request.endDate());
		year.setStatus(AcademicYearStatus.PLANNING);
		return year;
	}

	public void applyUpdate(UpdateAcademicYearRequest patch, AcademicYear year) {
		if (patch.name() != null) {
			year.setName(patch.name().trim());
		}
		if (patch.startDate() != null) {
			year.setStartDate(patch.startDate());
		}
		if (patch.endDate() != null) {
			year.setEndDate(patch.endDate());
		}
	}
}
