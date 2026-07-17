package com.edushift.infrastructure.multitenancy;

import com.edushift.shared.exception.ForbiddenException;
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
 * Runs after Spring Security to refine and validate the tenant binding using
 * the authenticated principal (the JWT-claimed tenant is authoritative).
 * <p>
 * Behavior:
 * <ul>
 *   <li>If the principal exposes a tenant id and the context is empty,
 *       it is bound here.</li>
 *   <li>If both are present and differ, a {@link ForbiddenException} is raised
 *       to block cross-tenant attempts (header spoofing).</li>
 *   <li>If the principal has no tenant, the existing context (e.g. from header
 *       for public flows) is left untouched.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class TenantInterceptor implements HandlerInterceptor {

	private static final String MDC_TENANT_ID = "tenantId";

	private final CurrentUserProvider currentUserProvider;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		// Sprint 15 / BE-15.1: SUPER_ADMIN bypasses tenant filtering entirely.
		// The SUPER_ADMIN can access all tenants; TenantContext is left unset
		// so no automatic Hibernate @TenantId filter is applied.
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getAuthorities().stream()
				.anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()))) {
			return true;
		}

		Optional<UUID> fromPrincipal = currentUserProvider.currentTenantId();
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

}
