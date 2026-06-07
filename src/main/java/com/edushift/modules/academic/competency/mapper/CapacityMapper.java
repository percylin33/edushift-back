package com.edushift.modules.academic.competency.mapper;

import com.edushift.modules.academic.competency.dto.CapacityResponse;
import com.edushift.modules.academic.competency.dto.CapacityResponse.CompetencyRef;
import com.edushift.modules.academic.competency.dto.CapacityResponse.CourseRef;
import com.edushift.modules.academic.competency.dto.CreateCapacityRequest;
import com.edushift.modules.academic.competency.dto.UpdateCapacityRequest;
import com.edushift.modules.academic.competency.entity.Capacity;
import com.edushift.modules.academic.competency.entity.Competency;
import com.edushift.modules.academic.course.entity.Course;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper for {@link Capacity}. No separate ListItem because
 * the projection is already lean (just {@code competency + course} refs).
 */
@Component
public class CapacityMapper {

	public CapacityResponse toResponse(Capacity capacity) {
		return new CapacityResponse(
				capacity.getPublicUuid(),
				toCompetencyRef(capacity.getCompetency()),
				capacity.getCode(),
				capacity.getName(),
				capacity.getDescription(),
				capacity.getDisplayOrder(),
				capacity.getIsActive(),
				capacity.getCreatedAt(),
				capacity.getUpdatedAt()
		);
	}

	public Capacity fromCreate(CreateCapacityRequest request, Competency competency,
			int displayOrder) {
		Capacity capacity = new Capacity();
		capacity.setCompetency(competency);
		capacity.setCode(request.code());
		capacity.setName(request.name());
		capacity.setDescription(blankToNull(request.description()));
		capacity.setDisplayOrder(displayOrder);
		capacity.setIsActive(request.isActive() == null
				? Boolean.TRUE : request.isActive());
		return capacity;
	}

	public void applyUpdate(UpdateCapacityRequest patch, Capacity capacity) {
		if (patch.code() != null) {
			capacity.setCode(patch.code());
		}
		if (patch.name() != null) {
			capacity.setName(patch.name());
		}
		if (patch.description() != null) {
			capacity.setDescription(blankToNull(patch.description()));
		}
		if (patch.isActive() != null) {
			capacity.setIsActive(patch.isActive());
		}
	}

	private static CompetencyRef toCompetencyRef(Competency competency) {
		if (competency == null) return null;
		Course course = competency.getCourse();
		CourseRef courseRef = course == null ? null : new CourseRef(
				course.getPublicUuid(),
				course.getCode(),
				course.getName());
		return new CompetencyRef(
				competency.getPublicUuid(),
				competency.getCode(),
				competency.getName(),
				courseRef);
	}

	private static String blankToNull(String value) {
		if (value == null) return null;
		return value.isBlank() ? null : value;
	}
}
