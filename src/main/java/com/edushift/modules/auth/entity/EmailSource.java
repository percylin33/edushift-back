package com.edushift.modules.auth.entity;

/**
 * Origin of {@link User#getEmail()}.
 *
 * <p>{@code SYNTHETIC} emails are generated during mass section
 * provisioning (CRED-7). The mailer must not deliver to them until the
 * user replaces the address with a real mailbox ({@code REAL}).
 */
public enum EmailSource {
	REAL,
	SYNTHETIC;

	public static EmailSource fromName(String name) {
		if (name == null || name.isBlank()) {
			return REAL;
		}
		try {
			return EmailSource.valueOf(name.trim().toUpperCase());
		}
		catch (IllegalArgumentException ignored) {
			return REAL;
		}
	}
}
