package com.edushift.infrastructure.integrations.google;

/**
 * Abstraction over Google OAuth token verification.
 *
 * <p>Implementations must:
 * <ul>
 *   <li>Verify the id_token signature against Google's published JWKS
 *       (no client-side trust).</li>
 *   <li>Verify {@code aud == clientId}, {@code iss == https://accounts.google.com}
 *       (or {@code accounts.google.com} for older audiences), and
 *       {@code exp}/{@code nbf} within clock skew.</li>
 *   <li>Return a {@link GoogleProfile} carrying the canonical fields we
 *       persist on the user row.</li>
 * </ul>
 *
 * <h3>Why an interface</h3>
 * Two reasons:
 * <ol>
 *   <li>Testability — the {@code GoogleAuthService} unit tests can inject a
 *       stub that returns canned {@link GoogleProfile}s without hitting
 *       Google's network.</li>
 *   <li>Future-proofing — when Microsoft / Apple SSO lands (per
 *       {@code auth-rules.mdc §GOOGLE LOGIN} "Never tightly couple auth
 *       providers"), we'll add a sibling {@code MicrosoftIdentityProvider}
 *       behind a common {@code ExternalIdentityProvider} interface.</li>
 * </ol>
 *
 * <p>This interface is intentionally narrow: only what the login flow
 * needs today. Token exchange + refresh are deferred to PR2 (Gmail send).
 */
public interface GoogleIdentityProvider {

	/**
	 * Verifies a Google {@code id_token} and returns the decoded profile.
	 *
	 * @param idToken raw JWT string returned by the front-end's OAuth popup
	 * @return verified profile; never null
	 * @throws com.edushift.shared.exception.UnauthorizedException with code
	 *         {@code INVALID_GOOGLE_TOKEN} when the token is malformed,
	 *         signed by the wrong key, expired, or addressed to a different
	 *         client id
	 */
	GoogleProfile verifyIdToken(String idToken);

	/** True when the integration is enabled at runtime (feature flag). */
	boolean isEnabled();
}