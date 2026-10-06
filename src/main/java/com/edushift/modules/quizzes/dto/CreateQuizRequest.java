package com.edushift.modules.quizzes.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JSON body for {@code POST /sections/{uuid}/quizzes}
 * (Sprint 7b / BE-7b.1 + V105).
 */
public record CreateQuizRequest(
		@NotBlank @Size(max = 200) String title,
		@Size(max = 10000) String description,
		@Future Instant dueAt,
		@Min(1) @Max(480) Integer timeLimitMinutes,
		@NotNull @Min(1) @Max(10) Integer maxAttempts,
		@NotNull @Min(0) @Max(1000) Integer maxScore,
		List<CreateQuestionRequest> questions,
		@NotNull UUID teacherAssignmentPublicUuid
) {
}
