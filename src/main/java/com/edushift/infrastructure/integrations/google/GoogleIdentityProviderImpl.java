package com.edushift.infrastructure.integrations.google;

import com.edushift.shared.exception.UnauthorizedException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Default {@link GoogleIdentityProvider} backed by Google's official
 * {@code google-api-client} library.
 *
 * <h3>Verification pipeline</h3>
 * {@link GoogleIdTokenVerifier} does the heavy lifting:
 * <ol>
 *   <li>Parses the JWT header to discover the {@code kid}.</li>
 *   <li>Looks up the signing key in Google's JWKS
 *       ({@code https://www.googleapis.com/oauth2/v3/certs}). The verifier
 *       caches JWKS responses for ~5 min and refreshes on unknown {@code kid}.</li>
 *   <li>Verifies the RS256 signature.</li>
 *   <li>Validates {@code aud} matches the configured client id (configurable
 *       per verifier instance — we set it once).</li>
 *   <li>Validates {@code iss} is one of Google's known issuers.</li>
 *   <li>Validates {@code exp} / {@code nbf} within clock skew.</li>
 * </ol>
 *
 * <p>Anything that fails surfaces as a
 * {@link com.edushift.shared.exception.UnauthorizedException} with code
 * {@code INVALID_GOOGLE_TOKEN} so the controller layer doesn't have to
 * distinguish "bad signature" from "wrong audience" from "expired" — by
 * design, the FE should show the same error message regardless.
 *
 * <h3>Feature flag</h3>
 * The bean is only registered when {@code app.integrations.google.enabled=true}
 * (see {@code @ConditionalOnProperty}). When disabled, the
 * {@code GoogleAuthService} falls back to throwing
 * {@code GOOGLE_PROVIDER_DISABLED} before doing any network I/O.
 *
 * <h3>Threading</h3>
 * {@link GoogleIdTokenVerifier} is thread-safe; we share a single instance
 * across the application context.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.integrations.google", name = "enabled", havingValue = "true")
public class GoogleIdentityProviderImpl implements GoogleIdentityProvider {

	private final GoogleProperties properties;
	private GoogleIdTokenVerifier verifier;
	private String configuredClientId;

	public GoogleIdentityProviderImpl(GoogleProperties properties) {
		this.properties = properties;
	}

	@PostConstruct
	void initVerifier() {
		GoogleProperties.Google google = properties.google();
		if (google == null || google.clientId() == null || google.clientId().isBlank()) {
			// The @ConditionalOnProperty on the bean only checks `enabled=true`;
			// a misconfigured `client-id=""` would otherwise let the app start
			// and only fail on the first login. We fail fast instead.
			throw new IllegalStateException(
					"app.integrations.google.client-id (env GOOGLE_OAUTH_CLIENT_ID) "
							+ "is required when Google Login is enabled");
		}
		this.configuredClientId = google.clientId();

		HttpTransport transport = new NetHttpTransport();
		GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

		// Builder chains:
		//   - setAudience(clientId) restricts aud to OUR client id (Google
		//     might have multiple audiences if our token is presented to
		//     a different verifier).
		//   - No clock-skew setter; Google's library default is generous
		//     enough (60s) but we don't expose it for now — the JWS
		//     `exp` check is strict.
		this.verifier = new GoogleIdTokenVerifier.Builder(transport, jsonFactory)
				.setAudience(Collections.singletonList(configuredClientId))
				.build();

		log.info("[google-identity] verifier ready; clientId={}, scopes={}",
				maskClientId(configuredClientId),
				properties.gmail() == null ? List.of() : properties.gmail().scopes());
	}

	@Override
	public GoogleProfile verifyIdToken(String idToken) {
		if (idToken == null || idToken.isBlank()) {
			throw new UnauthorizedException("INVALID_GOOGLE_TOKEN",
					"id_token is missing");
		}

		GoogleIdToken parsed;
		try {
			parsed = verifier.verify(idToken);
		}
		catch (GeneralSecurityException | IOException e) {
			// Network blip, JWKS unavailable, etc.
			log.warn("[google-identity] verification I/O error: {}", e.getMessage());
			throw new UnauthorizedException("INVALID_GOOGLE_TOKEN",
					"Google id_token could not be verified");
		}
		catch (IllegalArgumentException e) {
			// Malformed JWT (3 base64 segments, etc.)
			throw new UnauthorizedException("INVALID_GOOGLE_TOKEN",
					"Google id_token is malformed");
		}

		if (parsed == null) {
			// Verifier returns null on signature / aud / exp failure WITHOUT
			// throwing. We collapse all those into a single error code so
			// the front-end never tells the user "wrong audience" (which
			// would help an attacker probe valid client ids).
			throw new UnauthorizedException("INVALID_GOOGLE_TOKEN",
					"Google id_token is invalid or expired");
		}

		Payload payload = parsed.getPayload();

		// Defensive field reads — Google's contract requires `sub` and
		// `email` to be present on a successful id_token, but a defensive
		// null check costs nothing and protects against library changes.
		String subject = readString(payload, "sub");
		String email = readString(payload, "email");
		if (subject == null || email == null) {
			throw new UnauthorizedException("INVALID_GOOGLE_TOKEN",
					"Google id_token is missing required claims");
		}

		Boolean emailVerifiedRaw = (Boolean) payload.get("email_verified");
		boolean emailVerified = emailVerifiedRaw != null && emailVerifiedRaw;

		return new GoogleProfile(
				subject,
				email,
				emailVerified,
				readString(payload, "given_name"),
				readString(payload, "family_name"),
				readString(payload, "picture"),
				readString(payload, "hd"),
				// Scopes are NOT in the id_token (they live on the
				// authorization code exchange). We populate an empty list
				// here; PR2's link-with-Gmail flow will fill this when it
				// persists the refresh_token row.
				new ArrayList<>());
	}

	@Override
	public boolean isEnabled() {
		return properties != null
				&& properties.google() != null
				&& properties.google().enabled();
	}

	private static String readString(Payload payload, String key) {
		Object value = payload.get(key);
		return value instanceof String s ? s : null;
	}

	/** Mask the client id so logs don't leak the full value (defense-in-depth). */
	private static String maskClientId(String clientId) {
		if (clientId == null || clientId.length() < 10) {
			return "***";
		}
		return clientId.substring(0, 6) + "..." + clientId.substring(clientId.length() - 4);
	}
}