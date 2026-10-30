package com.edushift.modules.sessions.learning.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Plan-only view of a session shared with the grade.
 * Observations, attendance and student data are omitted.
 */
public record SharedLearningSessionResponse(

		UUID publicUuid,
		String title,
		String objective,
		LocalDate scheduledDate,
		Integer durationMinutes,
		List<String> activities,
		List<String> materials,
		List<String> competencies,
		String authorName,
		String sectionName,
		String gradeName,
		String courseCode,
		UUID coursePublicUuid,
		UUID periodPublicUuid
) {
}
