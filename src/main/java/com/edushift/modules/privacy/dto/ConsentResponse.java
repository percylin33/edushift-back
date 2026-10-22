package com.edushift.modules.privacy.dto;

import java.time.Instant;
import java.util.UUID;

public record ConsentResponse(
		UUID publicUuid,
		UUID guardianUserId,
		UUID studentPublicUuid,
		String policyVersion,
		String purposeCode,
		String noticeSnapshot,
		Boolean consentGranted,
		Instant grantedAt,
		Instant revokedAt
) {}
