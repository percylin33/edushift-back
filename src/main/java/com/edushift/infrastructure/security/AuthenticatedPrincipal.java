package com.edushift.infrastructure.security;

import java.util.UUID;

/**
 * Contract implemented by the principal stored in the {@code SecurityContext}.
 * The auth module will provide a concrete record/class once authentication is wired.
 */
public interface AuthenticatedPrincipal {

	UUID getId();

	UUID getTenantId();

	String getUsername();

}
