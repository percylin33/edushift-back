package com.edushift.modules.notifications.service;

import com.edushift.modules.auth.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves SMTP recipient addresses from {@code users.public_uuid}.
 * Used by publishers and as a defense-in-depth fallback in
 * {@link com.edushift.modules.notifications.event.NotificationEventListener}.
 */
@Component
@RequiredArgsConstructor
public class RecipientEmailResolver {

	private final UserRepository userRepository;

	public Optional<String> resolveEmail(UUID userPublicUuid) {
		if (userPublicUuid == null) {
			return Optional.empty();
		}
		return userRepository.findByPublicUuid(userPublicUuid)
				.map(u -> u.getEmail())
				.filter(e -> e != null && !e.isBlank());
	}

	/** Prefer an explicit email; otherwise look up by public UUID. */
	public String resolveOrFallback(UUID userPublicUuid, String explicitEmail) {
		if (explicitEmail != null && !explicitEmail.isBlank()) {
			return explicitEmail.trim();
		}
		return resolveEmail(userPublicUuid).orElse(null);
	}
}
