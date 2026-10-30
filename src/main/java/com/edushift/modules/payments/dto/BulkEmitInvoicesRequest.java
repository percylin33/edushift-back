package com.edushift.modules.payments.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Admin bulk invoice emission. Provide {@code sectionPublicUuid} OR set
 * {@code allEnrolled=true}. Use {@code dryRun=true} to preview counts.
 */
public record BulkEmitInvoicesRequest(
		UUID sectionPublicUuid,
		boolean allEnrolled,
		UUID conceptPublicUuid,
		@NotBlank @Size(max = 40) String periodLabel,
		@Min(0) Long amountCents,
		@Min(0) long discountCents,
		Instant dueAt,
		@Size(max = 2000) String notes,
		boolean dryRun
) {
	public BulkEmitInvoicesRequest {
		if (discountCents < 0) {
			discountCents = 0;
		}
	}
}
