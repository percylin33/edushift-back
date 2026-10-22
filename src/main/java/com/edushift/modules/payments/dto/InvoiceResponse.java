package com.edushift.modules.payments.dto;

import com.edushift.modules.payments.entity.Invoice;
import com.edushift.modules.students.entity.DocumentType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(
        UUID publicUuid,
        UUID subscriptionId,
        UUID studentId,
        UUID guardianUserId,
        String periodLabel,
        String currency,
        long subtotalCents,
        long discountCents,
        long taxCents,
        long totalCents,
        Invoice.Status status,
        Instant issuedAt,
        Instant dueAt,
        Instant paidAt,
        String notes,
        UUID evidenceFilePublicUuid,
        Instant evidenceSubmittedAt,
        String evidenceNotes,
        String evidenceRejectionReason,
        long moraCents,
        boolean moraExempt,
        List<InvoiceItemResponse> items,
        StudentIdentity studentIdentity
) {
	public record StudentIdentity(String fullName, DocumentType documentType, String documentNumber) {}

    public static InvoiceResponse from(Invoice i, List<InvoiceItemResponse> items) {
        return new InvoiceResponse(
                i.getPublicUuid(), i.getSubscriptionId(), i.getStudentId(),
                i.getGuardianUserId(), i.getPeriodLabel(), i.getCurrency(),
                i.getSubtotalCents(), i.getDiscountCents(), i.getTaxCents(),
                i.getTotalCents(), i.getStatus(), i.getIssuedAt(), i.getDueAt(),
                i.getPaidAt(), i.getNotes(),
                i.getEvidenceFilePublicUuid(),
                i.getEvidenceSubmittedAt(),
                i.getEvidenceNotes(),
                i.getEvidenceRejectionReason(),
                i.getMoraCents(),
                i.isMoraExempt(),
                items,
                null
        );
    }

	public InvoiceResponse withStudentIdentity(StudentIdentity identity) {
		return new InvoiceResponse(
				publicUuid, subscriptionId, studentId, guardianUserId, periodLabel,
				currency, subtotalCents, discountCents, taxCents, totalCents,
				status, issuedAt, dueAt, paidAt, notes, evidenceFilePublicUuid,
				evidenceSubmittedAt, evidenceNotes, evidenceRejectionReason,
				moraCents, moraExempt, items, identity);
	}
}
