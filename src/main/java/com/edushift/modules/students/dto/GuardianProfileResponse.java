package com.edushift.modules.students.dto;

import com.edushift.modules.students.entity.DocumentType;
import java.util.UUID;

/**
 * Guardian identity profile returned by document lookup.
 *
 * <p>Used to pre-fill the add-guardian form when the admin types a
 * document that already exists in the tenant (sibling-sharing path).
 * Relationship metadata is intentionally omitted — it is always
 * student-specific and entered at link time.
 */
public record GuardianProfileResponse(
		UUID guardianPublicUuid,
		DocumentType documentType,
		String documentNumber,
		String firstName,
		String lastName,
		String fullName,
		String email,
		String phone,
		String occupation,
		UUID userId
) {
}
