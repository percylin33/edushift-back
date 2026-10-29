package com.edushift.infrastructure.multitenancy;

import com.edushift.shared.identifier.Identifiers;
import com.edushift.shared.multitenancy.TenantResolver;
import com.edushift.shared.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Default tenant resolver.
 * <p>
 * Resolution order:
 * <ol>
 *   <li>Authenticated principal (JWT claim) via {@link CurrentUserProvider}</li>
 *   <li>Tenant subdomain resolved through the global tenant catalog</li>
 *   <li>{@value TenantResolver#TENANT_HEADER} for internal tooling on non-tenant hosts</li>
 * </ol>
 * The principal always wins. A subdomain selects the tenant context for public
 * tenant-scoped flows, while {@link TenantInterceptor} verifies it against the
 * authenticated principal before protected handlers run.
 */
@Component
@RequiredArgsConstructor
public class HttpTenantResolver implements TenantResolver {

	private final CurrentUserProvider currentUserProvider;
	private final TenantHostResolver tenantHostResolver;

	@Override
	public Optional<UUID> resolve(HttpServletRequest request) {
		Optional<UUID> fromPrincipal = currentUserProvider.currentTenantId();
		if (fromPrincipal.isPresent()) {
			return fromPrincipal;
		}
		Optional<TenantHostResolver.Resolution> fromHost = tenantHostResolver.resolve(request);
		if (fromHost.isPresent()) {
			// A recognized but unknown school host must not fall back to a
			// caller-supplied tenant id. TenantInterceptor returns a 404 for it.
			return fromHost.filter(TenantHostResolver.Resolution::exists)
					.map(TenantHostResolver.Resolution::tenantId);
		}
		String headerValue = request.getHeader(TENANT_HEADER);
		if (headerValue == null || headerValue.isBlank()) {
			return Optional.empty();
		}
		return Identifiers.tryParse(headerValue);
	}

}
