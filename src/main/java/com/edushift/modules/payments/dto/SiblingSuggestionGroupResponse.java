package com.edushift.modules.payments.dto;

import com.edushift.modules.payments.entity.Subscription;
import java.util.List;
import java.util.UUID;

public record SiblingSuggestionGroupResponse(
		UUID guardianId,
		String guardianName,
		String guardianDocument,
		int siblingCount,
		long suggestedDiscountCents,
		List<SiblingSuggestionChildResponse> children
) {
	public record SiblingSuggestionChildResponse(
			UUID studentPublicUuid,
			String studentName,
			UUID subscriptionPublicUuid,
			String planCode,
			long amountCents,
			String currency,
			Subscription.SiblingDiscountStatus siblingDiscountStatus
	) {
	}
}
