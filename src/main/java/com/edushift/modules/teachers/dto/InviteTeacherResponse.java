package com.edushift.modules.teachers.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response of {@code POST /v1/teachers/{publicUuid}/invite}. Mirrors
 * the projection returned by {@code POST /v1/users/invitations} but
 * limits the surface to the fields a teacher-invite admin needs.
 */
public record InviteTeacherResponse(
		UUID invitationPublicUuid,
		String invitationToken,
		Instant expiresAt,
		UUID teacherPublicUuid,
		String email
) {
}
