package com.edushift.infrastructure.multitenancy;

import com.edushift.shared.multitenancy.TenantContext;
import com.edushift.shared.multitenancy.TenantResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves and binds the current tenant for each HTTP request.
 * <p>
 * Runs once per request. The binding lives in {@link TenantContext} (a
 * {@link ThreadLocal}) and is mirrored to {@code MDC} as {@code tenantId} so
 * structured logs are auto-tagged.
 * <p>
 * Always clears the context in {@code finally} to prevent value leakage across
 * pooled Tomcat threads.
 */
@RequiredArgsConstructor
public class TenantFilter extends OncePerRequestFilter {

	private static final String MDC_TENANT_ID = "tenantId";

	private final TenantResolver tenantResolver;

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain chain) throws ServletException, IOException {
		try {
			tenantResolver.resolve(request).ifPresent(this::bind);
			chain.doFilter(request, response);
		}
		finally {
			TenantContext.clear();
			MDC.remove(MDC_TENANT_ID);
		}
	}

	private void bind(UUID tenantId) {
		TenantContext.set(tenantId);
		MDC.put(MDC_TENANT_ID, tenantId.toString());
	}

}
