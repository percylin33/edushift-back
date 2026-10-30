package com.edushift.modules.students.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Payload of {@code POST /v1/students/{publicUuid}/link-user}. Links an
 * existing {@code User} from the same tenant to a student who has no
 * portal account yet. The {@code STUDENT} role is granted atomically
 * with the profile link.
 */
public record LinkStudentUserRequest(
		@NotNull(message = "userPublicUuid is required")
		UUID userPublicUuid
) {
}
