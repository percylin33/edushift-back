package com.edushift.modules.admissions.dto;

import com.edushift.modules.admissions.entity.AdmissionReservationAdvanceMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RecordAdmissionReservationAdvanceRequest(
		@Positive long amountCents,
		@NotNull AdmissionReservationAdvanceMethod method,
		@Size(max = 120) String reference,
		@Size(max = 500) String notes
) {}
