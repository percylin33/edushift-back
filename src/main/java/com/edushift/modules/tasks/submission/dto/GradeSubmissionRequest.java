package com.edushift.modules.tasks.submission.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * PATCH body for grading a submission
 * (Sprint 7a / BE-7a.2).
 */
public record GradeSubmissionRequest(
		@NotNull @Min(0) @Max(100) Integer grade,
		@Size(max = 2000) String feedback
) {
}
