package com.edushift.modules.schedule.timeslot.dto;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Flat row of the weekly grid returned by the two reverse views:
 *
 * <ul>
 *   <li>{@code GET /v1/teachers/{teacherUuid}/schedule?periodId=...}</li>
 *   <li>{@code GET /v1/academic/sections/{sectionUuid}/schedule?periodId=...}</li>
 * </ul>
 *
 * <p>Each item carries every cross-context label the FE needs to render
 * a single cell of a Mon..Sun × HH:MM grid without another network
 * round-trip. Sort order is {@code (dayOfWeek asc, startTime asc)},
 * applied at the service.</p>
 *
 * <p>The {@code teacher} block is {@code null} on the teacher-centric
 * view (redundant — the caller is the teacher) and the {@code section}
 * block is {@code null} on the section-centric view, by symmetry. This
 * keeps the FE Strict-mode happy without forcing two distinct payload
 * shapes.</p>
 */
public record ScheduleSlotItem(
		UUID slotPublicUuid,
		UUID assignmentPublicUuid,
		Short dayOfWeek,
		LocalTime startTime,
		LocalTime endTime,
		String classroom,
		TeacherRef teacher,
		CourseRef course,
		SectionRef section,
		PeriodRef period
) {

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
