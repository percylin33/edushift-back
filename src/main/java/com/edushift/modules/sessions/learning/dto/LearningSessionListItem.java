package com.edushift.modules.sessions.learning.dto;

import com.edushift.modules.sessions.learning.entity.SessionStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lean projection used by every list endpoint: filtered list, by-unit
 * reverse view, and by-assignment reverse view (Sprint 5A / BE-5A.4).
 *
 * <p>Carries enough to render a row without a second fetch (teacher
 * name + course code + unit name) but does NOT carry {@code content}
 * or the competencies / capacities sets. The full
 * {@link LearningSessionResponse} is only loaded on the detail view.</p>
 */
public record LearningSessionListItem(

		UUID publicUuid,
		Long version,
		String title,
		LocalDate scheduledDate,
		Integer durationMinutes,
		SessionStatus status,
		Instant startedAt,
		Instant endedAt,
		Instant cancelledAt,

		AssignmentSummary assignment,
		UnitSummary unit,

		Instant createdAt,
		Instant updatedAt
) {

	public record AssignmentSummary(
			UUID publicUuid,
			String teacherName,
			String courseCode,
			String sectionName
	) {
	}

	public record UnitSummary(
			UUID publicUuid,
			String name,
			Integer displayOrder
	) {
	}
}
