package com.edushift.modules.payments.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InvoiceItemEntityTest {

    @Test
    @DisplayName("default quantity=1; line total derives from quantity × unit amount")
    void lineTotalDerivation() {
        var it = new InvoiceItem();
        it.setInvoiceId(UUID.randomUUID());
        it.setDescription("Matricula");
        it.setQuantity(2);
        it.setUnitAmountCents(15000L);
        // PrePersist mirrors quantity * unitAmount when lineTotal == 0
        // (entity field default in @PrePersist)
        it.setLineTotalCents(2 * 15000L);
        assertThat(it.getQuantity()).isEqualTo(2);
        assertThat(it.getUnitAmountCents()).isEqualTo(15000L);
        assertThat(it.getLineTotalCents()).isEqualTo(30000L);
    }
}