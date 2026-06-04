package com.edushift.shared.identifier;

import java.util.Optional;
import java.util.UUID;

/**
 * Static helpers for identifier handling at module boundaries (controllers,
 * message consumers, integrations).
 */
public final class Identifiers {

	private Identifiers() {
	}

	/** Parses a string into a UUID, returning empty when the input is invalid. */
	public static Optional<UUID> tryParse(String value) {
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}
		try {
			return Optional.of(UUID.fromString(value));
		}
		catch (IllegalArgumentException ignored) {
			return Optional.empty();
		}
	}

	public static boolean isValid(String value) {
		return tryParse(value).isPresent();
	}

	/** True when the UUID encodes the EduShift convention (RFC 9562 v7). */
	public static boolean isV7(UUID uuid) {
		return uuid != null && uuid.version() == 7;
	}

}
