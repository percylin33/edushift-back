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
 *   <li>{@value TenantResolver#TENANT_HEADER} HTTP header (admin / system tools)</li>
 * </ol>
 * The JWT-derived tenant always wins to prevent header spoofing by regular users.
 * The header is used only when no authenticated tenant is available (e.g., for
 * unauthenticated public flows or super-admin tools after a separate access check).
 */
@Component
@RequiredArgsConstructor
public class HttpTenantResolver implements TenantResolver {

	private final CurrentUserProvider currentUserProvider;

	@Override
	public Optional<UUID> resolve(HttpServletRequest request) {
		Optional<UUID> fromPrincipal = currentUserProvider.currentTenantId();
		if (fromPrincipal.isPresent()) {
			return fromPrincipal;
		}
		String headerValue = request.getHeader(TENANT_HEADER);
		if (headerValue == null || headerValue.isBlank()) {
			return Optional.empty();
		}
		return Identifiers.tryParse(headerValue);
	}

}
