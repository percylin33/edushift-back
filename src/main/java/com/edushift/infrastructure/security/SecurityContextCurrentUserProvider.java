package com.edushift.infrastructure.security;

import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.shared.security.CurrentUserProvider;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Reads the current user from Spring Security's {@link SecurityContextHolder}.
 * <p>
 * Resolution order for the user id:
 * <ol>
 *   <li>{@link AuthenticatedPrincipal#getId()} when the principal implements it</li>
 *   <li>{@link Authentication#getName()} parsed as {@link UUID}</li>
 * </ol>
 * Tenant resolution is delegated to {@link AuthenticatedPrincipal#getTenantId()}.
 * Returns {@link Optional#empty()} for unauthenticated or anonymous requests.
 */
@Component
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {

    /**
     * Resolved lazily so this provider stays a leaf bean in the wiring
     * graph (the StudentRepository pulls in JPA / Hibernate, which
     * pulls in the security filter chain — a {@code @Lazy} breaks the
     * cycle without forcing the principal to query the DB on every
     * request).
     */
    private final StudentRepository studentRepository;

    public SecurityContextCurrentUserProvider(@Lazy StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

	@Override
	public Optional<UUID> currentUserId() {
		return authentication()
				.map(Authentication::getPrincipal)
				.flatMap(this::resolveUserId);
	}

	@Override
	public Optional<String> currentUsername() {
		return authentication().map(Authentication::getName);
	}

	@Override
	public Optional<UUID> currentTenantId() {
		return authentication()
				.map(Authentication::getPrincipal)
				.flatMap(principal -> principal instanceof AuthenticatedPrincipal ap
						? Optional.ofNullable(ap.getTenantId())
						: Optional.empty());
	}

	@Override
	public Optional<UUID> currentStudentPublicUuid() {
		return currentUserId().flatMap(studentRepository::findPublicUuidByUserId);
	}

	/**
	 * Resolves the caller's primary {@link UserRole} from the JWT's
	 * {@code ROLE_*} authorities. The filter maps coarse roles
	 * (e.g. {@code ROLE_STUDENT}) on top of the granular {@code LMS_*}
	 * authorities, so we read them straight off the
	 * {@link Authentication}. Returns the first recognized role, in
	 * iteration order, so a user with multiple roles (e.g. TEACHER +
	 * STUDENT) gets a deterministic, but not necessarily "highest",
	 * answer — services that need to branch on role should prefer
	 * the explicit {@code @PreAuthorize} gate and only use this for
	 * ownership branches where the gate has already passed.
	 */
	@Override
	public Optional<com.edushift.modules.auth.entity.UserRole> currentUserRole() {
		return authentication()
				.map(Authentication::getAuthorities)
				.stream()
				.flatMap(java.util.Collection::stream)
				.map(GrantedAuthority::getAuthority)
				.filter(a -> a != null && a.startsWith("ROLE_"))
				.map(a -> UserRole.fromName(a.substring("ROLE_".length())))
				.filter(java.util.Objects::nonNull)
				.findFirst();
	}

	private Optional<Authentication> authentication() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
			return Optional.empty();
		}
		return Optional.of(auth);
	}

	private Optional<UUID> resolveUserId(Object principal) {
		if (principal instanceof AuthenticatedPrincipal ap) {
			return Optional.ofNullable(ap.getId());
		}
		if (principal instanceof String name) {
			try {
				return Optional.of(UUID.fromString(name));
			}
			catch (IllegalArgumentException ignored) {
				return Optional.empty();
			}
		}
		return Optional.empty();
	}

}
