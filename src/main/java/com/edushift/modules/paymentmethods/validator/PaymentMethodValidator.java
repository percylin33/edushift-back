package com.edushift.modules.paymentmethods.validator;

import com.edushift.modules.paymentmethods.dto.PaymentMethodCreateRequest;
import com.edushift.modules.paymentmethods.entity.TenantPaymentMethod.PaymentMethodType;
import org.springframework.stereotype.Component;
import java.util.regex.Pattern;

/**
 * Validador de formatos para métodos de pago Perú.
 */
@Component
public class PaymentMethodValidator {

	// Teléfono Yape/Plin: 9 dígitos
	private static final Pattern WALLET_PHONE = Pattern.compile("^9\\d{8}$");

	// Cuenta bancaria Perú: 13-18 dígitos (con o sin guiones)
	private static final Pattern ACCOUNT_NUMBER = Pattern.compile("^(\\d{13,18}|\\d{3}-\\d{10}-\\d{4})$");

	// CCI Perú: 20 dígitos
	private static final Pattern CCI = Pattern.compile("^\\d{20}$");

	public void validate(PaymentMethodCreateRequest request) {
		switch (request.methodType()) {
			case BANK_TRANSFER -> validateBankTransfer(request);
			case YAPE, PLIN -> validateWallet(request);
			case CASH -> {
				// Efectivo no requiere validación adicional
			}
		}
	}

	private void validateBankTransfer(PaymentMethodCreateRequest request) {
		if (isBlank(request.accountNumber())) {
			throw new ValidationException("Número de cuenta requerido");
		}
		if (!ACCOUNT_NUMBER.matcher(request.accountNumber()).matches()) {
			throw new ValidationException("Formato inválido (13-18 dígitos)");
		}
		if (request.cci() != null && !request.cci().isBlank()
				&& !CCI.matcher(request.cci()).matches()) {
			throw new ValidationException("CCI inválido (20 dígitos)");
		}
	}

	private void validateWallet(PaymentMethodCreateRequest request) {
		if (isBlank(request.walletPhone())) {
			throw new ValidationException("Teléfono requerido");
		}
		if (!WALLET_PHONE.matcher(request.walletPhone()).matches()) {
			throw new ValidationException("Teléfono inválido (9 dígitos)");
		}
	}

	private boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

	public static class ValidationException extends RuntimeException {
		public ValidationException(String message) {
			super(message);
		}
	}
}
