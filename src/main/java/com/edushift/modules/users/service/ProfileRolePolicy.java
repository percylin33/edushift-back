package com.edushift.modules.users.service;

import com.edushift.modules.auth.entity.UserRole;
import com.edushift.shared.exception.BusinessException;
import java.util.Map;
import java.util.Set;

/**
 * Keeps profile-backed access roles tied to their tenant-scoped domain records.
 * Generic user administration may manage staff and administrator access, while
 * teacher, student, and guardian access must be created through their profile.
 */
public final class ProfileRolePolicy {

	private static final Map<UserRole, String> PROFILE_METADATA_KEYS = Map.of(
			UserRole.TEACHER, "teacherId",
			UserRole.STUDENT, "studentId",
			UserRole.PARENT, "guardianId");

	private ProfileRolePolicy() {
	}

	/**
	 * Refuses profile roles in generic invitations and checks that internal
	 * profile invitations carry exactly the metadata needed by their listeners.
	 */
	public static void requireInvitationMetadata(
			Set<UserRole> roles, Map<String, Object> metadata) {
		Map<String, Object> safeMetadata = metadata == null ? Map.of() : metadata;

		for (Map.Entry<UserRole, String> entry : PROFILE_METADATA_KEYS.entrySet()) {
			boolean roleRequested = roles.contains(entry.getKey());
			boolean metadataProvided = hasValue(safeMetadata.get(entry.getValue()));
			if (roleRequested != metadataProvided) {
				throw profileRoleRequiresProfile(entry.getKey());
			}
		}
	}

	/**
	 * Generic role replacement may preserve existing profile roles so an
	 * administrator can change other access, but it cannot add or remove them.
	 */
	public static void requireRoleChangesAllowed(
			Set<UserRole> existing, Set<UserRole> requested) {
		for (UserRole role : PROFILE_METADATA_KEYS.keySet()) {
			if (existing.contains(role) != requested.contains(role)) {
				throw profileRoleRequiresProfile(role);
			}
		}
	}

	private static boolean hasValue(Object value) {
		return value != null && !String.valueOf(value).isBlank();
	}

	private static BusinessException profileRoleRequiresProfile(UserRole role) {
		String profile = switch (role) {
			case TEACHER -> "docente";
			case STUDENT -> "estudiante";
			case PARENT -> "tutor";
			default -> "perfil";
		};
		return new BusinessException("PROFILE_ROLE_REQUIRES_PROFILE",
				"El acceso de " + profile + " debe asignarse desde su ficha. "
						+ "Cree la ficha y use allí las acciones para invitar o vincular la cuenta.");
	}
}
