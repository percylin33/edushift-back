package com.edushift.modules.payments.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Admin request to issue one tuition/fee invoice for a student.
 */
public record EmitInvoiceRequest(
		@NotNull UUID studentPublicUuid,
		UUID conceptPublicUuid,
		@NotBlank @Size(max = 40) String periodLabel,
		@Min(0) long amountCents,
		@Min(0) long discountCents,
		Instant dueAt,
		@Size(max = 2000) String notes
) {
	public EmitInvoiceRequest {
		if (discountCents < 0) {
			discountCents = 0;
		}
	}
}
