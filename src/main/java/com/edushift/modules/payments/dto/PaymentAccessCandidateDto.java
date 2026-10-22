package com.edushift.modules.payments.dto;

import java.util.UUID;

/** Minimal student/guardian option for the tenant payment access exception picker. */
public record PaymentAccessCandidateDto(
		UUID userPublicUuid,
		UUID studentPublicUuid,
		String displayName,
		String relationship
) {}
