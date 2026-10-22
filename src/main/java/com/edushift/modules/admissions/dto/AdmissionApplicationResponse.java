package com.edushift.modules.admissions.dto;

import com.edushift.modules.admissions.entity.AdmissionApplicationStatus;
import com.edushift.modules.admissions.entity.AdmissionSource;
import com.edushift.modules.admissions.entity.AdmissionOfferStatus;
import java.time.Instant;
import java.util.UUID;

public record AdmissionApplicationResponse(
		UUID publicUuid,
		UUID studentPublicUuid,
		String studentFullName,
		UUID academicYearPublicUuid,
		String academicYearName,
		UUID gradePublicUuid,
		String gradeName,
		AdmissionApplicationStatus status,
		AdmissionSource source,
		Instant submittedAt,
		String lastStatusNote,
		UUID enrollmentPublicUuid,
		Instant createdAt,
		Instant updatedAt,
		AdmissionOfferStatus offerStatus,
		Instant offerSentAt,
		Instant offerReminderDueAt,
		Instant offerResponseAt
) {}
