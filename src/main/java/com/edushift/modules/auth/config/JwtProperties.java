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

	/** HTTP header used to carry the access token. */
	private String header = "Authorization";

	/** Token prefix expected in the header (with trailing space). */
	private String tokenPrefix = "Bearer ";

}
