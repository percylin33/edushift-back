package com.edushift.modules.students.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response of {@code POST /v1/students/{publicUuid}/invite}. Token is
 * returned once so secretaría can copy the activation link when SMTP
 * is off.
 */
public record InviteStudentResponse(
		UUID invitationPublicUuid,
		String invitationToken,
		Instant expiresAt,
		UUID studentPublicUuid,
		String email
) {
}
