package com.edushift.modules.payments.dto;

import com.edushift.modules.payments.entity.Subscription;
import java.util.UUID;

/** Rule details and the most recent synchronization outcome. */
public record BillingPlanRuleResponse(
		UUID publicUuid,
		UUID conceptPublicUuid,
		UUID levelPublicUuid,
		UUID gradePublicUuid,
		long amountCents,
		String currency,
		Subscription.BillingPeriod billingPeriod,
		int billingDayOfMonth,
		Integer billingMonth,
		boolean active,
		int candidateCount,
		int createdCount,
		int alreadyActiveCount,
		int waitingGuardianCount
) {}
