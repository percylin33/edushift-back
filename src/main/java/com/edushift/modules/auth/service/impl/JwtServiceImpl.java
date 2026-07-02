package com.edushift.modules.auth.service.impl;

import com.edushift.modules.auth.config.JwtProperties;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.shared.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Default {@link JwtService} backed by JJWT (HS256/HS512).
 *
 * <h3>Key sizing</h3>
 * The configured {@code app.security.jwt.secret} must be at least 256 bits
 * (32 chars / bytes) for HS256. JJWT will reject shorter keys with a
 * {@code WeakKeyException}; we surface that as a fatal startup error so
 * misconfiguration is caught immediately.
 *
 * <h3>Clock skew</h3>
 * 30 seconds of allowed clock skew when validating {@code exp}/{@code nbf}
 * — small enough to keep the security window tight, large enough to absorb
 * realistic NTP drift between client and server.
 */
@Slf4j
@Service
public class JwtServiceImpl implements JwtService {

	private static final String CLAIM_TENANT_ID = "tenant_id";
	private static final String CLAIM_TENANT_SLUG = "tenant_slug";
	private static final String CLAIM_ROLES = "roles";
	private static final String CLAIM_EMAIL = "email";
	private static final String CLAIM_TYPE = "typ";
	/**
	 * RFC 7519 §4.1.7 reserved {@code jti} claim — a unique identifier per
	 * issued token. Without it, two refresh tokens emitted to the same user
	 * within the same wall-clock second would share every other claim
	 * ({@code sub}, {@code iat}, {@code exp}, {@code tenant_id}, {@code typ})
	 * and produce <em>byte-identical</em> JWTs whose SHA-256 hashes collide
	 * against the {@code uk_refresh_tokens_token_hash} unique constraint.
	 * That collision was surfaced by {@code AuthTenantIsolationIT} when
	 * Failsafe was wired up for Sprint 2 — the issue had been latent because
	 * Sprint 1 only ran unit tests via {@code mvn test}.
	 *
	 * <p>Adding {@code jti} also brings us in line with standard JWT
	 * recommendations and lets future code (e.g. token introspection,
	 * audit logs) refer to a token by an opaque id without exposing the
	 * raw secret.
	 */
	private static final String CLAIM_JWT_ID = "jti";
	private static final long ALLOWED_CLOCK_SKEW_SECONDS = 30L;

	private final JwtProperties properties;
	private SecretKey signingKey;

	@Autowired
	public JwtServiceImpl(JwtProperties properties) {
		this.properties = properties;
	}

	@PostConstruct
	void initKey() {
		String secret = properties.getSecret();
		if (secret == null || secret.isBlank()) {
			throw new IllegalStateException(
					"app.security.jwt.secret (env JWT_SECRET) is required and must be at least 32 chars");
		}
		byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
		if (keyBytes.length < 32) {
			throw new IllegalStateException(
					"app.security.jwt.secret must be at least 32 bytes (256 bits) for HS256");
		}
		this.signingKey = Keys.hmacShaKeyFor(keyBytes);
		log.info("[jwt] signing key initialized ({} bytes); access TTL={}, refresh TTL={}",
				keyBytes.length,
				properties.getAccessTokenTtl(),
				properties.getRefreshTokenTtl());
	}

	@Override
	public String issueAccessToken(User user, Tenant tenant, Set<String> roles) {
		Instant now = Instant.now();
		Map<String, Object> claims = new LinkedHashMap<>();
		claims.put(CLAIM_TENANT_ID, tenant.getId().toString());
		claims.put(CLAIM_TENANT_SLUG, tenant.getSlug());
		claims.put(CLAIM_ROLES, roles == null ? List.of() : List.copyOf(roles));
		claims.put(CLAIM_EMAIL, user.getEmail());
		claims.put(CLAIM_TYPE, TokenType.ACCESS.name().toLowerCase());
		claims.put(CLAIM_JWT_ID, UUID.randomUUID().toString());

		return buildToken(user.getPublicUuid().toString(), claims, now, properties.getAccessTokenTtl());
	}

	@Override
	public String issueRefreshToken(User user, Tenant tenant) {
		Instant now = Instant.now();
		Map<String, Object> claims = new LinkedHashMap<>();
		claims.put(CLAIM_TENANT_ID, tenant.getId().toString());
		claims.put(CLAIM_TYPE, TokenType.REFRESH.name().toLowerCase());
		claims.put(CLAIM_JWT_ID, UUID.randomUUID().toString());

		return buildToken(user.getPublicUuid().toString(), claims, now, properties.getRefreshTokenTtl());
	}

	@Override
	public String issueResetToken(User user, Tenant tenant, UUID jti) {
		Instant now = Instant.now();
		Map<String, Object> claims = new LinkedHashMap<>();
		claims.put(CLAIM_TENANT_ID, tenant.getId().toString());
		claims.put(CLAIM_TENANT_SLUG, tenant.getSlug());
		claims.put(CLAIM_TYPE, TokenType.RESET.name().toLowerCase());
		// Caller-provided jti so the DB row and the token stay in sync; if
		// absent, JJWT will auto-generate one but it would be invisible to
		// us. We require it explicitly here to keep the contract tight.
		claims.put(CLAIM_JWT_ID, jti.toString());

		return buildToken(user.getPublicUuid().toString(), claims, now, properties.getResetTokenTtl());
	}

	@Override
	public String issueMfaToken(User user, Tenant tenant) {
		Instant now = Instant.now();
		Map<String, Object> claims = new LinkedHashMap<>();
		claims.put(CLAIM_TENANT_ID, tenant.getId().toString());
		claims.put(CLAIM_TENANT_SLUG, tenant.getSlug());
		claims.put(CLAIM_TYPE, TokenType.MFA.name().toLowerCase());
		claims.put(CLAIM_JWT_ID, UUID.randomUUID().toString());
		return buildToken(user.getPublicUuid().toString(), claims, now, properties.getMfaTokenTtl());
	}

	@Override
	public JwtClaims parseAndValidate(String token) {
		if (token == null || token.isBlank()) {
			throw new UnauthorizedException("INVALID_TOKEN", "Token is missing");
		}
		try {
			Jws<Claims> jws = Jwts.parser()
					.verifyWith(signingKey)
					.requireIssuer(properties.getIssuer())
					.clockSkewSeconds(ALLOWED_CLOCK_SKEW_SECONDS)
					.build()
					.parseSignedClaims(token);

			Claims body = jws.getPayload();
			TokenType type = parseTokenType(body.get(CLAIM_TYPE, String.class));
			UUID tenantId = parseUuid(body.get(CLAIM_TENANT_ID, String.class), CLAIM_TENANT_ID);
			Set<String> roles = parseRoles(body.get(CLAIM_ROLES));

			return new JwtClaims(
					body.getSubject(),
					tenantId,
					body.get(CLAIM_TENANT_SLUG, String.class),
					roles,
					type,
					parseUuid(body.get(CLAIM_JWT_ID, String.class), CLAIM_JWT_ID),
					body.getIssuedAt() == null ? null : body.getIssuedAt().toInstant(),
					body.getExpiration() == null ? null : body.getExpiration().toInstant()
			);
		}
		catch (ExpiredJwtException e) {
			throw new UnauthorizedException("TOKEN_EXPIRED", "Token has expired");
		}
		catch (SignatureException e) {
			throw new UnauthorizedException("INVALID_SIGNATURE", "Token signature is invalid");
		}
		catch (JwtException | IllegalArgumentException e) {
			throw new UnauthorizedException("INVALID_TOKEN", "Token is malformed or invalid");
		}
	}

	@Override
	public long accessTokenTtlSeconds() {
		return properties.getAccessTokenTtl().toSeconds();
	}

	@Override
	public Duration resetTokenTtl() {
		return properties.getResetTokenTtl();
	}

	@Override
	public long mfaTokenTtlSeconds() {
		return properties.getMfaTokenTtl().toSeconds();
	}

	private String buildToken(String subject, Map<String, Object> claims, Instant now, Duration ttl) {
		Instant expiresAt = now.plus(ttl);
		var builder = Jwts.builder()
				.subject(subject)
				.issuer(properties.getIssuer())
				.issuedAt(Date.from(now))
				.expiration(Date.from(expiresAt))
				.claims().add(claims).and()
				.signWith(signingKey);
		if (properties.getAudience() != null && !properties.getAudience().isBlank()) {
			builder.audience().add(properties.getAudience()).and();
		}
		return builder.compact();
	}

	private static TokenType parseTokenType(String raw) {
		if (raw == null) return TokenType.ACCESS;
		try {
			return TokenType.valueOf(raw.toUpperCase());
		}
		catch (IllegalArgumentException e) {
			return TokenType.ACCESS;
		}
	}

	private static UUID parseUuid(String value, String claimName) {
		if (value == null) {
			throw new UnauthorizedException("INVALID_TOKEN",
					"Missing '" + claimName + "' claim");
		}
		try {
			return UUID.fromString(value);
		}
		catch (IllegalArgumentException e) {
			throw new UnauthorizedException("INVALID_TOKEN",
					"Claim '" + claimName + "' is not a valid UUID");
		}
	}

	@SuppressWarnings("unchecked")
	private static Set<String> parseRoles(Object raw) {
		if (raw instanceof List<?> list) {
			Set<String> roles = new HashSet<>(list.size());
			for (Object item : list) {
				if (item != null) roles.add(item.toString());
			}
			return roles;
		}
		return Set.of();
	}

}
