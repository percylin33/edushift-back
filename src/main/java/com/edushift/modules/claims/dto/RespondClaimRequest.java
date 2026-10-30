package com.edushift.modules.claims.dto;

import jakarta.validation.constraints.NotBlank;

public record RespondClaimRequest(
		@NotBlank(message = "La respuesta formal es obligatoria")
		String responseText
) {}
