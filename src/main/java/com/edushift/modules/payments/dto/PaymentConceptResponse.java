package com.edushift.modules.payments.dto;

import com.edushift.modules.payments.entity.PaymentConcept;
import java.time.Instant;
import java.util.UUID;

/**
 * Response body for a {@link PaymentConcept} row.
 */
public record PaymentConceptResponse(
		UUID publicUuid,
		String code,
		String name,
		String description,
		PaymentConcept.Category category,
		long defaultAmountCents,
		String currency,
		boolean recurring,
		boolean active,
		int sortOrder,
		Instant createdAt,
		Instant updatedAt
) {
	public static PaymentConceptResponse from(PaymentConcept c) {
		return new PaymentConceptResponse(
				c.getPublicUuid(),
				c.getCode(),
				c.getName(),
				c.getDescription(),
				c.getCategory(),
				c.getDefaultAmountCents(),
				c.getCurrency(),
				c.isRecurring(),
				c.isActive(),
				c.getSortOrder(),
				c.getCreatedAt(),
				c.getUpdatedAt());
	}
}