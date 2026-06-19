package com.edushift.modules.payments.exception;

import com.edushift.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

/** Invoice not found in current tenant. */
public class InvoiceNotFoundException extends ApiException {
    public InvoiceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "INVOICE_NOT_FOUND", message, null);
    }
}
