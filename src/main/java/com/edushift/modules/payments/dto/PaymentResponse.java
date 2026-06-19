package com.edushift.modules.payments.dto;

import com.edushift.modules.payments.entity.Payment;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID publicUuid,
        UUID invoiceId,
        UUID guardianUserId,
        Payment.Provider provider,
        String externalId,
        Payment.Status status,
        long amountCents,
        String currency,
        String paymentMethod,
        Integer installments,
        Instant paidAt,
        String failureReason,
        Instant createdAt
) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
                p.getPublicUuid(), p.getInvoiceId(), p.getGuardianUserId(),
                p.getProvider(), p.getExternalId(), p.getStatus(),
                p.getAmountCents(), p.getCurrency(),
                p.getPaymentMethod(), p.getInstallments(),
                p.getPaidAt(), p.getFailureReason(), p.getCreatedAt()
        );
    }
}
