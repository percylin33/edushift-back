package com.edushift.modules.claims.dto;

import com.edushift.modules.claims.entity.ClaimsBookEntry.ClaimType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateClaimRequest(
		@NotBlank(message = "El nombre del consumidor es obligatorio")
		String consumerName,

		@NotBlank(message = "El tipo de documento es obligatorio")
		String consumerDocType,

		@NotBlank(message = "El número de documento es obligatorio")
		String consumerDocNumber,

		@NotBlank(message = "El correo electrónico es obligatorio")
		@Email(message = "Ingrese un correo válido")
		String consumerEmail,

		@NotBlank(message = "El teléfono es obligatorio")
		String consumerPhone,

		@NotBlank(message = "La dirección es obligatoria")
		String consumerAddress,

		Boolean isMinor,
		String parentGuardianName,

		@NotBlank(message = "El servicio o bien contratado es obligatorio")
		String contractedService,

		BigDecimal claimAmount,

		@NotNull(message = "El tipo de registro (RECLAMO o QUEJA) es obligatorio")
		ClaimType claimType,

		@NotBlank(message = "El detalle del reclamo es obligatorio")
		String detailDescription,

		@NotBlank(message = "El pedido o solicitud es obligatorio")
		String consumerRequest
) {}
