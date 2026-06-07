package com.edushift.modules.academic.competency.mapper;

import com.edushift.modules.academic.competency.dto.CompetencyListItem;
import com.edushift.modules.academic.competency.dto.CompetencyResponse;
import com.edushift.modules.academic.competency.dto.CompetencyResponse.CapacityRef;
import com.edushift.modules.academic.competency.dto.CompetencyResponse.CourseRef;
import com.edushift.modules.academic.competency.dto.CreateCompetencyRequest;
import com.edushift.modules.academic.competency.dto.UpdateCompetencyRequest;
import com.edushift.modules.academic.competency.entity.Capacity;
import com.edushift.modules.academic.competency.entity.Competency;
import com.edushift.modules.academic.course.entity.Course;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper for {@link Competency}. Same convention as the rest
 * of the codebase (no MapStruct).
 *
 * <p>Capacities are passed in explicitly so the service can decide how
 * to load them (single-fetch for detail, batched fetch for list).</p>
 */
@Component
public class CompetencyMapper {

	public CompetencyResponse toResponse(Competency competency, List<Capacity> capacities) {
		return new CompetencyResponse(
				competency.getPublicUuid(),
				toCourseRef(competency.getCourse()),
				competency.getCode(),
				competency.getName(),
				competency.getDescription(),
				competency.getDisplayOrder(),
				competency.getIsActive(),
				toCapacityRefs(capacities),
				competency.getCreatedAt(),
				competency.getUpdatedAt()
		);
	}

	public CompetencyListItem toListItem(Competency competency, long capacityCount) {
		return new CompetencyListItem(
				competency.getPublicUuid(),
				competency.getCode(),
				competency.getName(),
				competency.getDisplayOrder(),
				competency.getIsActive(),
				capacityCount
		);
	}

	public Competency fromCreate(CreateCompetencyRequest request, Course course,
			int displayOrder) {
		Competency competency = new Competency();
		competency.setCourse(course);
		competency.setCode(request.code());
		competency.setName(request.name());
		competency.setDescription(blankToNull(request.description()));
		competency.setDisplayOrder(displayOrder);
		competency.setIsActive(request.isActive() == null
				? Boolean.TRUE : request.isActive());
		return competency;
	}

	public void applyUpdate(UpdateCompetencyRequest patch, Competency competency) {
		if (patch.code() != null) {
			competency.setCode(patch.code());
		}
		if (patch.name() != null) {
			competency.setName(patch.name());
		}
		if (patch.description() != null) {
			competency.setDescription(blankToNull(patch.description()));
		}
		if (patch.isActive() != null) {
			competency.setIsActive(patch.isActive());
		}
	}

	private static CourseRef toCourseRef(Course course) {
		if (course == null) return null;
		return new CourseRef(
				course.getPublicUuid(),
				course.getCode(),
				course.getName());
	}

	private static List<CapacityRef> toCapacityRefs(List<Capacity> capacities) {
		if (capacities == null || capacities.isEmpty()) return List.of();
		return capacities.stream()
				.sorted(Comparator.comparing(Capacity::getDisplayOrder))
				.map(c -> new CapacityRef(
						c.getPublicUuid(),
						c.getCode(),
						c.getName(),
						c.getDisplayOrder(),
						c.getIsActive()))
				.toList();
	}

	private static String blankToNull(String value) {
		if (value == null) return null;
		return value.isBlank() ? null : value;
	}
}
