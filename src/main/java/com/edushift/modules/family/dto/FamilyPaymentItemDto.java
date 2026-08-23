package com.edushift.modules.family.dto;

import com.edushift.modules.payments.entity.Invoice;
import java.time.Instant;
import java.util.UUID;

public record FamilyPaymentItemDto(
		UUID publicUuid,
		String periodLabel,
		String currency,
		long totalCents,
		Invoice.Status status,
		Instant issuedAt,
		Instant dueAt,
		Instant paidAt
) {
}
