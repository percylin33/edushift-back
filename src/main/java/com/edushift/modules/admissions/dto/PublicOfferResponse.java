package com.edushift.modules.admissions.dto;

import com.edushift.modules.admissions.entity.AdmissionOfferStatus;
import java.time.Instant;

/** Public-safe offer details; contains no DNI, email, or phone. */
public record PublicOfferResponse(
		String tenantName,
		String studentName,
		String gradeName,
		String academicYearName,
		AdmissionOfferStatus status,
		Instant reminderDueAt,
		Instant expiresAt
) {}
