package com.edushift.infrastructure.multitenancy;

import com.edushift.shared.exception.ForbiddenException;
import com.edushift.shared.exception.NotFoundException;
import com.edushift.shared.multitenancy.TenantContext;
import com.edushift.shared.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Runs after Spring Security to validate the authenticated tenant against the
 * tenant selected by the request host and the user's account membership.
 * <p>
 * Behavior:
 * <ul>
 *   <li>A school subdomain is resolved against the tenant catalog; unknown
 *       school hosts are rejected and login slugs must match the host.</li>
 *   <li>For tenant-scoped authenticated users, the tenant in the principal
 *       must match the host tenant and any already-bound context. The explicit
 *       {@code SUPER_ADMIN} role retains its cross-tenant bypass.</li>
 *   <li>Public flows may use the resolved host tenant to select a school, but
 *       the host or slug alone does not authenticate a user.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class TenantInterceptor implements HandlerInterceptor {

	private static final String MDC_TENANT_ID = "tenantId";

	private final CurrentUserProvider currentUserProvider;
	private final TenantHostResolver tenantHostResolver;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		Optional<TenantHostResolver.Resolution> hostTenant = tenantHostResolver.resolve(request);
		if (hostTenant.isPresent()) {
			TenantHostResolver.Resolution resolution = hostTenant.get();
			if (!resolution.exists()) {
				throw new NotFoundException("TENANT_NOT_FOUND", "School subdomain is not registered");
			}

			if (isLoginRequest(request)) {
				String loginSlug = request.getHeader("X-Tenant-Slug");
				if (loginSlug != null && !resolution.canonicalSlug().equalsIgnoreCase(loginSlug.trim())) {
					throw new ForbiddenException("TENANT_MISMATCH",
							"Login tenant does not match the school subdomain");
				}
			}
		}

		// Sprint 15 / BE-15.1: SUPER_ADMIN bypasses tenant filtering entirely.
		// The SUPER_ADMIN can access all tenants; TenantContext is left unset
		// so no automatic Hibernate @TenantId filter is applied.
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getAuthorities().stream()
				.anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()))) {
			return true;
		}

		Optional<UUID> fromPrincipal = currentUserProvider.currentTenantId();
		if (fromPrincipal.isPresent() && hostTenant.isPresent()
				&& !fromPrincipal.get().equals(hostTenant.get().tenantId())) {
			throw new ForbiddenException("TENANT_MISMATCH",
					"Authenticated tenant does not match the school subdomain");
		}
		if (fromPrincipal.isEmpty()) {
			return true;
		}
		UUID principalTenant = fromPrincipal.get();
		Optional<UUID> current = TenantContext.current();
		if (current.isPresent() && !current.get().equals(principalTenant)) {
			throw new ForbiddenException("TENANT_MISMATCH",
					"Authenticated tenant does not match the requested tenant");
		}
		TenantContext.set(principalTenant);
		MDC.put(MDC_TENANT_ID, principalTenant.toString());
		return true;
	}

	private static boolean isLoginRequest(HttpServletRequest request) {
		String uri = request.getRequestURI();
		return uri.endsWith("/auth/login") || uri.endsWith("/auth/google");
	}

}
