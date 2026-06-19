package com.edushift.modules.payments.exception;

import com.edushift.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

/** MercadoPago returned an error (4xx/5xx) for our API call. */
public class PaymentFailedException extends ApiException {
    public PaymentFailedException(String message) {
        super(HttpStatus.BAD_GATEWAY, "PAYMENT_PROVIDER_ERROR", message, null);
    }
}
