package com.edushift.modules.sessions.learning.dto;

import com.edushift.modules.sessions.learning.entity.SessionStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Full projection of a {@code LearningSession} (Sprint 5A / BE-5A.4).
 *
 * <p>Returned from single-row endpoints (GET / POST / PUT /
 * lifecycle). Carries the full content blob plus the competencies and
 * capacities references so the FE can render the detail screen without
 * any extra fetch.</p>
 */
public record LearningSessionResponse(

		UUID publicUuid,
		Long version,

		AssignmentRef assignment,
		UnitRef unit,

		String title,
		String objective,
		LocalDate scheduledDate,
		Integer durationMinutes,
		SessionStatus status,

		SessionContentDto content,

		List<CompetencyRef> competencies,
		List<CapacityRef> capacities,

		Instant startedAt,
		Instant endedAt,
		Instant cancelledAt,

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
			String name,
			LocalDate startDate,
			LocalDate endDate
	) {
	}

	public record UnitRef(
			UUID publicUuid,
			String name,
			Integer displayOrder
	) {
	}

	public record CompetencyRef(
			UUID publicUuid,
			String code,
			String name
	) {
	}

	public record CapacityRef(
			UUID publicUuid,
			String code,
			String name,
			UUID competencyPublicUuid
	) {
	}
}
