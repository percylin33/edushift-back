package com.edushift.modules.admin.invoicing;

import com.edushift.modules.admin.invoicing.B2BPayment.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record CreatePaymentRequest(

        @NotNull UUID invoiceId,

        @NotNull @Positive int amountCents,

        @NotNull PaymentMethod method,

        String externalRef,

        String notes
) {}
