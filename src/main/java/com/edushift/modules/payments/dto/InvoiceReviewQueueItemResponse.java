package com.edushift.modules.payments.dto;

import com.edushift.modules.payments.entity.Invoice;
import com.edushift.modules.students.entity.DocumentType;
import java.time.Instant;
import java.util.UUID;

/** Minimal invoice data required to review a submitted payment voucher. */
public record InvoiceReviewQueueItemResponse(
		UUID publicUuid,
		UUID studentId,
		String periodLabel,
		String currency,
		long totalCents,
		Invoice.Status status,
		Instant dueAt,
		UUID evidenceFilePublicUuid,
		Instant evidenceSubmittedAt,
		String evidenceNotes,
		StudentIdentity studentIdentity
) {
	public record StudentIdentity(String fullName, DocumentType documentType, String documentNumber) {}
}
