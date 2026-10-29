package com.edushift.modules.admissions.service;

import com.edushift.modules.admissions.entity.AdmissionReservationAdvanceMethod;
import java.time.Instant;
import java.util.UUID;

public record AppliedAdmissionAdvance(
		UUID publicUuid,
		long amountCents,
		String currency,
		AdmissionReservationAdvanceMethod method,
		String reference,
		Instant receivedAt
) {}
