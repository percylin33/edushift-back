package com.edushift.modules.admissions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecordAdmissionReservationAdvanceRefundRequest(
		@NotBlank @Size(max = 500) String reference,
		@Size(max = 500) String notes
) {}
