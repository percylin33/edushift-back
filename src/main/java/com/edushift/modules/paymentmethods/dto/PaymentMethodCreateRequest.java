package com.edushift.modules.paymentmethods.dto;

import com.edushift.modules.paymentmethods.entity.TenantPaymentMethod.PaymentMethodType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PaymentMethodCreateRequest(

	@NotNull(message = "Tipo de método requerido")
	PaymentMethodType methodType,

	@Size(max = 100, message = "Nombre muy largo")
	String displayName,

	// Transferencia bancaria
	@Size(max = 100, message = "Nombre de banco muy largo")
	String bankName,

	@Size(max = 200, message = "Titular muy largo")
	String accountHolder,

	String accountNumber,

	String cci,

	// Billeteras digitales
	String walletPhone,

	String walletQrUrl
) {}
