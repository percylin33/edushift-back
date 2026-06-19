package com.edushift.modules.payments.dto;

import com.edushift.modules.payments.entity.InvoiceItem;
import java.util.UUID;

public record InvoiceItemResponse(
        UUID id,
        String description,
        int quantity,
        long unitAmountCents,
        long lineTotalCents
) {
    public static InvoiceItemResponse from(InvoiceItem it) {
        return new InvoiceItemResponse(
                it.getId(), it.getDescription(), it.getQuantity(),
                it.getUnitAmountCents(), it.getLineTotalCents()
        );
    }
}
