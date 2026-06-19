package com.edushift.shared.security;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the current authenticated user from the security context.
 *
 * <p>The default implementation lives in
 * {@code edushift.infrastructure.security.SecurityContextCurrentUserProvider}
 * and reads the principal from Spring's {@code SecurityContextHolder}.</p>
 *
 * <p>The user is identified by a UUID (public id, not the internal
 * {@code Long} primary key — see ADR-1.2 about UUID v7 as the
 * public identifier).</p>
 */
public interface CurrentUserProvider {

    /** UUID of the current authenticated user, empty if anonymous. */
    Optional<UUID> currentUserId();

    /** Username of the current authenticated user, empty if anonymous. */
    Optional<String> currentUsername();

    /** Tenant id of the current authenticated user, empty if anonymous. */
    Optional<UUID> currentTenantId();
}
