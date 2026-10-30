package com.edushift.modules.admissions.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record CompleteAdmissionEnrollmentRequest(
		@NotNull UUID sectionPublicUuid,
		@NotNull LocalDate enrolledAt,
		@Size(max = 1000) String notes
) {}
