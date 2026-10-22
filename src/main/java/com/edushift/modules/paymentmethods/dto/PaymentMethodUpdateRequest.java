package com.edushift.modules.paymentmethods.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PaymentMethodUpdateRequest(

	@Size(max = 100, message = "Nombre muy largo")
	String displayName,

	@Size(max = 100, message = "Nombre de banco muy largo")
	String bankName,

	@Size(max = 200, message = "Titular muy largo")
	String accountHolder,

	@Pattern(regexp = "^(\\d{13,18}|\\d{3}-\\d{10}-\\d{4})$", message = "Formato inválido (13-18 dígitos)")
	String accountNumber,

	@Pattern(regexp = "^\\d{20}$", message = "CCI inválido (20 dígitos)")
	String cci,

	@Pattern(regexp = "^9\\d{8}$", message = "Teléfono inválido (9 dígitos)")
	String walletPhone,

	String walletQrUrl,

	Boolean active
) {}
