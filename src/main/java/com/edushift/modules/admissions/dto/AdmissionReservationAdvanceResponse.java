package com.edushift.modules.admissions.dto;

import com.edushift.modules.admissions.entity.AdmissionReservationAdvanceMethod;
import com.edushift.modules.admissions.entity.AdmissionReservationAdvanceStatus;
import java.time.Instant;
import java.util.UUID;

public record AdmissionReservationAdvanceResponse(
		UUID publicUuid,
		UUID reservationPublicUuid,
		long amountCents,
		long appliedCents,
		long availableCents,
		String currency,
		AdmissionReservationAdvanceMethod method,
		AdmissionReservationAdvanceStatus status,
		String reference,
		String notes,
		Instant receivedAt
) {}
