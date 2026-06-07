package com.edushift.modules.schedule.timeslot.dto;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Full projection of {@link com.edushift.modules.schedule.timeslot.entity.TimeSlot}
 * with the parent assignment summary so the FE can render breadcrumbs
 * (Teacher → Course → Section → Slot) without a second fetch.
 *
 * <p>Used by single-slot endpoints (GET / POST / PUT). For weekly grids
 * the schedule reverse views return a flat
 * {@link ScheduleSlotItem} which embeds richer cross-context data.</p>
 */
public record TimeSlotResponse(
		UUID publicUuid,
		AssignmentRef assignment,
		Short dayOfWeek,
		LocalTime startTime,
		LocalTime endTime,
		String classroom,
		Instant createdAt,
		Instant updatedAt
) {

	public record AssignmentRef(
			UUID publicUuid,
			TeacherRef teacher,
			CourseRef course,
			SectionRef section,
			PeriodRef period
	) {
	}

	public record TeacherRef(
			UUID publicUuid,
			String firstName,
			String lastName
	) {
	}

	public record CourseRef(
			UUID publicUuid,
			String code,
			String name
	) {
	}

	public record SectionRef(
			UUID publicUuid,
			String name
	) {
	}

	public record PeriodRef(
			UUID publicUuid,
			String periodType,
			Integer ordinal,
			String name
	) {
	}
}
