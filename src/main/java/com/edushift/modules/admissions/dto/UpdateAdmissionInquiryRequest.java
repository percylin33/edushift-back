package com.edushift.modules.admissions.dto;

import com.edushift.modules.admissions.entity.AdmissionInquiryStatus;
import jakarta.validation.constraints.Size;

public record UpdateAdmissionInquiryRequest(
		AdmissionInquiryStatus status,
		@Size(max = 2000) String note,
        @Size(max = 100) String responsibleName,
        java.time.LocalDate nextAttentionDate,
        @Size(max = 300) String nextAction,
        boolean updateFollowUp
) {
    public UpdateAdmissionInquiryRequest(AdmissionInquiryStatus status, String note) {
        this(status, note, null, null, null, false);
    }
}
