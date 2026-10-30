package com.edushift.modules.admissions.dto;

import com.edushift.modules.admissions.entity.AdmissionContactType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Minimal public intake: never request identity documents at this stage. */
public record PublicAdmissionInquiryRequest(
		@NotNull UUID academicYearPublicUuid,
		@NotNull UUID gradePublicUuid,
		@NotBlank @Size(max = 200) String contactName,
		@NotNull AdmissionContactType contactType,
		@NotBlank @Size(max = 32) @Pattern(regexp = "^[+0-9()\\-\\s]{7,32}$") String contactValue,
		boolean privacyAccepted,
		@Size(max = 120) String website
) {}
