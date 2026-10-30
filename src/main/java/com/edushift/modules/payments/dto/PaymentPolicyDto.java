package com.edushift.modules.payments.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Per-tenant payment collection policy stored in {@code tenants.settings.paymentPolicy}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentPolicyDto(
		List<Integer> reminderDaysBeforeDue,
		boolean moraEnabled,
		int moraGraceDays,
		double moraPercent,
		boolean lockEnabled,
		int lockOverdueDays,
		boolean siblingDiscountEnabled,
		long siblingDiscountCents,
		int siblingMinCount
) {
	public static PaymentPolicyDto defaults() {
		return new PaymentPolicyDto(List.of(7, 3, 1), false, 3, 5.0, false, 1, false, 0L, 2);
	}
}
