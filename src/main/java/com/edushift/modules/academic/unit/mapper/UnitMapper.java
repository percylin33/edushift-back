package com.edushift.modules.academic.unit.mapper;

import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.unit.dto.CreateUnitRequest;
import com.edushift.modules.academic.unit.dto.UnitListItem;
import com.edushift.modules.academic.unit.dto.UnitResponse;
import com.edushift.modules.academic.unit.dto.UpdateUnitRequest;
import com.edushift.modules.academic.unit.entity.Unit;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper for {@link Unit}. Same convention as the rest of
 * the codebase (no MapStruct).
 *
 * <p>The {@code sessionCount} is passed in explicitly so the service can
 * choose a single bulk lookup (list path) or a per-row count (detail
 * path). Wiring lives in {@link com.edushift.modules.academic.unit.service.UnitService}.</p>
 */
@Component
public class UnitMapper {

	public UnitResponse toResponse(Unit unit, long sessionCount) {
		return new UnitResponse(
				unit.getPublicUuid(),
				toCourseRef(unit.getCourse()),
				unit.getName(),
				unit.getDescription(),
				unit.getDisplayOrder(),
				unit.getStartDate(),
				unit.getEndDate(),
				unit.getIsActive(),
				sessionCount,
				unit.getCreatedAt(),
				unit.getUpdatedAt()
		);
	}

	public UnitListItem toListItem(Unit unit, long sessionCount) {
		return new UnitListItem(
				unit.getPublicUuid(),
				unit.getName(),
				unit.getDisplayOrder(),
				unit.getStartDate(),
				unit.getEndDate(),
				unit.getIsActive(),
				sessionCount
		);
	}

	public Unit fromCreate(CreateUnitRequest request, Course course, int displayOrder) {
		Unit unit = new Unit();
		unit.setCourse(course);
		unit.setName(request.name());
		unit.setDescription(blankToNull(request.description()));
		unit.setDisplayOrder(displayOrder);
		unit.setStartDate(request.startDate());
		unit.setEndDate(request.endDate());
		unit.setIsActive(request.isActive() == null ? Boolean.TRUE : request.isActive());
		return unit;
	}

	public void applyUpdate(UpdateUnitRequest patch, Unit unit) {
		if (patch.name() != null) {
			unit.setName(patch.name());
		}
		if (patch.description() != null) {
			unit.setDescription(blankToNull(patch.description()));
		}
		if (patch.startDate() != null) {
			unit.setStartDate(patch.startDate());
		}
		if (patch.endDate() != null) {
			unit.setEndDate(patch.endDate());
		}
		if (patch.isActive() != null) {
			unit.setIsActive(patch.isActive());
		}
	}

	private static UnitResponse.CourseRef toCourseRef(Course course) {
		if (course == null) return null;
		return new UnitResponse.CourseRef(
				course.getPublicUuid(),
				course.getCode(),
				course.getName());
	}

	private static String blankToNull(String value) {
		if (value == null) return null;
		return value.isBlank() ? null : value;
	}
}
