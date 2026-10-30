package com.edushift.modules.students.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Payload of {@code POST /v1/students/{student}/guardians/{g}/link-user}.
 * Links an existing account from the same tenant to the guardian ficha.
 * The {@code PARENT} role is granted atomically with the profile link.
 */
public record LinkGuardianUserRequest(
		@NotNull(message = "userPublicUuid is required")
		UUID userPublicUuid
) {
}
