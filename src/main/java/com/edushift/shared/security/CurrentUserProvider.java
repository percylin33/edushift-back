package com.edushift.shared.security;

import java.util.Optional;
import java.util.UUID;

/**
 * Port that exposes the currently authenticated user. Implementations read from
 * the {@code SecurityContext} (or any other source) without coupling callers
 * to Spring Security.
 */
public interface CurrentUserProvider {

	Optional<UUID> currentUserId();

	Optional<String> currentUsername();

	Optional<UUID> currentTenantId();

}
