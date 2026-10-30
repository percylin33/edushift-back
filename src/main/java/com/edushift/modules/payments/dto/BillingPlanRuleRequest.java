package com.edushift.modules.payments.dto;

import com.edushift.modules.payments.entity.Subscription;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Creates or previews an automatic tuition rule for exactly one academic scope. */
public record BillingPlanRuleRequest(
		@NotNull UUID conceptPublicUuid,
		UUID levelPublicUuid,
		UUID gradePublicUuid,
		@Min(1) Long amountCents,
		@NotNull Subscription.BillingPeriod billingPeriod,
		@NotNull @Min(1) @Max(31) Integer billingDayOfMonth,
		@Min(1) @Max(12) Integer billingMonth,
		@Size(min = 3, max = 3) @Pattern(regexp = "[A-Z]{3}") String currency
) {}
