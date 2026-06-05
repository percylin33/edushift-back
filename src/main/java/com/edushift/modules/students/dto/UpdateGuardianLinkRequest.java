package com.edushift.modules.students.dto;

import com.edushift.modules.students.entity.RelationshipType;

/**
 * Body of {@code PUT /v1/students/{studentUuid}/guardians/{guardianUuid}}.
 *
 * <p>Updates fields on the link itself, not on the guardian profile.
 * To edit the guardian's name / contact info we'd add a separate
 * endpoint at the guardian level (out of scope for Sprint 3).
 *
 * <p>{@code null} = no change. Boolean fields use {@link Boolean}
 * (boxed) on purpose — {@code boolean} primitive would force the
 * client to always send a value, which we don't want.
 */
public record UpdateGuardianLinkRequest(
		RelationshipType relationship,
		Boolean isPrimaryContact,
		Boolean canPickupStudent
) {
	public boolean isEmpty() {
		return relationship == null && isPrimaryContact == null && canPickupStudent == null;
	}
}
