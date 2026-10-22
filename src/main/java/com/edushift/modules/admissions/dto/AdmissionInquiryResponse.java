package com.edushift.modules.admissions.dto;

import com.edushift.modules.admissions.entity.AdmissionContactType;
import com.edushift.modules.admissions.entity.AdmissionInquiryStatus;
import java.time.Instant;
import java.util.UUID;

public record AdmissionInquiryResponse(
		UUID publicUuid,
		UUID academicYearPublicUuid,
		String academicYearName,
		UUID gradePublicUuid,
		String gradeName,
		String contactName,
		AdmissionContactType contactType,
		String contactValue,
		AdmissionInquiryStatus status,
		String note,
		UUID convertedApplicationPublicUuid,
		Instant createdAt,
		com.edushift.modules.admissions.entity.AdmissionApplicationStatus convertedApplicationStatus,
		UUID convertedAcademicYearPublicUuid,
        String responsibleName,
        java.time.LocalDate nextAttentionDate,
        String nextAction
) {}
