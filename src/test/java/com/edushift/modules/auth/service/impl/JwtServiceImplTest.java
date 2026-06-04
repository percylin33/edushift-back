package com.edushift.modules.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edushift.modules.auth.config.JwtProperties;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.service.JwtService.JwtClaims;
import com.edushift.modules.auth.service.JwtService.TokenType;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.shared.exception.UnauthorizedException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link JwtServiceImpl}. Pure JVM, no Spring context, no DB.
 * <p>
 * Verifies the JWT roundtrip (issue → parse → assert claims), expiration
 * handling, signature validation, and refresh-token shape.
 */
class JwtServiceImplTest {

	private static final String SECRET = "test-secret-min-32-chars-edushift-2026-XYZ";
	private static final String ISSUER = "edushift-test";

	private JwtServiceImpl jwtService;
	private JwtProperties properties;

	@BeforeEach
	void setUp() {
		properties = new JwtProperties();
		properties.setSecret(SECRET);
		properties.setIssuer(ISSUER);
		properties.setAccessTokenTtl(Duration.ofMinutes(15));
		properties.setRefreshTokenTtl(Duration.ofDays(7));
		jwtService = new JwtServiceImpl(properties);
		jwtService.initKey();
	}

	@Test
	@DisplayName("issueAccessToken → parseAndValidate roundtrip preserves all claims")
	void accessTokenRoundtrip() {
		User user = newUser("admin@demo.edushift.pe");
		Tenant tenant = newTenant("demo", TenantStatus.ACTIVE);
		Set<String> roles = Set.of("ADMIN", "TEACHER");

		String token = jwtService.issueAccessToken(user, tenant, roles);

		assertThat(token).isNotBlank();
		JwtClaims claims = jwtService.parseAndValidate(token);

		assertThat(claims.subject()).isEqualTo(user.getPublicUuid().toString());
		assertThat(claims.tenantId()).isEqualTo(tenant.getId());
		assertThat(claims.tenantSlug()).isEqualTo("demo");
		assertThat(claims.roles()).containsExactlyInAnyOrder("ADMIN", "TEACHER");
		assertThat(claims.type()).isEqualTo(TokenType.ACCESS);
		assertThat(claims.issuedAt()).isNotNull();
		assertThat(claims.expiresAt()).isNotNull().isAfter(claims.issuedAt());
	}

	@Test
	@DisplayName("issueRefreshToken carries typ=refresh and minimal claims")
	void refreshTokenShape() {
		User user = newUser("admin@demo.edushift.pe");
		Tenant tenant = newTenant("demo", TenantStatus.ACTIVE);

		String token = jwtService.issueRefreshToken(user, tenant);
		JwtClaims claims = jwtService.parseAndValidate(token);

		assertThat(claims.type()).isEqualTo(TokenType.REFRESH);
		assertThat(claims.subject()).isEqualTo(user.getPublicUuid().toString());
		assertThat(claims.tenantId()).isEqualTo(tenant.getId());
		assertThat(claims.tenantSlug()).isNull();
		assertThat(claims.roles()).isEmpty();
	}

	@Test
	@DisplayName("parseAndValidate throws UnauthorizedException on missing token")
	void blankTokenRejected() {
		assertThatThrownBy(() -> jwtService.parseAndValidate(null))
				.isInstanceOf(UnauthorizedException.class)
				.hasMessageContaining("missing");
		assertThatThrownBy(() -> jwtService.parseAndValidate(""))
				.isInstanceOf(UnauthorizedException.class)
				.hasMessageContaining("missing");
	}

	@Test
	@DisplayName("parseAndValidate throws INVALID_SIGNATURE for token signed with another key")
	void wrongSignatureRejected() {
		User user = newUser("admin@demo.edushift.pe");
		Tenant tenant = newTenant("demo", TenantStatus.ACTIVE);

		// Build a token with the SAME claims but a DIFFERENT signing key.
		SecretKey otherKey = Keys.hmacShaKeyFor(
				"another-secret-min-32-chars-XYZ-edushift-2026".getBytes(StandardCharsets.UTF_8));
		String forged = Jwts.builder()
				.subject(user.getPublicUuid().toString())
				.issuer(ISSUER)
				.issuedAt(new Date())
				.expiration(new Date(System.currentTimeMillis() + 60_000))
				.signWith(otherKey)
				.compact();

		assertThatThrownBy(() -> jwtService.parseAndValidate(forged))
				.isInstanceOf(UnauthorizedException.class);
	}

	@Test
	@DisplayName("parseAndValidate throws TOKEN_EXPIRED for expired token")
	void expiredTokenRejected() throws Exception {
		// Issue a token with TTL=1ms by reflecting the field, then wait beyond clock skew.
		properties.setAccessTokenTtl(Duration.ofMillis(1));
		User user = newUser("admin@demo.edushift.pe");
		Tenant tenant = newTenant("demo", TenantStatus.ACTIVE);

		String token = jwtService.issueAccessToken(user, tenant, Set.of());
		// Wait more than the clock skew (30s) — that would slow the test a lot. Instead
		// build a token with an expiration in the deep past directly.
		token = Jwts.builder()
				.subject(user.getPublicUuid().toString())
				.issuer(ISSUER)
				.issuedAt(new Date(System.currentTimeMillis() - 600_000))
				.expiration(new Date(System.currentTimeMillis() - 300_000))
				.signWith(getSigningKeyReflectively())
				.compact();

		final String t = token;
		assertThatThrownBy(() -> jwtService.parseAndValidate(t))
				.isInstanceOf(UnauthorizedException.class);
	}

	@Test
	@DisplayName("parseAndValidate throws on token with wrong issuer")
	void wrongIssuerRejected() {
		String token = Jwts.builder()
				.subject(UUID.randomUUID().toString())
				.issuer("not-edushift")
				.issuedAt(new Date())
				.expiration(new Date(System.currentTimeMillis() + 60_000))
				.signWith(getSigningKeyReflectively())
				.compact();

		assertThatThrownBy(() -> jwtService.parseAndValidate(token))
				.isInstanceOf(UnauthorizedException.class);
	}

	@Test
	@DisplayName("initKey rejects secrets shorter than 32 bytes")
	void weakKeyRejected() {
		JwtProperties weak = new JwtProperties();
		weak.setSecret("too-short");
		JwtServiceImpl svc = new JwtServiceImpl(weak);
		assertThatThrownBy(svc::initKey)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("32");
	}

	@Test
	@DisplayName("initKey rejects blank secret")
	void blankSecretRejected() {
		JwtProperties blank = new JwtProperties();
		blank.setSecret("");
		JwtServiceImpl svc = new JwtServiceImpl(blank);
		assertThatThrownBy(svc::initKey).isInstanceOf(IllegalStateException.class);
	}

	private static User newUser(String email) {
		User u = new User();
		u.setPublicUuid(UUID.randomUUID());
		u.setEmail(email);
		u.setFirstName("Admin");
		u.setLastName("Demo");
		u.setStatus(UserStatus.ACTIVE);
		u.setEmailVerified(true);
		u.setPasswordHash("$2a$12$irrelevant.for.jwt.test.................................");
		return u;
	}

	private static Tenant newTenant(String slug, TenantStatus status) {
		Tenant t = new Tenant();
		setBaseEntityId(t, UUID.randomUUID());
		t.setName("Demo Institution");
		t.setSlug(slug);
		t.setStatus(status);
		t.setPublicUuid(UUID.randomUUID());
		return t;
	}

	/**
	 * Reflectively sets the inherited {@code id} field on a {@code BaseEntity}.
	 * The setter is package-private, but tests live in a different package, so
	 * we reach in through reflection. Documented exception to the project's
	 * "no reflection" rule, justified because we never want production code
	 * to set the PK directly.
	 */
	private static void setBaseEntityId(Object entity, UUID id) {
		try {
			Class<?> clazz = entity.getClass();
			while (clazz != null) {
				try {
					Field f = clazz.getDeclaredField("id");
					f.setAccessible(true);
					f.set(entity, id);
					return;
				}
				catch (NoSuchFieldException ignored) {
					clazz = clazz.getSuperclass();
				}
			}
			throw new IllegalStateException("No 'id' field found in " + entity.getClass());
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private SecretKey getSigningKeyReflectively() {
		try {
			Field f = JwtServiceImpl.class.getDeclaredField("signingKey");
			f.setAccessible(true);
			return (SecretKey) f.get(jwtService);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

}
