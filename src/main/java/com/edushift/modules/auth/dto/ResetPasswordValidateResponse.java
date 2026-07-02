package com.edushift.modules.auth.dto;

import java.time.Instant;

/**
 * Result of {@code GET /auth/reset-password/validate?token=...}.
 *
 * <p>Returned to the FE so the reset-password page can render the right
 * UX (e.g. show "this link expired" before the user types a new password).
 * We deliberately do NOT return the user's email here — anti-enumeration.
 *
 * @param valid        true when the token is redeemable right now
 * @param tenantName   tenant display name (so the FE can show "Reset
 *                     password for &lt;tenantName&gt;")
 * @param tenantSlug   tenant slug (for branding / sub-domain routing)
 * @param expiresAt    absolute expiration timestamp; useful for a countdown
 *                     if the FE wants one
 * @param reasonCode   when {@code valid=false}, a stable code identifying
 *                     the failure mode (e.g. {@code RESET_TOKEN_EXPIRED},
 *                     {@code RESET_TOKEN_USED}, {@code RESET_TOKEN_SUPERSEDED})
 */
public record ResetPasswordValidateResponse(
		boolean valid,
		String tenantName,
		String tenantSlug,
		Instant expiresAt,
		String reasonCode
) {
}