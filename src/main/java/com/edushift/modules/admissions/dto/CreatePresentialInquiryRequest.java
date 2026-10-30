package com.edushift.modules.admissions.dto;

import com.edushift.modules.admissions.entity.AdmissionContactType;
import com.edushift.modules.admissions.entity.AdmissionInquiryChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record CreatePresentialInquiryRequest(
		@NotNull UUID gradePublicUuid,
		@NotBlank @Size(max = 200) String contactName,
		@NotNull AdmissionContactType contactType,
		@NotBlank @Size(max = 254) String contactValue,
		@NotNull AdmissionInquiryChannel initialChannel,
		@Size(max = 2000) String note,
		@Size(max = 100) String responsibleName,
		LocalDate nextAttentionDate,
		@Size(max = 300) String nextAction,
		boolean privacyNoticeGiven
) {
	public CreatePresentialInquiryRequest {
		if (initialChannel == AdmissionInquiryChannel.WEBSITE) {
			initialChannel = AdmissionInquiryChannel.PRESENTIAL;
		}
	}
}
