package com.edushift.modules.schedule.daytemplate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateDayTemplateRequest(
		@NotNull UUID yearUuid,
		@NotNull UUID levelUuid,
		UUID gradeUuid,
		@Size(max = 20) String shift,
		@NotBlank @Size(max = 120) String name,
		@Size(max = 80) String recessShareGroup
) {
}
