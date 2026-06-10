package com.edushift.modules.attendance.service.impl;

import com.edushift.modules.attendance.exception.QrInvalidException;
import com.edushift.modules.attendance.service.QrTokenService;
import com.edushift.modules.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Default {@link QrTokenService} implementation backed by JJWT 0.12
 * (Sprint 6 / BE-6.2.B).
 *
 * <p>Reuses the {@code app.security.jwt.secret} configured for the
 * {@code auth} module — the {@code typ="attendance"} claim is what
 * keeps these tokens unusable as access tokens (rejected by
 * {@link com.edushift.modules.auth.security.JwtAuthenticationFilter}).
 *
 * <h3>Why HS256 with the same secret?</h3>
 * Trade-offs documented in ADR-6.1: smaller blast radius is desirable
 * (per-tenant keys, key rotation with {@code kid}), but the operational
 * cost of a separate secret store is not justified at MVP. Compromise
 * of {@code JWT_SECRET} already grants login forgery, so the
 * incremental risk added by reuse is bounded.
 */
@Slf4j
@Service
public class QrTokenServiceImpl implements QrTokenService {

	private static final String CLAIM_TENANT_ID = "tenant_id";
	private static final String CLAIM_TYPE = "typ";
	private static final String CLAIM_JWT_ID = "jti";
	private static final String QR_TOKEN_TYPE = "attendance";
	private static final long ALLOWED_CLOCK_SKEW_SECONDS = 30L;

	private final JwtProperties properties;
	private SecretKey signingKey;

	public QrTokenServiceImpl(JwtProperties properties) {
		this.properties = properties;
	}

	@PostConstruct
	void initKey() {
		String secret = properties.getSecret();
		if (secret == null || secret.isBlank()) {
			throw new IllegalStateException(
					"app.security.jwt.secret (env JWT_SECRET) is required "
							+ "to mint attendance QR tokens");
		}
		byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
		if (keyBytes.length < 32) {
			throw new IllegalStateException(
					"app.security.jwt.secret must be at least 32 bytes "
							+ "(256 bits) for HS256");
		}
		this.signingKey = Keys.hmacShaKeyFor(keyBytes);
		log.info("[qr-token] signing key initialised (typ={})", QR_TOKEN_TYPE);
	}

	@Override
	public IssuedQrToken issue(UUID studentPublicUuid, UUID tenantId) {
		if (studentPublicUuid == null || tenantId == null) {
			throw new IllegalArgumentException(
					"studentPublicUuid and tenantId are required to issue a QR token");
		}
		Instant now = Instant.now();
		Map<String, Object> claims = new LinkedHashMap<>();
		claims.put(CLAIM_TENANT_ID, tenantId.toString());
		claims.put(CLAIM_TYPE, QR_TOKEN_TYPE);
		claims.put(CLAIM_JWT_ID, UUID.randomUUID().toString());

		var builder = Jwts.builder()
				.subject(studentPublicUuid.toString())
				.issuer(properties.getIssuer())
				.issuedAt(Date.from(now))
				.claims().add(claims).and()
				.signWith(signingKey);
		if (properties.getAudience() != null && !properties.getAudience().isBlank()) {
			builder.audience().add(properties.getAudience()).and();
		}
		String token = builder.compact();
		return new IssuedQrToken(token, hash(token));
	}

	@Override
	public QrClaims parseAndValidate(String token) {
		if (token == null || token.isBlank()) {
			throw new QrInvalidException("QR token is missing");
		}
		try {
			Jws<Claims> jws = Jwts.parser()
					.verifyWith(signingKey)
					.requireIssuer(properties.getIssuer())
					.clockSkewSeconds(ALLOWED_CLOCK_SKEW_SECONDS)
					.build()
					.parseSignedClaims(token);

			Claims body = jws.getPayload();
			String type = body.get(CLAIM_TYPE, String.class);
			if (!QR_TOKEN_TYPE.equals(type)) {
				// Hardening: never let an access/refresh token sneak in
				// as a QR. Same defensive stance as
				// JwtAuthenticationFilter rejecting non-access tokens.
				throw new QrInvalidException(
						"QR token has wrong type: expected '"
								+ QR_TOKEN_TYPE + "'");
			}

			UUID studentPublicUuid = parseUuid(body.getSubject(), "sub");
			UUID tenantId = parseUuid(body.get(CLAIM_TENANT_ID, String.class),
					CLAIM_TENANT_ID);
			return new QrClaims(studentPublicUuid, tenantId);
		}
		catch (SignatureException e) {
			throw new QrInvalidException("QR token signature is invalid");
		}
		catch (JwtException | IllegalArgumentException e) {
			throw new QrInvalidException("QR token is malformed or invalid");
		}
	}

	@Override
	public String hash(String token) {
		if (token == null) {
			throw new IllegalArgumentException("token is required");
		}
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(bytes.length * 2);
			for (byte b : bytes) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		}
		catch (NoSuchAlgorithmException e) {
			// SHA-256 is mandatory in every JRE since 1.4.2 — failing
			// here means the JRE is broken or has been deliberately
			// stripped. Bubble it up.
			throw new IllegalStateException(
					"SHA-256 algorithm is not available", e);
		}
	}

	private static UUID parseUuid(String value, String claimName) {
		if (value == null || value.isBlank()) {
			throw new QrInvalidException(
					"QR token claim '" + claimName + "' is missing");
		}
		try {
			return UUID.fromString(value);
		}
		catch (IllegalArgumentException e) {
			throw new QrInvalidException(
					"QR token claim '" + claimName + "' is not a valid UUID");
		}
	}
}
