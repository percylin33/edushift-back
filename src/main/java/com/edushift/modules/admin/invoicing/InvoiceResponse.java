package com.edushift.modules.admin.invoicing;

import com.edushift.modules.admin.invoicing.B2BInvoice.B2BInvoiceStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        UUID tenantId,
        UUID subscriptionId,
        LocalDate periodStart,
        LocalDate periodEnd,
        int activeStudentCount,
        int pricePerStudentCents,
        int subtotalCents,
        int discountCents,
        int totalCents,
        B2BInvoiceStatus status,
        Instant issuedAt,
        LocalDate dueAt,
        Instant paidAt,
        String notes
) {

    static InvoiceResponse from(B2BInvoice inv) {
        return new InvoiceResponse(
                inv.getId(), inv.getTenantId(), inv.getSubscriptionId(),
                inv.getPeriodStart(), inv.getPeriodEnd(),
                inv.getActiveStudentCount(), inv.getPricePerStudentCents(),
                inv.getSubtotalCents(), inv.getDiscountCents(), inv.getTotalCents(),
                inv.getStatus(), inv.getIssuedAt(), inv.getDueAt(), inv.getPaidAt(),
                inv.getNotes());
    }
}
