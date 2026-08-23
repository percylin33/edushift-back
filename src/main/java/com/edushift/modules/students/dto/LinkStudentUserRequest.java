package com.edushift.modules.students.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Payload of {@code POST /v1/students/{publicUuid}/link-user}. Links an
 * existing {@code User} (same tenant, {@code STUDENT} role) to a student
 * who has no portal account yet.
 */
public record LinkStudentUserRequest(
		@NotNull(message = "userPublicUuid is required")
		UUID userPublicUuid
) {
}
