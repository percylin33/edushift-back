package com.edushift.modules.sessions.learning.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * API representation of the free-form pedagogical content of a
 * {@code LearningSession} (Sprint 5A / BE-5A.4).
 *
 * <p>Mirrors the shape of the persisted JSON blob (see
 * {@link com.edushift.modules.sessions.learning.entity.SessionContent}).
 * Lists are accepted as null (treated as empty) on the wire to keep
 * payloads compact.</p>
 */
public record SessionContentDto(

		@Size(max = 1000, message = "objective too long")
		String objective,

		List<@Size(max = 500, message = "activity too long") String> activities,

		List<@Size(max = 200, message = "material too long") String> materials,

		@Size(max = 2000, message = "observations too long")
		String observations
) {
}
