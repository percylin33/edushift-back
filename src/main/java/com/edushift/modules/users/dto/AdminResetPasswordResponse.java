package com.edushift.modules.users.dto;

/**
 * Result of an admin password reset.
 *
 * <p>{@code temporaryPassword} is present only for
 * {@link AdminPasswordResetDelivery#TEMPORARY} and must never be logged
 * or stored by the client beyond the one-time handoff to the user.
 */
public record AdminResetPasswordResponse(
		AdminPasswordResetDelivery delivery,
		String email,
		String temporaryPassword,
		boolean mustChangePassword
) {
	public static AdminResetPasswordResponse email(String email) {
		return new AdminResetPasswordResponse(
				AdminPasswordResetDelivery.EMAIL, email, null, false);
	}

	public static AdminResetPasswordResponse temporary(String email, String temporaryPassword) {
		return new AdminResetPasswordResponse(
				AdminPasswordResetDelivery.TEMPORARY, email, temporaryPassword, true);
	}
}
