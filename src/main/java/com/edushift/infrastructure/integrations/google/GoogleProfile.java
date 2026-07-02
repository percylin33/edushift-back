package com.edushift.infrastructure.integrations.google;

import java.util.List;

/**
 * Verified profile extracted from a Google {@code id_token}.
 *
 * <p>Only the fields we actually consume downstream. We intentionally do
 * NOT expose Google's {@code azp} (authorized party), {@code nonce}, or
 * {@code iat}/{@code exp} — those are checked inside the verifier and
 * discarded.
 *
 * @param subject       stable Google user id ({@code sub}); never changes
 *                      for a given Google account, even if the email does
 * @param email         primary email Google returned for this login
 * @param emailVerified whether Google attests the email was verified
 *                      (drives our {@link com.edushift.modules.auth.entity.UserStatus}
 *                      transition during auto-provisioning)
 * @param givenName     first name (may be null if user withheld)
 * @param familyName    last name (may be null if user withheld)
 * @param pictureUrl    profile picture URL (may be null); we copy to
 *                      {@code users.avatar_url} on first login
 * @param hd            hosted domain for Google Workspace accounts; null for
 *                      consumer Gmail accounts. Lets us surface a richer
 *                      audit trail in PR2
 * @param scopes        scopes the user consented to (filled in PR2 once we
 *                      start storing the refresh_token)
 */
public record GoogleProfile(
		String subject,
		String email,
		boolean emailVerified,
		String givenName,
		String familyName,
		String pictureUrl,
		String hd,
		List<String> scopes
) {

	/** Compact display name: falls back to the email local-part if no name. */
	public String displayName() {
		String given = givenName == null ? "" : givenName.trim();
		String family = familyName == null ? "" : familyName.trim();
		String combined = (given + " " + family).trim();
		return combined.isEmpty() && email != null
				? email.substring(0, email.indexOf('@'))
				: combined;
	}
}