package com.edushift.modules.payments.dto;

import java.time.Instant;
import java.util.UUID;

/** Tenant-scoped active LMS exception without exposing persistence entities. */
public record PaymentAccessOverrideDto(
		UUID publicUuid,
		UUID userPublicUuid,
		String userName,
		UUID studentPublicUuid,
		String studentName,
		String reason,
		Instant untilAt
) {}
