package com.edushift.modules.admissions.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateAdmissionReservationRequest(
		@NotNull @Future Instant expiresAt,
		@Size(max = 1000) String reason
) {}
