package com.edushift.modules.paymentmethods.dto;

import com.edushift.modules.paymentmethods.entity.TenantPaymentMethod.PaymentMethodType;
import java.util.UUID;

public record PaymentMethodAdminResponse(
	UUID publicUuid,
	PaymentMethodType methodType,
	String displayName,
	String bankName,
	String accountHolder,
	String accountNumber,
	String cci,
	String walletPhone,
	String walletQrUrl,
	boolean active,
	boolean defaultMethod
) {}
