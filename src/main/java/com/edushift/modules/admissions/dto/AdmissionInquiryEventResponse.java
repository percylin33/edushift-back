package com.edushift.modules.admissions.dto;

import com.edushift.modules.admissions.entity.AdmissionInquiryEvent.ActorType;
import com.edushift.modules.admissions.entity.AdmissionInquiryStatus;
import java.time.Instant;
import java.util.UUID;

public record AdmissionInquiryEventResponse(
		AdmissionInquiryStatus fromStatus,
		AdmissionInquiryStatus toStatus,
		String note,
		UUID actorUserId,
		ActorType actorType,
		Instant createdAt
) {}
