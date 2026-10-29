package com.edushift.modules.admissions.dto;

import com.edushift.modules.admissions.entity.AdmissionContactType;
import com.edushift.modules.admissions.entity.AdmissionInquiryStatus;
import com.edushift.modules.admissions.entity.AdmissionInquirySource;
import com.edushift.modules.admissions.entity.AdmissionInquiryChannel;
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
        String nextAction,
		AdmissionInquirySource source,
		AdmissionInquiryChannel initialChannel,
		Instant privacyNoticeGivenAt
) {
	public AdmissionInquiryResponse(UUID publicUuid, UUID academicYearPublicUuid, String academicYearName,
			UUID gradePublicUuid, String gradeName, String contactName, AdmissionContactType contactType,
			String contactValue, AdmissionInquiryStatus status, String note,
			UUID convertedApplicationPublicUuid, Instant createdAt,
			com.edushift.modules.admissions.entity.AdmissionApplicationStatus convertedApplicationStatus,
			UUID convertedAcademicYearPublicUuid, String responsibleName,
			java.time.LocalDate nextAttentionDate, String nextAction) {
		this(publicUuid, academicYearPublicUuid, academicYearName, gradePublicUuid, gradeName, contactName,
				contactType, contactValue, status, note, convertedApplicationPublicUuid, createdAt,
				convertedApplicationStatus, convertedAcademicYearPublicUuid, responsibleName,
				nextAttentionDate, nextAction, AdmissionInquirySource.WEBSITE,
				AdmissionInquiryChannel.WEBSITE, null);
	}
}
