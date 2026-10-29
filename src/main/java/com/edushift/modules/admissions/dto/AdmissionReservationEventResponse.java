package com.edushift.modules.admissions.dto;

import com.edushift.modules.admissions.entity.AdmissionReservationStatus;
import java.time.Instant;
import java.util.UUID;

public record AdmissionReservationEventResponse(
		AdmissionReservationStatus fromStatus,
		AdmissionReservationStatus toStatus,
		String note,
		UUID actorUserId,
		Instant createdAt
) {}
