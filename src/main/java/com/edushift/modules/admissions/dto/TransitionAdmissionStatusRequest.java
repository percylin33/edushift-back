package com.edushift.modules.admissions.dto;

import com.edushift.modules.admissions.entity.AdmissionApplicationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransitionAdmissionStatusRequest(
		@NotNull AdmissionApplicationStatus status,
		@Size(max = 2000) String note
) {}
