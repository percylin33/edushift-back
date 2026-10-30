package com.edushift.modules.admissions.dto;

import com.edushift.modules.admissions.entity.AdmissionReservationAdvanceEventAction;
import java.time.Instant;
import java.util.UUID;

public record AdmissionReservationAdvanceEventResponse(
		UUID advancePublicUuid,
		AdmissionReservationAdvanceEventAction action,
		long amountCents,
		UUID invoicePublicUuid,
		String note,
		UUID actorUserId,
		Instant createdAt
) {}
