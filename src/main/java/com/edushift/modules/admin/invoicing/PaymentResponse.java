package com.edushift.modules.admin.invoicing;

import com.edushift.modules.admin.invoicing.B2BPayment.PaymentMethod;
import com.edushift.modules.admin.invoicing.B2BPayment.PaymentStatus;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID invoiceId,
        UUID tenantId,
        int amountCents,
        PaymentMethod paymentMethod,
        PaymentStatus status,
        String externalRef,
        Instant paidAt,
        String notes
) {

    static PaymentResponse from(B2BPayment payment) {
        return new PaymentResponse(
                payment.getId(), payment.getInvoiceId(), payment.getTenantId(),
                payment.getAmountCents(), payment.getPaymentMethod(), payment.getStatus(),
                payment.getExternalRef(), payment.getPaidAt(), payment.getNotes());
    }
}
