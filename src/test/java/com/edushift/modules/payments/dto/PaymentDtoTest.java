package com.edushift.modules.payments.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.payments.entity.Invoice;
import com.edushift.modules.payments.entity.InvoiceItem;
import com.edushift.modules.payments.entity.Payment;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentDtoTest {

    @Test
    @DisplayName("CheckoutResponse carries the payment publicUuid and init_point URLs")
    void checkoutResponse() {
        UUID id = UUID.randomUUID();
        var r = new CheckoutResponse(id, "https://mp/init", "https://mp/sandbox");
        assertThat(r.paymentPublicUuid()).isEqualTo(id);
        assertThat(r.initPoint()).isEqualTo("https://mp/init");
        assertThat(r.sandboxInitPoint()).isEqualTo("https://mp/sandbox");
    }

    @Test
    @DisplayName("InvoiceItemResponse.from maps entity")
    void invoiceItemResponseFrom() {
        var item = new InvoiceItem();
        item.setInvoiceId(UUID.randomUUID());
        item.setDescription("Matricula 2026");
        item.setQuantity(2);
        item.setUnitAmountCents(15000L);
        item.setLineTotalCents(30000L);
        InvoiceItemResponse r = InvoiceItemResponse.from(item);
        assertThat(r.description()).isEqualTo("Matricula 2026");
        assertThat(r.quantity()).isEqualTo(2);
        assertThat(r.unitAmountCents()).isEqualTo(15000L);
        assertThat(r.lineTotalCents()).isEqualTo(30000L);
    }

    @Test
    @DisplayName("InvoiceResponse.from maps every scalar + items")
    void invoiceResponseFrom() {
        var i = new Invoice();
        i.setPublicUuid(UUID.randomUUID());
        i.setSubscriptionId(UUID.randomUUID());
        i.setStudentId(UUID.randomUUID());
        i.setGuardianUserId(UUID.randomUUID());
        i.setIdempotencyKey("sub:019e:2026-01");
        i.setPeriodLabel("2026-01");
        i.setCurrency("PEN");
        i.setSubtotalCents(25000L);
        i.setDiscountCents(1000L);
        i.setTaxCents(0L);
        i.setTotalCents(24000L);
        i.setStatus(Invoice.Status.PAID);
        i.setIssuedAt(Instant.parse("2026-01-01T00:00:00Z"));
        i.setDueAt(Instant.parse("2026-01-15T00:00:00Z"));
        i.setPaidAt(Instant.parse("2026-01-02T00:00:00Z"));
        i.setNotes("On time");
        var item = new InvoiceItem();
        item.setDescription("Cuota");
        item.setQuantity(1);
        item.setUnitAmountCents(25000L);
        item.setLineTotalCents(25000L);
        InvoiceResponse r = InvoiceResponse.from(i, List.of(InvoiceItemResponse.from(item)));
        assertThat(r.publicUuid()).isEqualTo(i.getPublicUuid());
        assertThat(r.periodLabel()).isEqualTo("2026-01");
        assertThat(r.totalCents()).isEqualTo(24000L);
        assertThat(r.status()).isEqualTo(Invoice.Status.PAID);
        assertThat(r.items()).hasSize(1);
        assertThat(r.items().get(0).description()).isEqualTo("Cuota");
    }

    @Test
    @DisplayName("PaymentResponse.from maps every scalar")
    void paymentResponseFrom() {
        var p = new Payment();
        p.setPublicUuid(UUID.randomUUID());
        p.setInvoiceId(UUID.randomUUID());
        p.setGuardianUserId(UUID.randomUUID());
        p.setProvider(Payment.Provider.MERCADOPAGO);
        p.setExternalId("123");
        p.setStatus(Payment.Status.APPROVED);
        p.setAmountCents(25000L);
        p.setCurrency("PEN");
        p.setPaymentMethod("visa");
        p.setInstallments(3);
        p.setPaidAt(Instant.now());
        p.setFailureReason(null);
        PaymentResponse r = PaymentResponse.from(p);
        assertThat(r.provider()).isEqualTo(Payment.Provider.MERCADOPAGO);
        assertThat(r.status()).isEqualTo(Payment.Status.APPROVED);
        assertThat(r.installments()).isEqualTo(3);
    }
}