package com.edushift.modules.users.service.impl;

import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.events.UserStatusChangeEvent;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.users.dto.AssignRolesRequest;
import com.edushift.modules.users.dto.UpdateUserRequest;
import com.edushift.modules.users.dto.UserDetailResponse;
import com.edushift.modules.users.dto.UserListFilters;
import com.edushift.modules.users.dto.UserListItem;
import com.edushift.modules.users.mapper.UserManagementMapper;
import com.edushift.modules.users.service.UserManagementService;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.exception.UnauthorizedException;
import com.edushift.shared.multitenancy.TenantContext;
import jakarta.persistence.criteria.Predicate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link UserManagementService}.
 *
 * <h3>Tenant scoping</h3>
 * Every {@code UserRepository} call goes through Hibernate's
 * {@code @TenantId} discriminator, so all read/write paths are
 * automatically tenant-scoped. The one exception is
 * {@link UserRepository#countActiveTenantAdmins(UUID)} which is a native
 * query — that one passes the tenant id explicitly via
 * {@link TenantContext#currentRequired()}.
 *
 * <h3>Filtering</h3>
 * The list endpoint composes filters via JPA {@link Specification}
 * (see {@link Specs}). Specifications are easier to test and compose than
 * a custom {@code @Query} that would have to balloon as filters get added,
 * and they preserve the natural feel of the existing
 * {@code findAll(Pageable)} entry point.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementServiceImpl implements UserManagementService {

	private final UserRepository userRepository;
	private final UserManagementMapper mapper;
	/** Sprint 14 / DEBT-AUTH-4: publishes UserStatusChangeEvent on transitions. */
	private final ApplicationEventPublisher eventPublisher;

	// ===========================================================================
	// Read paths
	// ===========================================================================

	@Override
	@Transactional(readOnly = true)
	public Page<UserListItem> listUsers(UserListFilters filters, Pageable pageable) {
		UserListFilters effective = filters == null ? UserListFilters.empty() : filters;
		Specification<User> spec = Specs.combine(effective);
		return userRepository.findAll(spec, pageable).map(mapper::toListItem);
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetailResponse getUser(UUID publicUuid) {
		User user = loadUser(publicUuid);
		return mapper.toDetail(user);
	}

	// ===========================================================================
	// Profile update
	// ===========================================================================

	@Override
	@Transactional
	public UserDetailResponse updateUser(UUID publicUuid, UpdateUserRequest request) {
		User user = loadUser(publicUuid);

		// Short-circuit on a fully-empty patch: there's literally nothing to
		// merge, and avoiding the save() saves a row update + an audit log
		// entry that would be misleading ("user updated" with zero deltas).
		if (request == null || request.isEmpty()) {
			return mapper.toDetail(user);
		}

		mapper.applyUpdate(request, user);
		User saved = userRepository.save(user);
		log.info("[users] profile updated -- publicUuid={} by={}",
				saved.getPublicUuid(), currentAdminPublicUuid());
		return mapper.toDetail(saved);
	}

	// ===========================================================================
	// Role assignment
	// ===========================================================================

	@Override
	@Transactional
	public UserDetailResponse assignRoles(UUID publicUuid, AssignRolesRequest request) {
		User user = loadUser(publicUuid);

		// Translate the request set into UserRole values, surfacing unknowns
		// up-front so we never persist a partial change.
		Set<UserRole> requested = parseRoles(request.roles());
		Set<UserRole> existing = user.getRoleSet();

		boolean isLosingAdmin = existing.contains(UserRole.TENANT_ADMIN)
				&& !requested.contains(UserRole.TENANT_ADMIN);

		if (isLosingAdmin) {
			ensureNotLastAdmin(user, "remove TENANT_ADMIN role from");
		}

		user.setRoleSet(requested);
		User saved = userRepository.save(user);
		log.info("[users] roles changed -- publicUuid={} {} -> {} by={}",
				saved.getPublicUuid(), existing, requested, currentAdminPublicUuid());
		return mapper.toDetail(saved);
	}

	// ===========================================================================
	// Lifecycle: disable / enable / reset password
	// ===========================================================================

	@Override
	@Transactional
	public UserDetailResponse disableUser(UUID publicUuid) {
		User user = loadUser(publicUuid);

		// Self-lockout protection: the request comes from the SecurityContext,
		// and the target user comes from the URL. The only way this guardrail
		// fails is when the admin literally puts their own UUID in the URL.
		if (user.getPublicUuid().equals(currentAdminPublicUuid())) {
			throw new BusinessException("SELF_LOCKOUT",
					"Admins cannot disable their own account");
		}

		if (user.getStatus() == UserStatus.SUSPENDED) {
			// Idempotent: re-disabling a suspended account is a no-op,
			// not a 409. Same rationale as `tenants.activateCurrent`.
			log.debug("[users] disable -- {} already SUSPENDED; no-op", user.getPublicUuid());
			return mapper.toDetail(user);
		}

		if (user.hasRole(UserRole.TENANT_ADMIN) && user.getStatus() == UserStatus.ACTIVE) {
			ensureNotLastAdmin(user, "disable");
		}

		UserStatus oldStatus = user.getStatus();
		user.setStatus(UserStatus.SUSPENDED);
		User saved = userRepository.save(user);
		log.info("[users] disabled -- publicUuid={} by={}",
				saved.getPublicUuid(), currentAdminPublicUuid());

		// DEBT-AUTH-4: publish event so the auth listener can revoke all
		// active refresh tokens for this user. AFTER_COMMIT semantics mean
		// the listener sees the persisted SUSPENDED status.
		eventPublisher.publishEvent(new UserStatusChangeEvent(
				saved.getPublicUuid(),
				oldStatus,
				UserStatus.SUSPENDED,
				"admin-disable",
				currentAdminPublicUuid()));

		return mapper.toDetail(saved);
	}

	@Override
	@Transactional
	public UserDetailResponse enableUser(UUID publicUuid) {
		User user = loadUser(publicUuid);

		if (user.getStatus() == UserStatus.ACTIVE) {
			log.debug("[users] enable -- {} already ACTIVE; no-op", user.getPublicUuid());
			return mapper.toDetail(user);
		}

		if (user.getStatus() != UserStatus.SUSPENDED) {
			// PENDING_VERIFICATION needs the email-verification flow
			// (Sprint 9); LOCKED needs the unlock flow; INACTIVE needs the
			// reactivation flow. Lumping them here would hide real
			// remediation steps from admins.
			throw new ConflictException("USER_NOT_ENABLEABLE",
					"User cannot be enabled from status " + user.getStatus()
							+ "; use the matching dedicated flow");
		}

		UserStatus oldStatus = user.getStatus();
		user.setStatus(UserStatus.ACTIVE);
		User saved = userRepository.save(user);
		log.info("[users] enabled -- publicUuid={} by={}",
				saved.getPublicUuid(), currentAdminPublicUuid());

		// DEBT-AUTH-4: publish event. Re-enabling does NOT touch existing
		// refresh tokens (the listener only acts on ACTIVE→non-AUTHENTICATABLE
		// transitions). Listed here for symmetry / future hooks.
		eventPublisher.publishEvent(new UserStatusChangeEvent(
				saved.getPublicUuid(),
				oldStatus,
				UserStatus.ACTIVE,
				"admin-enable",
				currentAdminPublicUuid()));

		return mapper.toDetail(saved);
	}

	@Override
	@Transactional
	public void resetPassword(UUID publicUuid) {
		User user = loadUser(publicUuid);
		// Sprint 3 surface: load + log the intent; Sprint 9 wires the
		// notifications module to actually send the reset email. The
		// status flip can be added when that lands.
		log.info("[users] reset-password requested -- publicUuid={} by={} (TODO Sprint 9: send email)",
				user.getPublicUuid(), currentAdminPublicUuid());
	}

	// ===========================================================================
	// Internals
	// ===========================================================================

	private User loadUser(UUID publicUuid) {
		return userRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("User", publicUuid));
	}

	private Set<UserRole> parseRoles(Set<String> names) {
		Set<UserRole> resolved = new LinkedHashSet<>();
		for (String name : names) {
			UserRole role = UserRole.fromName(name);
			if (role == null) {
				// Surface the offending name so admins can fix typos
				// without trial-and-error against the API.
				throw new BusinessException("INVALID_ROLE",
						"Unknown role: '" + name + "'. Valid roles: "
								+ allRoleNames());
			}
			resolved.add(role);
		}
		return resolved;
	}

	private String allRoleNames() {
		return java.util.Arrays.stream(UserRole.values())
				.map(UserRole::name)
				.collect(Collectors.joining(", "));
	}

	private void ensureNotLastAdmin(User candidate, String operationLabel) {
		UUID tenantId = TenantContext.currentRequired();
		long activeAdmins = userRepository.countActiveTenantAdmins(tenantId);

		// `candidate` is the user being modified. If they currently count as
		// an active admin, removing them drops the population by 1; we
		// require that removing them still leaves at least one admin behind.
		boolean candidateCountsAsAdmin = candidate.hasRole(UserRole.TENANT_ADMIN)
				&& candidate.getStatus() == UserStatus.ACTIVE;
		long postOperation = candidateCountsAsAdmin ? activeAdmins - 1 : activeAdmins;

		if (postOperation < 1) {
			log.warn("[users] last-admin protection tripped -- tenant={} target={} op='{}'",
					tenantId, candidate.getPublicUuid(), operationLabel);
			throw new ConflictException("LAST_ADMIN_PROTECTION",
					"Cannot " + operationLabel + " the last active TENANT_ADMIN of the tenant");
		}
	}

	private UUID currentAdminPublicUuid() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || auth.getName() == null) {
			throw new UnauthorizedException("AUTHENTICATION_REQUIRED",
					"Authentication required");
		}
		try {
			return UUID.fromString(auth.getName());
		}
		catch (IllegalArgumentException e) {
			throw new UnauthorizedException("INVALID_PRINCIPAL",
					"Authentication principal is not a valid UUID");
		}
	}

	// ===========================================================================
	// Specifications
	// ===========================================================================

	/**
	 * Composable filter predicates for the list endpoint. Kept package-
	 * private and static so they can be unit-tested directly against an
	 * in-memory criteria builder if needed.
	 */
	static final class Specs {
		private Specs() { }

		static Specification<User> combine(UserListFilters f) {
			Specification<User> spec = Specification.where(null);
			if (f.search() != null && !f.search().isBlank()) {
				spec = spec.and(searchLike(f.search().trim()));
			}
			if (f.status() != null) {
				spec = spec.and(byStatus(f.status()));
			}
			if (f.role() != null && !f.role().isBlank()) {
				spec = spec.and(byRole(f.role().trim()));
			}
			return spec;
		}

		private static Specification<User> searchLike(String needle) {
			final String pattern = "%" + needle.toLowerCase() + "%";
			return (root, q, cb) -> {
				Predicate emailHit = cb.like(cb.lower(root.get("email")), pattern);
				Predicate firstHit = cb.like(cb.lower(root.get("firstName")), pattern);
				Predicate lastHit = cb.like(cb.lower(root.get("lastName")), pattern);
				return cb.or(emailHit, firstHit, lastHit);
			};
		}

		private static Specification<User> byStatus(UserStatus status) {
			return (root, q, cb) -> cb.equal(root.get("status"), status);
		}

		/**
		 * Filter by role. The {@code roles} column is a Postgres
		 * {@code varchar[]}, which JPA Criteria can't query natively, so we
		 * fall back to a {@code function('array_position', ...) IS NOT NULL}
		 * trick: it returns a non-null index when the role name is found.
		 * This works on Postgres (the only DB we target) and stays out of
		 * native-SQL territory.
		 */
		private static Specification<User> byRole(String roleName) {
			return (root, q, cb) -> cb.isNotNull(
					cb.function("array_position", Integer.class,
							root.get("roles"), cb.literal(roleName)));
		}
	}
}
