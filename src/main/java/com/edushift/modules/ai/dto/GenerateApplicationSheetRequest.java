package com.edushift.modules.ai.dto;

import com.edushift.modules.sessions.learning.entity.ApplicationSheetKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * One call produces one worksheet for {@code grade}.
 * A second grade is a second request.
 */
public record GenerateApplicationSheetRequest(

		@NotNull ApplicationSheetKind kind,

		@NotBlank
		@Size(max = 80) String grade,

		@NotBlank
		@Size(max = 200) String courseName,

		@Size(max = 200) String topic,

		@NotBlank
		@Size(max = 200) String sessionTitle,

		@Size(max = 2000) String sessionSummary,

		@NotNull
		@Size(min = 1, max = 8) List<@Valid OutlineActivity> activities,

		List<@Size(max = 200) String> competencyNames,

		List<@Valid OutlineCriterion> evaluationCriteria
) {
	public record OutlineActivity(
			@Size(max = 40) String phase,
			@NotBlank @Size(max = 200) String name,
			@Size(max = 2000) String description
	) {
	}

	public record OutlineCriterion(
			@NotBlank @Size(max = 200) String name,
			@Size(max = 1000) String description
	) {
	}
}
