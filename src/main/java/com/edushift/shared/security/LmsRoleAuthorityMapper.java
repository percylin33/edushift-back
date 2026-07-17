package com.edushift.shared.security;

import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.tenants.service.PermissionOverrideService;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Maps a set of coarse user roles to a set of granular
 * {@code LMS_*} authorities (Sprint 7a / BE-7a.3).
 *
 * <h3>Resolution algorithm</h3>
 * <ol>
 *   <li>Start from the role's <b>platform default</b> set, declared in
 *       {@link #DEFAULTS}.</li>
 *   <li>Apply the tenant's <b>explicit grants</b> from
 *       {@link PermissionOverrideService#snapshotFor} → union.</li>
 *   <li>Subtract the tenant's <b>explicit revocations</b> from
 *       {@link PermissionOverrideService#revokedFor}.</li>
 * </ol>
 * This is total-replacement-per-role (decision Q2 option (c),
 * {@code docs/qa/11-decisiones-pendientes.md}). Adding and subtracting
 * produces the same final set as the documented "reemplazo total"
 * behavior because the platform default is always in the picture;
 * absent override → default wins; present override → it wins.
 *
 * <h3>Why constructor injection with {@code @Lazy}</h3>
 * {@code PermissionOverrideService} depends on JPA, which depends on
 * Hibernate's {@code @TenantId} discriminator which depends on the
 * security filter chain. The mapper used to be a simple bean with no
 * dependencies — making it depend on the service would normally
 * introduce a circular bean dependency at startup. {@code @Lazy}
 * defers the wiring until first use (i.e. the first JWT mint after
 * application ready), by which point the cycles are resolved.
 *
 * <h3>Backward-compat</h3>
 * {@link #grantFor(UserRole)} remains as the pure-default variant for
 * callers that don't have a tenant context (tests, the JUnit
 * test-class {@code LmsRoleAuthorityMapperTest}). The new public API
 * is {@link #resolveFor(UUID, Collection)}.
 */
@Component
public class LmsRoleAuthorityMapper {

	private static final Map<UserRole, Set<String>> DEFAULTS = new EnumMap<>(UserRole.class);

	static {
		DEFAULTS.put(UserRole.TENANT_ADMIN, Set.of(
				LmsAuthorities.LMS_TASK_READ,
				LmsAuthorities.LMS_TASK_CREATE,
				LmsAuthorities.LMS_TASK_GRADE,
				LmsAuthorities.LMS_TASK_SUBMIT,
				LmsAuthorities.LMS_MATERIAL_READ,
				LmsAuthorities.LMS_MATERIAL_WRITE,
				LmsAuthorities.LMS_MATERIAL_DELETE,
				LmsAuthorities.LMS_QUIZ_READ,
				LmsAuthorities.LMS_QUIZ_CREATE,
				LmsAuthorities.LMS_QUIZ_GRADE,
				LmsAuthorities.LMS_QUIZ_SUBMIT,
				LmsAuthorities.LMS_AI_GENERATE,
				LmsAuthorities.LMS_PAYMENT_ADMIN,
				LmsAuthorities.LMS_ANNOUNCEMENTS_CREATE,
				LmsAuthorities.LMS_NOTIFICATIONS_MANAGE,
				LmsAuthorities.LMS_AI_USAGE
		));
		DEFAULTS.put(UserRole.TEACHER, Set.of(
				LmsAuthorities.LMS_TASK_READ,
				LmsAuthorities.LMS_TASK_CREATE,
				LmsAuthorities.LMS_TASK_GRADE,
				LmsAuthorities.LMS_MATERIAL_READ,
				LmsAuthorities.LMS_MATERIAL_WRITE,
				LmsAuthorities.LMS_MATERIAL_DELETE,
				LmsAuthorities.LMS_QUIZ_READ,
				LmsAuthorities.LMS_QUIZ_CREATE,
				LmsAuthorities.LMS_QUIZ_GRADE,
				LmsAuthorities.LMS_AI_GENERATE
		));
		DEFAULTS.put(UserRole.STUDENT, Set.of(
				LmsAuthorities.LMS_TASK_READ,
				LmsAuthorities.LMS_TASK_SUBMIT,
				LmsAuthorities.LMS_MATERIAL_READ,
				LmsAuthorities.LMS_QUIZ_READ,
				LmsAuthorities.LMS_QUIZ_SUBMIT
		));
		DEFAULTS.put(UserRole.PARENT, Set.of(
				LmsAuthorities.LMS_TASK_READ,
				LmsAuthorities.LMS_TASK_SUBMIT,
				LmsAuthorities.LMS_MATERIAL_READ,
				LmsAuthorities.LMS_QUIZ_READ,
				LmsAuthorities.LMS_QUIZ_SUBMIT
		));
		DEFAULTS.put(UserRole.STAFF, Set.of(
				LmsAuthorities.LMS_TASK_READ,
				LmsAuthorities.LMS_MATERIAL_READ,
				LmsAuthorities.LMS_QUIZ_READ,
				LmsAuthorities.LMS_PAYMENT_ADMIN
		));
		// SUPER_ADMIN intentionally not in DEFAULTS — its authority set is
		// assembled inline in resolveFor() because it bypasses tenant overrides.
	}

	private final PermissionOverrideService overrideService;

	public LmsRoleAuthorityMapper(@Lazy PermissionOverrideService overrideService) {
		this.overrideService = overrideService;
	}

	/**
	 * Resolves the final authority set for a user authenticated in a
	 * tenant. Combines platform defaults with tenant overrides per the
	 * algorithm in the class javadoc.
	 *
	 * @param tenantId the caller's tenant; {@code null} for SUPER_ADMIN
	 *                 (whose authorities are platform-defined and
	 *                 immune to tenant overrides).
	 * @param roles    the user's coarse roles.
	 */
	public Set<String> resolveFor(UUID tenantId, Collection<UserRole> roles) {
		if (roles == null || roles.isEmpty()) {
			return Set.of();
		}
		Set<String> out = new LinkedHashSet<>();
		boolean containsSuper = roles.contains(UserRole.SUPER_ADMIN);
		for (UserRole role : roles) {
			if (role == null) continue;
			if (role == UserRole.SUPER_ADMIN) {
				out.addAll(superAdminAuthorities());
			} else {
				out.addAll(resolveForNonSuper(tenantId, role));
			}
		}
		return out;
	}

	/**
	 * Pure-default variant. Kept for tests and for callers that
	 * genuinely don't have tenant context. Equivalent to
	 * {@code resolveFor(null, List.of(role))} except it skips the
	 * (defensive) super-admin path.
	 */
	Set<String> grantFor(UserRole role) {
		if (role == UserRole.SUPER_ADMIN) {
			return superAdminAuthorities();
		}
		return DEFAULTS.getOrDefault(role, Set.of());
	}

	private Set<String> resolveForNonSuper(UUID tenantId, UserRole role) {
		// Default
		Set<String> result = new LinkedHashSet<>(DEFAULTS.getOrDefault(role, Set.of()));
		if (tenantId == null) {
			return result;
		}
		// Tenant explicit grants
		Set<String> grants = overrideService.snapshotFor(tenantId)
				.getOrDefault(role, Set.of());
		result.addAll(grants);
		// Tenant explicit revocations (override default true → false)
		Set<String> revokes = overrideService.revokedFor(tenantId)
				.getOrDefault(role, Set.of());
		result.removeAll(revokes);
		return result;
	}

	private Set<String> superAdminAuthorities() {
		// SUPER_ADMIN has every LMS_* authority. Hard-coded (not via DEFAULTS)
		// to make the bypass explicit and to anchor the comment that
		// SUPER_ADMIN cannot be customised per tenant.
		return Set.of(
				LmsAuthorities.LMS_TASK_READ,
				LmsAuthorities.LMS_TASK_CREATE,
				LmsAuthorities.LMS_TASK_GRADE,
				LmsAuthorities.LMS_TASK_SUBMIT,
				LmsAuthorities.LMS_MATERIAL_READ,
				LmsAuthorities.LMS_MATERIAL_WRITE,
				LmsAuthorities.LMS_MATERIAL_DELETE,
				LmsAuthorities.LMS_QUIZ_READ,
				LmsAuthorities.LMS_QUIZ_CREATE,
				LmsAuthorities.LMS_QUIZ_GRADE,
				LmsAuthorities.LMS_QUIZ_SUBMIT,
				LmsAuthorities.LMS_AI_GENERATE,
				LmsAuthorities.LMS_AI_USAGE,
				LmsAuthorities.LMS_PAYMENT_ADMIN,
				LmsAuthorities.LMS_ANNOUNCEMENTS_CREATE,
				LmsAuthorities.LMS_NOTIFICATIONS_MANAGE
		);
	}

	// -----------------------------------------------------------------
	// Legacy API kept for JWT filter and LmsRoleAuthorityMapperTest.
	// Both call sites have been updated to resolveFor() — this method is
	// still exposed for unit tests that don't have tenant context.
	// -----------------------------------------------------------------

	public Set<String> mapAuthorities(Collection<UserRole> roles) {
		return resolveFor(null, roles);
	}
}
