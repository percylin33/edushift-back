package com.edushift.modules.students.dto;

import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.RelationshipType;
import java.util.UUID;

/**
 * Composite projection: a {@code Guardian} together with the
 * {@code StudentGuardian} link that connects them to the student
 * the request came in for.
 *
 * <p>The {@code linkPublicUuid} is the handle admins use to update or
 * delete the relationship; the {@code guardianPublicUuid} is the
 * handle they use to update the guardian profile itself (Sprint 4+,
 * not exposed yet — only useful for API consumers building richer
 * tooling on top).
 */
public record GuardianResponse(
		UUID linkPublicUuid,
		UUID guardianPublicUuid,
		DocumentType documentType,
		String documentNumber,
		String firstName,
		String lastName,
		String fullName,
		String email,
		String phone,
		String occupation,
		RelationshipType relationship,
		boolean isPrimaryContact,
		boolean canPickupStudent
) {
}
