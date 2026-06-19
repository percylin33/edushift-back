package com.edushift.modules.payments.dto;

import com.edushift.modules.payments.entity.Invoice;
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
        List<InvoiceItemResponse> items
) {
    public static InvoiceResponse from(Invoice i, List<InvoiceItemResponse> items) {
        return new InvoiceResponse(
                i.getPublicUuid(), i.getSubscriptionId(), i.getStudentId(),
                i.getGuardianUserId(), i.getPeriodLabel(), i.getCurrency(),
                i.getSubtotalCents(), i.getDiscountCents(), i.getTaxCents(),
                i.getTotalCents(), i.getStatus(), i.getIssuedAt(), i.getDueAt(),
                i.getPaidAt(), i.getNotes(), items
        );
    }
}
