package com.edushift.infrastructure.integrations.google;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed configuration for the Google integration family.
 *
 * <p>Bound from the {@code app.integrations.google.*} and
 * {@code app.integrations.gmail.*} property trees in
 * {@code application.properties}. All secrets must come from environment
 * variables; defaults are documented in the migration / README so dev
 * boxes can opt-in with placeholder values.
 *
 * <h3>Why a single record for both Google + Gmail</h3>
 * Both share the same OAuth client (same {@code client-id} /
 * {@code client-secret}). Splitting into two records would force callers
 * to inject both and keep them in sync. The Gmail-only fields are nullable
 * so a deployment that disables Gmail still wires cleanly.
 *
 * <h3>Why {@code scopes} is a {@code List<String>}</h3>
 * Spring Boot's relaxed binding handles comma-separated env vars
 * ({@code GOOGLE_SCOPES=openid,email,profile,...}). The list is exposed
 * verbatim to {@code GoogleLoginProvider.newBuilder()} so the front-end
 * and back-end agree on the OAuth popup scopes.
 *
 * @param enabled              feature flag for Google Login at all
 * @param clientId             OAuth Web Client ID (env GOOGLE_OAUTH_CLIENT_ID)
 * @param clientSecret         OAuth Web Client secret (env GOOGLE_OAUTH_CLIENT_SECRET; unused
 *                             on the login-only path because we never hit Google's
 *                             token-exchange endpoint — id_token verification is signature-only)
 * @param scopes               default scopes to request at login
 * @param encryptionKeyBase64  base64-encoded 32-byte AES key used to encrypt Google refresh
 *                             tokens at rest (env GOOGLE_TOKEN_ENCRYPTION_KEY). Required for
 *                             Gmail send (PR2); login alone tolerates an absent key because
 *                             PR1 doesn't persist refresh tokens.
 * @param dailySendCapPerUser  per-user daily send quota (PR2)
 */
@Validated
@ConfigurationProperties(prefix = "app.integrations")
public record GoogleProperties(
		Google google,
		Gmail gmail
) {

	public record Google(
			boolean enabled,
			@NotBlank String clientId,
			String clientSecret
	) {}

	public record Gmail(
			List<String> scopes,
			String encryptionKeyBase64,
			int dailySendCapPerUser
	) {}
}