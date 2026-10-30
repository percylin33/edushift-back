package com.edushift.modules.privacy.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateConsentRequest(
		@NotNull(message = "studentPublicUuid es obligatorio")
		UUID studentPublicUuid,

		@NotNull(message = "consentGranted es obligatorio")
		Boolean consentGranted,

		@NotBlank @Size(max = 20) String policyVersion,

		@NotBlank @Size(max = 80) String purposeCode,

		@NotBlank @Size(max = 12000) String noticeSnapshot
) {}
