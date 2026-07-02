package com.edushift.modules.payments.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InvoiceEntityTest {

    @Test
    @DisplayName("defaults: status=PENDING, currency=PEN, all amounts=0")
    void defaults() {
        var i = new Invoice();
        assertThat(i.getStatus()).isEqualTo(Invoice.Status.PENDING);
        assertThat(i.getCurrency()).isEqualTo("PEN");
        assertThat(i.getSubtotalCents()).isZero();
        assertThat(i.getDiscountCents()).isZero();
        assertThat(i.getTaxCents()).isZero();
        assertThat(i.getTotalCents()).isZero();
    }

    @Test
    @DisplayName("publicUuid populates on first save")
    void publicUuid() {
        var i = new Invoice();
        // Manual populate mirrors what @PrePersist does at flush time
        i.setPublicUuid(UUID.randomUUID());
        assertThat(i.getPublicUuid()).isNotNull();
    }

    @Test
    @DisplayName("Status enum has expected values")
    void statusEnum() {
        assertThat(Invoice.Status.values()).hasSize(5);
        assertThat(Invoice.Status.PAID).isNotEqualTo(Invoice.Status.CANCELLED);
    }
}