package com.edushift.modules.users.mapper;

import com.edushift.modules.auth.entity.User;
import com.edushift.modules.users.dto.UpdateUserRequest;
import com.edushift.modules.users.dto.UserDetailResponse;
import com.edushift.modules.users.dto.UserListItem;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper between the {@link User} aggregate and the user
 * management DTOs. Hand-written for the same reasons as
 * {@code TenantMapper}: pinning the partial-merge semantics in code
 * (and tests) is far cleaner than fighting MapStruct conventions.
 *
 * <h3>Why no {@code Set} → {@code Set} role mapping here</h3>
 * Role assignment goes through
 * {@link com.edushift.modules.users.service.UserManagementService#assignRoles}
 * because role changes need validation against {@link
 * com.edushift.modules.auth.entity.UserRole} (unknown names → 400) and
 * a guardrail to prevent stranding the tenant without a {@code TENANT_ADMIN}.
 * The mapper only <em>reads</em> roles for the response DTOs.
 */
@Component
public class UserManagementMapper {

	public UserListItem toListItem(User user) {
		return new UserListItem(
				user.getPublicUuid(),
				user.getEmail(),
				user.getFirstName(),
				user.getLastName(),
				user.fullName(),
				user.getStatus(),
				user.getRoleNames(),
				user.getLastLoginAt(),
				user.getCreatedAt()
		);
	}

	public UserDetailResponse toDetail(User user) {
		return new UserDetailResponse(
				user.getPublicUuid(),
				user.getEmail(),
				user.getFirstName(),
				user.getLastName(),
				user.fullName(),
				user.getPhone(),
				user.getAvatarUrl(),
				user.getStatus(),
				user.isEmailVerified(),
				user.isMfaEnabled(),
				user.getRoleNames(),
				user.getLastLoginAt(),
				user.getCreatedAt(),
				user.getUpdatedAt()
		);
	}

	/**
	 * Applies a partial profile patch in place. Mirrors {@code TenantMapper.applyUpdate}:
	 * <ul>
	 *   <li>Null = no-op (don't clobber the existing value).</li>
	 *   <li>Blank string on a nullable column = clear that column. Useful for
	 *       removing an avatar or clearing a phone number — admins need a way
	 *       to express "remove this".</li>
	 *   <li>Non-blank string = trimmed and persisted.</li>
	 * </ul>
	 *
	 * <p>Email and roles are intentionally <em>not</em> handled here — see
	 * the class-level Javadoc.
	 */
	public void applyUpdate(UpdateUserRequest patch, User user) {
		if (patch == null || patch.isEmpty()) return;

		if (patch.firstName() != null) {
			user.setFirstName(patch.firstName().trim());
		}
		if (patch.lastName() != null) {
			user.setLastName(patch.lastName().trim());
		}
		if (patch.phone() != null) {
			user.setPhone(patch.phone().isBlank() ? null : patch.phone().trim());
		}
		if (patch.avatarUrl() != null) {
			user.setAvatarUrl(patch.avatarUrl().isBlank() ? null : patch.avatarUrl().trim());
		}
	}
}
