package com.edushift.modules.admissions.dto;

import com.edushift.modules.admissions.entity.AdmissionApplicationStatus;
import java.time.Instant;
import java.util.UUID;

public record AdmissionStatusEventResponse(
		AdmissionApplicationStatus fromStatus,
		AdmissionApplicationStatus toStatus,
		String note,
		UUID actorUserId,
		Instant createdAt
) {}
