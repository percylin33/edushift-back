package com.edushift.modules.payments.dto;

import com.edushift.modules.payments.entity.Subscription;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Bulk-assign recurring plans to all actively enrolled students in a level or grade.
 * Exactly one of {@code levelPublicUuid} / {@code gradePublicUuid} must be set.
 */
public record BulkCreateSubscriptionsRequest(
		@NotNull UUID conceptPublicUuid,
		UUID levelPublicUuid,
		UUID gradePublicUuid,
		@Size(max = 40) String planCode,
		@Min(1) Long amountCents,
		@NotNull Subscription.BillingPeriod billingPeriod,
		@NotNull @Min(1) @Max(31) Integer billingDayOfMonth,
		@Min(1) @Max(12) Integer billingMonth,
		@Size(max = 3) String currency,
		boolean dryRun
) {
}
