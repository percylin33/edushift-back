package com.edushift.modules.admissions.dto;

import com.edushift.modules.admissions.entity.AdmissionReservationStatus;
import java.time.Instant;
import java.util.UUID;

public record AdmissionReservationResponse(
		UUID publicUuid,
		UUID applicationPublicUuid,
		String studentFullName,
		UUID academicYearPublicUuid,
		String academicYearName,
		UUID gradePublicUuid,
		String gradeName,
		AdmissionReservationStatus status,
		Instant expiresAt,
		String reason,
		UUID studentPublicUuid,
		long advanceReceivedCents,
		long advanceAvailableCents
) {}
