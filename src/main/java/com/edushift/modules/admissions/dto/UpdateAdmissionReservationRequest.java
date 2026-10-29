package com.edushift.modules.admissions.dto;

import com.edushift.modules.admissions.entity.AdmissionReservationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateAdmissionReservationRequest(
		@NotNull AdmissionReservationStatus status,
		@Size(max = 1000) String reason
) {}
