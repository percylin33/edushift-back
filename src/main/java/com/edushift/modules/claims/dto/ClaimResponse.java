package com.edushift.modules.claims.dto;

import com.edushift.modules.claims.entity.ClaimsBookEntry.ClaimStatus;
import com.edushift.modules.claims.entity.ClaimsBookEntry.ClaimType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ClaimResponse(
		UUID id,
		String claimCode,
		String consumerName,
		String consumerDocType,
		String consumerDocNumber,
		String consumerEmail,
		String consumerPhone,
		String consumerAddress,
		Boolean isMinor,
		String parentGuardianName,
		String contractedService,
		BigDecimal claimAmount,
		ClaimType claimType,
		String detailDescription,
		String consumerRequest,
		ClaimStatus status,
		LocalDate dueDate,
		String responseText,
		Instant respondedAt,
		Instant createdAt
) {}
