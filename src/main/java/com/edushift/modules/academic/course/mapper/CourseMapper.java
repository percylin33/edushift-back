package com.edushift.modules.academic.course.mapper;

import com.edushift.modules.academic.course.dto.CourseListItem;
import com.edushift.modules.academic.course.dto.CourseResponse;
import com.edushift.modules.academic.course.dto.CourseResponse.CourseLevelRef;
import com.edushift.modules.academic.course.dto.CreateCourseRequest;
import com.edushift.modules.academic.course.dto.UpdateCourseRequest;
import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.course.entity.CourseLevel;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper for {@link Course}. Same convention as the rest of
 * the codebase (no MapStruct).
 *
 * <p>Level associations are passed in explicitly so the service can
 * decide how to load them (single-fetch for detail, batched fetch for
 * list — see {@code CourseServiceImpl.listCourses}).</p>
 */
@Component
public class CourseMapper {

	public CourseResponse toResponse(Course course, List<CourseLevel> levels) {
		return new CourseResponse(
				course.getPublicUuid(),
				course.getCode(),
				course.getName(),
				course.getDescription(),
				course.getCredits(),
				course.getHoursPerWeek(),
				course.getIsActive(),
				toLevelRefs(levels),
				course.getCreatedAt(),
				course.getUpdatedAt()
		);
	}

	public CourseListItem toListItem(Course course, List<CourseLevel> levels) {
		return new CourseListItem(
				course.getPublicUuid(),
				course.getCode(),
				course.getName(),
				course.getCredits(),
				course.getHoursPerWeek(),
				course.getIsActive(),
				toLevelRefs(levels)
		);
	}

	public Course fromCreate(CreateCourseRequest request) {
		Course course = new Course();
		course.setCode(request.code());
		course.setName(request.name());
		course.setDescription(request.description());
		course.setCredits(request.credits());
		course.setHoursPerWeek(request.hoursPerWeek());
		course.setIsActive(request.isActive() == null ? Boolean.TRUE : request.isActive());
		return course;
	}

	public void applyUpdate(UpdateCourseRequest patch, Course course) {
		if (patch.code() != null) {
			course.setCode(patch.code());
		}
		if (patch.name() != null) {
			course.setName(patch.name());
		}
		if (patch.description() != null) {
			// blank string is a valid "clear" payload for description
			course.setDescription(patch.description().isBlank() ? null : patch.description());
		}
		if (patch.credits() != null) {
			course.setCredits(patch.credits());
		}
		if (patch.hoursPerWeek() != null) {
			course.setHoursPerWeek(patch.hoursPerWeek());
		}
		if (patch.isActive() != null) {
			course.setIsActive(patch.isActive());
		}
	}

	private static List<CourseLevelRef> toLevelRefs(List<CourseLevel> links) {
		if (links == null || links.isEmpty()) return List.of();
		return links.stream()
				.map(CourseLevel::getLevel)
				.filter(java.util.Objects::nonNull)
				.sorted(Comparator.comparing(AcademicLevel::getOrdinal))
				.map(level -> new CourseLevelRef(
						level.getPublicUuid(),
						level.getCode(),
						level.getName(),
						level.getOrdinal()))
				.toList();
	}
}
