package com.edushift.modules.students.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response of {@code POST /v1/students/{student}/guardians/{g}/invite}.
 * One invitation per Guardian (sibling sharing): accepting links the
 * portal to every child already on that guardian.
 */
public record InviteGuardianResponse(
		UUID invitationPublicUuid,
		String invitationToken,
		Instant expiresAt,
		UUID guardianPublicUuid,
		UUID studentPublicUuid,
		String email
) {
}
