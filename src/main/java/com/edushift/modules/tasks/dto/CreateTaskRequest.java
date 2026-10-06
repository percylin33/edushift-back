package com.edushift.modules.tasks.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * JSON body for {@code POST /sections/{uuid}/tasks}
 * (Sprint 7a / BE-7a.2 + V105 course scoping).
 */
public record CreateTaskRequest(
		@NotBlank @Size(max = 200) String title,
		@Size(max = 10000) String description,
		@Future Instant dueAt,
		UUID attachmentPublicUuid,
		@NotNull UUID teacherAssignmentPublicUuid,
		@NotNull @Min(1) @Max(1000) Integer maxScore,
		@JsonAlias("allowResubmissions") Boolean allowResubmission
) {
}
