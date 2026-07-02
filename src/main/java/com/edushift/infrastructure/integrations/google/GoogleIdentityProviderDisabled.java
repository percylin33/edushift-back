package com.edushift.infrastructure.integrations.google;

import com.edushift.shared.exception.UnauthorizedException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * No-op {@link GoogleIdentityProvider} used when
 * {@code app.integrations.google.enabled=false}.
 *
 * <h3>Why a separate bean instead of just gating {@link GoogleIdentityProviderImpl}</h3>
 * Three reasons:
 * <ol>
 *   <li>Other modules (Gmail service, future audit reports) depend on the
 *       <em>interface</em>, not the implementation. Wiring a stub lets the
 *       whole context start cleanly without sprinkling
 *       {@code @ConditionalOnBean} throughout the codebase.</li>
 *   <li>Failure semantics differ: this stub throws
 *       {@code GOOGLE_PROVIDER_DISABLED} (mapped to 401 with a clear
 *       message) instead of {@code INVALID_GOOGLE_TOKEN} (which would
 *       confuse ops into debugging a token problem).</li>
 *   <li>The property classes ({@link GoogleProperties}) are still loaded
 *       so other tools (Swagger docs, environment reports) can show
 *       "Google login is disabled" without a {@code NullPointerException}.</li>
 * </ol>
 */
@Service
@ConditionalOnProperty(
		prefix = "app.integrations.google",
		name = "enabled",
		havingValue = "false",
		matchIfMissing = true)
public class GoogleIdentityProviderDisabled implements GoogleIdentityProvider {

	@Override
	public GoogleProfile verifyIdToken(String idToken) {
		throw new UnauthorizedException("GOOGLE_PROVIDER_DISABLED",
				"Google Login is not enabled for this deployment");
	}

	@Override
	public boolean isEnabled() {
		return false;
	}
}