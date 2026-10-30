package com.edushift.modules.payments.dto;

import com.edushift.modules.payments.entity.Subscription;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Create a recurring billing plan. {@code conceptPublicUuid} is required;
 * {@code amountCents} defaults to the concept amount when null/omitted.
 *
 * <p>{@code billingDayOfMonth} (1–31) is required. For {@code ANNUAL}, also send
 * {@code billingMonth} (1–12). Days that don't exist in a month bill on that month's last day.</p>
 */
public record CreateSubscriptionRequest(
		@NotNull UUID studentPublicUuid,
		@NotNull UUID conceptPublicUuid,
		@Size(max = 40) String planCode,
		@Min(1) Long amountCents,
		@NotNull Subscription.BillingPeriod billingPeriod,
		@NotNull @Min(1) @Max(31) Integer billingDayOfMonth,
		@Min(1) @Max(12) Integer billingMonth,
		@Size(max = 3) String currency
) {
}
