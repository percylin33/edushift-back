package com.edushift.modules.payments.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.shared.exception.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PaymentsExceptionTest {

    @Test
    @DisplayName("InvoiceNotFoundException → 404 INVOICE_NOT_FOUND")
    void invoiceNotFound() {
        var ex = new InvoiceNotFoundException("Invoice not found in the current tenant: x");
        assertThat(ex).isInstanceOf(ApiException.class);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getCode()).isEqualTo("INVOICE_NOT_FOUND");
        assertThat(ex.getMessage()).contains("x");
    }

    @Test
    @DisplayName("PaymentFailedException → 502 PAYMENT_PROVIDER_ERROR")
    void paymentFailed() {
        var ex = new PaymentFailedException("MP createPreference failed: 401");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(ex.getCode()).isEqualTo("PAYMENT_PROVIDER_ERROR");
        assertThat(ex.getMessage()).contains("401");
    }
}