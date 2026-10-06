package com.edushift.modules.notifications.service;

import com.edushift.modules.auth.entity.EmailSource;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.students.provision.SyntheticEmailFactory;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
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
				.filter(u -> u.getEmailSource() != EmailSource.SYNTHETIC)
				.map(User::getEmail)
				.filter(e -> e != null && !e.isBlank());
	}

	/** Prefer an explicit email; otherwise look up by public UUID. Skips synthetic mailboxes. */
	public String resolveOrFallback(UUID userPublicUuid, String explicitEmail) {
		if (explicitEmail != null && !explicitEmail.isBlank()) {
			String trimmed = explicitEmail.trim();
			if (SyntheticEmailFactory.isSyntheticAddress(trimmed)) {
				return null;
			}
			return trimmed;
		}
		return resolveEmail(userPublicUuid).orElse(null);
	}

	/**
	 * One lookup for every recipient that did not already bring a
	 * deliverable address. An empty collection does not hit the database.
	 */
	public Map<UUID, String> resolveEmails(Collection<UUID> userPublicUuids) {
		if (userPublicUuids == null || userPublicUuids.isEmpty()) {
			return Map.of();
		}
		Map<UUID, String> emails = new HashMap<>();
		for (User user : userRepository.findAllByPublicUuidIn(userPublicUuids)) {
			if (user.getPublicUuid() == null || user.getEmailSource() == EmailSource.SYNTHETIC) {
				continue;
			}
			String email = user.getEmail();
			if (email == null || email.isBlank() || SyntheticEmailFactory.isSyntheticAddress(email)) {
				continue;
			}
			emails.put(user.getPublicUuid(), email.trim());
		}
		return emails;
	}
}
