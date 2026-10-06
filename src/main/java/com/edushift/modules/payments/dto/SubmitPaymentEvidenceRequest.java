package com.edushift.modules.payments.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Parent submits a voucher file already uploaded via {@code POST /files?module=payments}.
 */
public record SubmitPaymentEvidenceRequest(
		@NotNull UUID filePublicUuid,
		@Size(max = 500) String notes
) {
}
