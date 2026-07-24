package com.edushift.modules.payments.exception;

import com.edushift.shared.exception.BusinessException;

/**
 * Thrown when a payment concept lookup fails inside the current tenant.
 * Anti-enumeration: callers receive 404 / PAYMENT_CONCEPT_NOT_FOUND.
 */
public class PaymentConceptNotFoundException extends BusinessException {

	public PaymentConceptNotFoundException(String message) {
		super("PAYMENT_CONCEPT_NOT_FOUND", message);
	}
}