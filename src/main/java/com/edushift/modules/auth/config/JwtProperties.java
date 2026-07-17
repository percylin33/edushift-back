package com.edushift.modules.auth.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings for the auth module. Bound to {@code app.security.jwt.*}.
 * <p>
 * The {@code secret} must be provided via environment (never hardcoded) and must be
 * at least 256 bits (32 chars) when using HS256.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {

	/** Signing secret (HS256/HS512). Provide via JWT_SECRET. */
	private String secret;

	/** Token issuer claim ("iss"). */
	private String issuer = "edushift";

	/** Optional audience claim ("aud"). */
	private String audience;

	/** Access token time-to-live. */
	private Duration accessTokenTtl = Duration.ofMinutes(15);

	/** Refresh token time-to-live. */
	private Duration refreshTokenTtl = Duration.ofDays(7);

	/**
	 * Password-reset token time-to-live (Sprint 17 / BE-17.1).
	 * Default 1h — short enough to limit exposure if a link leaks, long
	 * enough for a user to find the email in their inbox.
	 */
	private Duration resetTokenTtl = Duration.ofHours(1);

	/**
	 * MFA challenge token time-to-live (Sprint 17 / BE-17.2). Default
	 * 5 minutes — enough for a user to open their authenticator app
	 * and type the code, short enough that a stolen token has a
	 * narrow window of usefulness.
	 */
	private Duration mfaTokenTtl = Duration.ofMinutes(5);

	/**
	 * Onboarding token time-to-live for the SUPER_ADMIN first-login MFA
	 * enrolment flow (Sprint 15 / F-02 / H-02). Default 10 minutes —
	 * long enough to scan the QR, type the first TOTP code and recover
	 * from a mis-typed secret, short enough that a leaked token has a
	 * narrow window.
	 */
	private Duration onboardingTokenTtl = Duration.ofMinutes(10);

	/**
	 * Impersonation token time-to-live (Sprint 15 / F-03 / H-05).
	 * Default 15 minutes — short enough to limit the blast radius of a
	 * leaked token, long enough for a SUPER_ADMIN to perform a support
	 * task without re-impersonating mid-flow.
	 */
	private Duration impersonationTokenTtl = Duration.ofMinutes(15);

	/** HTTP header used to carry the access token. */
	private String header = "Authorization";

	/** Token prefix expected in the header (with trailing space). */
	private String tokenPrefix = "Bearer ";

}
