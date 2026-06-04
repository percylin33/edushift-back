package com.edushift.shared.multitenancy;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;

/**
 * Strategy that extracts the current tenant from an HTTP request.
 * <p>
 * Implementations can inspect headers, the authenticated principal, subdomains,
 * path variables, or any combination thereof.
 */
public interface TenantResolver {

	/** HTTP header used to carry an explicit tenant id. */
	String TENANT_HEADER = "X-Tenant-Id";

	Optional<UUID> resolve(HttpServletRequest request);

}
