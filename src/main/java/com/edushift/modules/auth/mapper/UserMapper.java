package com.edushift.modules.auth.mapper;

import com.edushift.modules.auth.dto.CreateUserRequest;
import com.edushift.modules.auth.dto.UpdateUserRequest;
import com.edushift.modules.auth.dto.UserResponse;
import com.edushift.modules.auth.dto.UserSummary;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.shared.security.LmsRoleAuthorityMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * DTO ↔ entity mapping for the {@link User} aggregate.
 * <p>
 * Implemented as a Spring component (rather than MapStruct) because the
 * mapping is intentionally small and asymmetric: the API <em>never</em>
 * accepts the password hash or the internal id, so we don't want a generic
 * machine-generated mapper to ever overwrite those fields by accident.
 *
 * <h3>Security contract</h3>
 * <ul>
 *   <li>{@link #toResponse(User)} and {@link #toSummary(User)} expose only
 *       {@code publicUuid} and never the password hash.</li>
 *   <li>{@link #toEntity(CreateUserRequest, String)} requires the
 *       <em>already hashed</em> password — the raw value never reaches the
 *       entity layer.</li>
 *   <li>{@link #applyUpdate(User, UpdateUserRequest)} touches only
 *       profile-mutable fields; identity / security flags must be updated
 *       through dedicated service methods.</li>
 * </ul>
 *
 * <h3>Permissions</h3>
 * {@link #toResponse(User)} additionally populates the {@code permissions}
 * field by delegating to {@link LmsRoleAuthorityMapper}, which translates
 * the user's coarse roles into the granular {@code LMS_*} authorities that
 * the frontend's {@code permissionGuard} and {@code HasPermissionDirective}
 * match against. Closes part of DEBT-SEC-1 for the LMS surface.
 */
@Component
public class UserMapper {

	private final LmsRoleAuthorityMapper lmsRoleAuthorityMapper;

	public UserMapper(LmsRoleAuthorityMapper lmsRoleAuthorityMapper) {
		this.lmsRoleAuthorityMapper = lmsRoleAuthorityMapper;
	}

	public UserResponse toResponse(User user) {
		if (user == null) {
			return null;
		}
		Set<String> roleNames = user.getRoleNames();
		Set<String> permissions = resolvePermissions(roleNames);
		return new UserResponse(
				user.getPublicUuid(),
				user.getFirstName(),
				user.getLastName(),
				user.fullName(),
				user.getEmail(),
				user.getPhone(),
				user.getAvatarUrl(),
				user.getStatus(),
				user.isEmailVerified(),
				user.isMfaEnabled(),
				roleNames,
				permissions,
				user.getLastLoginAt(),
				user.getCreatedAt(),
				user.getUpdatedAt()
		);
	}

	/**
	 * Translates the user's coarse roles into the {@code LMS_*} authority
	 * strings consumed by the frontend. Unknown / null roles are ignored
	 * defensively (mirrors {@link LmsRoleAuthorityMapper#mapAuthorities}).
	 * Returns {@code null} (not an empty set) when the user has no
	 * authorities so the field is omitted from the JSON payload — keeping
	 * the wire format lean for non-LMS tenants.
	 */
	private Set<String> resolvePermissions(Set<String> roleNames) {
		if (roleNames == null || roleNames.isEmpty()) {
			return null;
		}
		Set<String> authorities = new LinkedHashSet<>();
		roleNames.stream()
				.map(this::safeRole)
				.filter(java.util.Objects::nonNull)
				.forEach(role -> authorities.addAll(lmsRoleAuthorityMapper.mapAuthorities(List.of(role))));
		return authorities.isEmpty() ? null : authorities;
	}

	/**
	 * Map a role name (e.g. {@code "TENANT_ADMIN"}) to the
	 * {@link com.edushift.modules.auth.entity.UserRole} enum. Returns
	 * {@code null} for unknown / blank names so the mapper never throws on
	 * forward-compat role names the server doesn't know yet.
	 */
	private com.edushift.modules.auth.entity.UserRole safeRole(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		try {
			return com.edushift.modules.auth.entity.UserRole.valueOf(name);
		}
		catch (IllegalArgumentException e) {
			return null;
		}
	}

	public UserSummary toSummary(User user) {
		if (user == null) {
			return null;
		}
		return new UserSummary(
				user.getPublicUuid(),
				user.fullName(),
				user.getEmail(),
				user.getAvatarUrl(),
				user.getStatus()
		);
	}

	public List<UserResponse> toResponseList(List<User> users) {
		if (users == null || users.isEmpty()) {
			return List.of();
		}
		return users.stream().map(this::toResponse).toList();
	}

	public List<UserSummary> toSummaryList(List<User> users) {
		if (users == null || users.isEmpty()) {
			return List.of();
		}
		return users.stream().map(this::toSummary).toList();
	}

	/**
	 * Builds a new {@link User} from a creation request.
	 *
	 * @param request        the validated incoming payload
	 * @param hashedPassword the password digest produced by the auth service
	 *                       (BCrypt / Argon2) — never the raw password
	 */
	public User toEntity(CreateUserRequest request, String hashedPassword) {
		if (request == null) {
			throw new IllegalArgumentException("CreateUserRequest must not be null");
		}
		if (hashedPassword == null || hashedPassword.isBlank()) {
			throw new IllegalArgumentException("hashedPassword must not be blank");
		}
		User user = new User();
		user.setFirstName(request.firstName());
		user.setLastName(request.lastName());
		user.setEmail(request.email());
		user.setPasswordHash(hashedPassword);
		user.setPhone(request.phone());
		user.setAvatarUrl(request.avatarUrl());
		user.setStatus(UserStatus.PENDING_VERIFICATION);
		user.setEmailVerified(false);
		user.setMfaEnabled(false);
		return user;
	}

	/**
	 * Copies the mutable profile fields from an update request onto the
	 * managed entity. Identity-sensitive and security flags
	 * ({@code email}, {@code passwordHash}, {@code status}, {@code mfaEnabled},
	 * {@code emailVerified}) are intentionally left untouched.
	 */
	public void applyUpdate(User user, UpdateUserRequest request) {
		if (user == null || request == null) {
			return;
		}
		user.setFirstName(request.firstName());
		user.setLastName(request.lastName());
		user.setPhone(request.phone());
		user.setAvatarUrl(request.avatarUrl());
	}

}
