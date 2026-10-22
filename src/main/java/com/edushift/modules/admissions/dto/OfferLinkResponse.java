package com.edushift.modules.admissions.dto;

import com.edushift.modules.admissions.entity.AdmissionOfferStatus;
import java.time.Instant;

/** One-time staff response used to copy the family confirmation link. */
public record OfferLinkResponse(
		String confirmationUrl,
		AdmissionOfferStatus status,
		Instant reminderDueAt,
		Instant expiresAt
) {}
