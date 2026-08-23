package com.edushift.modules.students.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Payload of {@code POST /v1/students/{student}/guardians/{g}/link-user}.
 * Rescue path: an admin invited a PARENT from Usuarios and now binds
 * that User to the guardian ficha.
 */
public record LinkGuardianUserRequest(
		@NotNull(message = "userPublicUuid is required")
		UUID userPublicUuid
) {
}
