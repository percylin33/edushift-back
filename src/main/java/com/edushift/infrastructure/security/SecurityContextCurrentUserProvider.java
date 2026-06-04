package com.edushift.infrastructure.security;

import com.edushift.shared.security.CurrentUserProvider;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
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
