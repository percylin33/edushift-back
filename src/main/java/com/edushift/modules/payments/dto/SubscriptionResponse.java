package com.edushift.modules.payments.dto;

import com.edushift.modules.payments.entity.Subscription;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionResponse(
		UUID publicUuid,
		UUID studentId,
		UUID guardianUserId,
		UUID conceptId,
		String planCode,
		long amountCents,
		String currency,
		Subscription.BillingPeriod billingPeriod,
		Subscription.Status status,
		Subscription.SiblingDiscountStatus siblingDiscountStatus,
		Integer billingDayOfMonth,
		Integer billingMonth,
		Instant startAt,
		Instant nextBillingAt,
		Instant cancelledAt
) {
	public static SubscriptionResponse from(Subscription s) {
		return new SubscriptionResponse(
				s.getPublicUuid(),
				s.getStudentId(),
				s.getGuardianUserId(),
				s.getConceptId(),
				s.getPlanCode(),
				s.getAmountCents(),
				s.getCurrency(),
				s.getBillingPeriod(),
				s.getStatus(),
				s.getSiblingDiscountStatus() == null
						? Subscription.SiblingDiscountStatus.NONE
						: s.getSiblingDiscountStatus(),
				s.getBillingDayOfMonth(),
				s.getBillingMonth(),
				s.getStartAt(),
				s.getNextBillingAt(),
				s.getCancelledAt());
	}
}
