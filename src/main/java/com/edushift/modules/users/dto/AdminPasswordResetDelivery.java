package com.edushift.modules.users.dto;

/**
 * How an admin-triggered password reset is delivered to the user.
 */
public enum AdminPasswordResetDelivery {

	/** Queues the same forgot-password email (link, TTL ~1h). */
	EMAIL,

	/**
	 * Sets a random temporary password, forces {@code mustChangePassword},
	 * and returns the plaintext once in the API response (no email).
	 */
	TEMPORARY
}
