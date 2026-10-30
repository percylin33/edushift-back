package com.edushift.modules.payments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectPaymentEvidenceRequest(
		@NotBlank @Size(min = 4, max = 500) String reason
) {
}
