package com.edushift.modules.payments.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentEntityTest {

    @Test
    @DisplayName("defaults: provider=MERCADOPAGO, status=PENDING, currency=PEN")
    void defaults() {
        var p = new Payment();
        assertThat(p.getProvider()).isEqualTo(Payment.Provider.MERCADOPAGO);
        assertThat(p.getStatus()).isEqualTo(Payment.Status.PENDING);
        assertThat(p.getCurrency()).isEqualTo("PEN");
        assertThat(p.getRawResponse()).isEqualTo("{}");
    }

    @Test
    @DisplayName("amount + provider + lifecycle fields are settable")
    void setters() {
        var p = new Payment();
        p.setPublicUuid(UUID.randomUUID());
        p.setInvoiceId(UUID.randomUUID());
        p.setGuardianUserId(UUID.randomUUID());
        p.setProvider(Payment.Provider.MANUAL);
        p.setExternalId("ext-123");
        p.setExternalReference("ref-123");
        p.setStatus(Payment.Status.APPROVED);
        p.setAmountCents(25000L);
        p.setCurrency("USD");
        p.setPaymentMethod("visa");
        p.setInstallments(3);
        p.setPaidAt(Instant.now());
        p.setFailureReason("card_declined");
        p.setRawResponse("{\"raw\": true}");

        assertThat(p.getProvider()).isEqualTo(Payment.Provider.MANUAL);
        assertThat(p.getStatus()).isEqualTo(Payment.Status.APPROVED);
        assertThat(p.getAmountCents()).isEqualTo(25000L);
        assertThat(p.getInstallments()).isEqualTo(3);
        assertThat(p.getFailureReason()).isEqualTo("card_declined");
        assertThat(p.getRawResponse()).isEqualTo("{\"raw\": true}");
    }

    @Test
    @DisplayName("Status / Provider enums expose expected values")
    void enums() {
        assertThat(Payment.Status.values()).hasSize(6);
        assertThat(Payment.Provider.values()).hasSize(3);
    }
}