package com.edushift.modules.payments.dto;

import java.util.List;
import java.util.UUID;

public record BulkEmitInvoicesResponse(
		boolean dryRun,
		int candidateCount,
		int createdCount,
		int skippedDuplicateCount,
		int skippedNoGuardianCount,
		List<UUID> createdInvoicePublicUuids
) {
}
