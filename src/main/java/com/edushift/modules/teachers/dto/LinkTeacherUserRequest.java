package com.edushift.modules.teachers.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Payload of {@code POST /v1/teachers/{publicUuid}/link-user}. Links an
 * existing {@code User} account (must be in the same tenant and have
 * the {@code TEACHER} role) to a teacher. The constraint
 * "one user → at most one teacher" is enforced at both the service
 * layer ({@code USER_ALREADY_LINKED_TO_TEACHER}) and the DB
 * ({@code uk_teachers_user_active}).
 */
public record LinkTeacherUserRequest(
		@NotNull(message = "userPublicUuid is required")
		UUID userPublicUuid
) {
}
