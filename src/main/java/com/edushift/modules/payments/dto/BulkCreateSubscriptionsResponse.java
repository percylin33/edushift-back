package com.edushift.modules.payments.dto;

import java.util.List;
import java.util.UUID;

public record BulkCreateSubscriptionsResponse(
		boolean dryRun,
		int candidateCount,
		int createdCount,
		int skippedAlreadyActive,
		int skippedNoGuardian,
		int siblingSuggestionsCreated,
		List<UUID> createdSubscriptionPublicUuids
) {
}
