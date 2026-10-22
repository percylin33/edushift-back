package com.edushift.modules.admissions.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record LinkAdmissionEnrollmentRequest(
		@NotNull UUID enrollmentPublicUuid,
		@Size(max = 2000) String note
) {}
