package com.edushift.modules.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.audit.events.AuditAction;
import com.edushift.modules.audit.service.AuditLogger;
import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.auth.dto.LoginRequest;
import com.edushift.modules.auth.dto.UserSummary;
import com.edushift.modules.auth.entity.RefreshToken;
import com.edushift.modules.auth.entity.RevocationReason;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.mapper.UserMapper;
import com.edushift.modules.auth.repository.RefreshTokenRepository;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.auth.service.JwtService.JwtClaims;
import com.edushift.modules.auth.service.JwtService.TokenType;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.exception.TenantNotFoundException;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.exception.UnauthorizedException;
import com.edushift.shared.multitenancy.TenantContext;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Unit tests for {@link AuthServiceImpl}. Mock-based — no Spring context, no
 * database — so they run instantly and pin the service contract directly.
 *
 * <p>Coverage:
 * <ul>
 *   <li><strong>Login</strong>: happy path, blank slug, tenant not found,
 *       tenant suspended, unknown email, bad password, user LOCKED, user
 *       PENDING_VERIFICATION.</li>
 *   <li><strong>Refresh</strong>: happy rotation, unknown token, token
 *       reuse (theft detection), expired token, wrong token type, tenant
 *       gone, user gone.</li>
 *   <li><strong>Logout</strong>: happy path, idempotent (already revoked),
 *       malformed token (no-op), unknown hash (no-op).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

	@Mock private TenantRepository tenantRepository;
	@Mock private UserRepository userRepository;
	@Mock private RefreshTokenRepository refreshTokenRepository;
	@Mock private UserMapper userMapper;
	@Mock private PasswordEncoder passwordEncoder;
	@Mock private JwtService jwtService;
	@Mock private AuditLogger auditLogger;

	/**
	 * Plain {@link PlatformTransactionManager} mock — Mockito returns {@code null}
	 * from {@code getTransaction(...)} by default, and {@code commit(null)} /
	 * {@code rollback(null)} are no-ops on a mock. That's enough to make the
	 * internal {@code TransactionTemplate} pass the callback through verbatim,
	 * which is exactly what unit tests want (no real transaction; we're not
	 * exercising Hibernate, just the service contract).
	 */
	@Mock private PlatformTransactionManager txManager;

	@InjectMocks private AuthServiceImpl authService;

	private static final String SLUG = "demo";
	private static final String EMAIL = "admin@demo.edushift.pe";
	private static final String RAW_PWD = "Edushift123!";
	private static final String HASH = "$2a$12$dummyhashbutbcryptshape...........................";

	@BeforeEach
	void initAuthService() {
		// DEBT-AUTH-1: invoke the @Autowired setter so the decoy hash is
		// populated before any login() test runs. Mockito's @InjectMocks does
		// not auto-invoke @Autowired methods on the SUT — it only sets fields
		// or constructor args — so we wire this manually. Use lenient() so
		// tests that never call login() (e.g. refresh-only paths) don't
		// trigger an "unnecessary stubbing" violation in strict mode.
		org.mockito.Mockito.lenient().when(passwordEncoder.encode(anyString()))
				.thenReturn("$2a$12$decoy-hash-for-unit-test");
		authService.initTimingDecoyHash(passwordEncoder);
	}

	@AfterEach
	void clearTenantContext() {
		TenantContext.clear();
	}

	// =========================================================================
	// Login
	// =========================================================================

	@Test
	@DisplayName("login OK with valid credentials returns access + refresh tokens and persists hash")
	void loginOk() {
		Tenant tenant = newTenant(SLUG, TenantStatus.ACTIVE);
		User user = newUser(EMAIL, UserStatus.ACTIVE, HASH);
		UserSummary summary = new UserSummary(user.getPublicUuid(), "Admin Demo",
				EMAIL, null, UserStatus.ACTIVE);

		when(tenantRepository.findBySlugIgnoreCase(SLUG)).thenReturn(Optional.of(tenant));
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches(RAW_PWD, HASH)).thenReturn(true);
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
		when(jwtService.issueAccessToken(eq(user), eq(tenant), any())).thenReturn("access.token.value");
		when(jwtService.issueRefreshToken(user, tenant)).thenReturn("refresh.token.value");
		when(jwtService.accessTokenTtlSeconds()).thenReturn(900L);
		when(userMapper.toSummary(user)).thenReturn(summary);
		when(refreshTokenRepository.saveAndFlush(any(RefreshToken.class)))
				.thenAnswer(inv -> inv.getArgument(0));

		AuthResponse response = authService.login(new LoginRequest(EMAIL, RAW_PWD), SLUG);

		assertThat(response).isNotNull();
		assertThat(response.accessToken()).isEqualTo("access.token.value");
		assertThat(response.refreshToken()).isEqualTo("refresh.token.value");
		assertThat(response.tokenType()).isEqualTo("Bearer");
		assertThat(response.expiresInSec()).isEqualTo(900L);

		// Refresh token must be persisted with parent_token_id = null (first in chain).
		ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
		verify(refreshTokenRepository, times(1)).saveAndFlush(tokenCaptor.capture());
		RefreshToken persisted = tokenCaptor.getValue();
		assertThat(persisted.getParentTokenId()).isNull();
		assertThat(persisted.getUserId()).isEqualTo(user.getId());
		assertThat(persisted.getTokenHash()).hasSize(64);
		assertThat(persisted.getTokenHash()).matches("[0-9a-f]{64}");
		assertThat(persisted.getExpiresAt()).isAfter(Instant.now());

		// last_login_at flushed eagerly.
		verify(userRepository, times(1)).saveAndFlush(user);
		assertThat(user.getLastLoginAt()).isNotNull()
				.isCloseTo(Instant.now(), within(10, ChronoUnit.SECONDS));
	}

	@Test
	@DisplayName("login KO when tenantSlug is blank → UnauthorizedException(TENANT_REQUIRED)")
	void loginRejectsBlankTenantSlug() {
		assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, RAW_PWD), ""))
				.isInstanceOf(UnauthorizedException.class)
				.hasMessageContaining("Tenant");

		verify(tenantRepository, never()).findBySlugIgnoreCase(anyString());
	}

	@Test
	@DisplayName("login KO when tenant slug not found → TenantNotFoundException (404)")
	void loginRejectsUnknownTenant() {
		when(tenantRepository.findBySlugIgnoreCase("ghost")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, RAW_PWD), "ghost"))
				.isInstanceOf(TenantNotFoundException.class)
				.hasMessageContaining("ghost");

		verify(userRepository, never()).findByEmail(anyString());
	}

	@Test
	@DisplayName("login KO when tenant SUSPENDED → UnauthorizedException(TENANT_INACTIVE)")
	void loginRejectsInactiveTenant() {
		Tenant suspended = newTenant(SLUG, TenantStatus.SUSPENDED);
		when(tenantRepository.findBySlugIgnoreCase(SLUG)).thenReturn(Optional.of(suspended));

		assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, RAW_PWD), SLUG))
				.isInstanceOf(UnauthorizedException.class);

		verify(userRepository, never()).findByEmail(anyString());
	}

	@Test
	@DisplayName("login KO when email unknown → UnauthorizedException(BAD_CREDENTIALS)")
	void loginRejectsUnknownEmail() {
		Tenant tenant = newTenant(SLUG, TenantStatus.ACTIVE);
		when(tenantRepository.findBySlugIgnoreCase(SLUG)).thenReturn(Optional.of(tenant));
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, RAW_PWD), SLUG))
				.isInstanceOf(UnauthorizedException.class)
				.hasMessageContaining("Invalid email or password");

		// DEBT-AUTH-1: even on the unknown-email branch, BCrypt is run against
		// the pre-computed decoy hash so the response time evens out with the
		// real-user branch (defeats user-enumeration timing attacks).
		verify(passwordEncoder, times(1)).matches(eq(RAW_PWD), anyString());
		verify(jwtService, never()).issueAccessToken(any(), any(), any());
		verify(refreshTokenRepository, never()).saveAndFlush(any(RefreshToken.class));
	}

	@Test
	@DisplayName("DEBT-AUTH-1: unknown email triggers BCrypt against decoy hash "
			+ "(timing-attack mitigation), but no user save/flush or token issue")
	void loginUnknownEmailIsConstantTime() {
		Tenant tenant = newTenant(SLUG, TenantStatus.ACTIVE);
		when(tenantRepository.findBySlugIgnoreCase(SLUG)).thenReturn(Optional.of(tenant));
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

		// Capture the hash passed to the decoy match — it must NOT be the
		// real user's password hash (there is no user) and it must NOT
		// change between calls (deterministic at runtime).
		ArgumentCaptor<String> decoyHashCaptor = ArgumentCaptor.forClass(String.class);
		when(passwordEncoder.matches(eq(RAW_PWD), decoyHashCaptor.capture())).thenReturn(false);

		assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, RAW_PWD), SLUG))
				.isInstanceOf(UnauthorizedException.class)
				.hasMessageContaining("Invalid email or password");

		// The decoy hash was the second arg of `matches(password, hash)`.
		assertThat(decoyHashCaptor.getValue()).isNotBlank();
		// A second call should reuse the SAME decoy hash (set once in
		// initTimingDecoyHash, then read-only).
		String firstDecoy = decoyHashCaptor.getValue();
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, RAW_PWD), SLUG))
				.isInstanceOf(UnauthorizedException.class);
		assertThat(decoyHashCaptor.getValue()).isEqualTo(firstDecoy);

		// The decoy hash is the auto-generated BCrypt of a random uuid-based
		// placeholder — must NOT contain the raw password (defense in depth:
		// if the encoder log ever leaks, the raw pwd is still not visible).
		assertThat(firstDecoy).doesNotContain(RAW_PWD);
	}

	@Test
	@DisplayName("login KO with wrong password → UnauthorizedException(BAD_CREDENTIALS)")
	void loginRejectsBadPassword() {
		Tenant tenant = newTenant(SLUG, TenantStatus.ACTIVE);
		User user = newUser(EMAIL, UserStatus.ACTIVE, HASH);
		when(tenantRepository.findBySlugIgnoreCase(SLUG)).thenReturn(Optional.of(tenant));
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("wrong-password", HASH)).thenReturn(false);

		assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "wrong-password"), SLUG))
				.isInstanceOf(UnauthorizedException.class)
				.hasMessageContaining("Invalid email or password");

		verify(jwtService, never()).issueAccessToken(any(), any(), any());
		verify(userRepository, never()).saveAndFlush(any(User.class));
		verify(refreshTokenRepository, never()).saveAndFlush(any(RefreshToken.class));
	}

	// =====================================================================
	// DEBT-USR-3: audit_logs persistence
	// The audit module is already in place (AuditEvent, AuditLogger,
	// AuditEventListener persists to edushift.audit_logs). What's tested
	// here is that the auth service EMITS the right AuditAction on each
	// success / failure path.
	// =====================================================================

	@Test
	@DisplayName("DEBT-USR-3: successful login emits AuditAction.LOGIN with user publicUuid")
	void loginEmitsAuditLogOnSuccess() {
		Tenant tenant = newTenant(SLUG, TenantStatus.ACTIVE);
		User user = newUser(EMAIL, UserStatus.ACTIVE, HASH);
		UserSummary summary = new UserSummary(user.getPublicUuid(), "Admin Demo",
				EMAIL, null, UserStatus.ACTIVE);
		when(tenantRepository.findBySlugIgnoreCase(SLUG)).thenReturn(Optional.of(tenant));
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches(RAW_PWD, HASH)).thenReturn(true);
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
		when(jwtService.issueAccessToken(eq(user), eq(tenant), any())).thenReturn("access");
		when(jwtService.issueRefreshToken(user, tenant)).thenReturn("refresh");
		when(jwtService.accessTokenTtlSeconds()).thenReturn(900L);
		when(userMapper.toSummary(user)).thenReturn(summary);
		when(refreshTokenRepository.saveAndFlush(any(RefreshToken.class)))
				.thenAnswer(inv -> inv.getArgument(0));

		authService.login(new LoginRequest(EMAIL, RAW_PWD), SLUG);

		// Verify the audit emit with the right action and the user's publicUuid.
		verify(auditLogger, times(1)).log(
				eq(AuditAction.LOGIN), eq("user"), eq(user.getPublicUuid()),
				eq("login OK"), anyMap());
	}

	@Test
	@DisplayName("DEBT-USR-3: bad password emits AuditAction.LOGIN_FAILED with user publicUuid")
	void loginEmitsAuditLogOnBadPassword() {
		Tenant tenant = newTenant(SLUG, TenantStatus.ACTIVE);
		User user = newUser(EMAIL, UserStatus.ACTIVE, HASH);
		when(tenantRepository.findBySlugIgnoreCase(SLUG)).thenReturn(Optional.of(tenant));
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("wrong", HASH)).thenReturn(false);

		assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "wrong"), SLUG))
				.isInstanceOf(UnauthorizedException.class);

		// The audit emit must carry LOGIN_FAILED with the user publicUuid
		// (we DO know which user it was — only the password was wrong).
		verify(auditLogger, times(1)).log(
				eq(AuditAction.LOGIN_FAILED), eq("user"), eq(user.getPublicUuid()),
				eq("login failed: bad password"), anyMap());
	}

	@Test
	@DisplayName("DEBT-USR-3: unknown email emits AuditAction.LOGIN_FAILED with null resourceId (no leak)")
	void loginEmitsAuditLogOnUnknownEmail() {
		Tenant tenant = newTenant(SLUG, TenantStatus.ACTIVE);
		when(tenantRepository.findBySlugIgnoreCase(SLUG)).thenReturn(Optional.of(tenant));
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, RAW_PWD), SLUG))
				.isInstanceOf(UnauthorizedException.class);

		// The audit emit must carry LOGIN_FAILED with a NULL resourceId —
		// the user is unknown so we cannot leak a publicUuid.
		verify(auditLogger, times(1)).log(
				eq(AuditAction.LOGIN_FAILED), eq("user_email"), isNull(),
				anyString(), anyMap());
	}

	@Test
	@DisplayName("login KO when user LOCKED → UnauthorizedException(USER_LOCKED)")
	void loginRejectsLockedUser() {
		Tenant tenant = newTenant(SLUG, TenantStatus.ACTIVE);
		User user = newUser(EMAIL, UserStatus.LOCKED, HASH);
		when(tenantRepository.findBySlugIgnoreCase(SLUG)).thenReturn(Optional.of(tenant));
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches(RAW_PWD, HASH)).thenReturn(true);

		assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, RAW_PWD), SLUG))
				.isInstanceOf(UnauthorizedException.class);

		verify(jwtService, never()).issueAccessToken(any(), any(), any());
	}

	@Test
	@DisplayName("login KO when user PENDING_VERIFICATION → UnauthorizedException(EMAIL_NOT_VERIFIED)")
	void loginRejectsUnverifiedEmail() {
		Tenant tenant = newTenant(SLUG, TenantStatus.ACTIVE);
		User user = newUser(EMAIL, UserStatus.PENDING_VERIFICATION, HASH);
		when(tenantRepository.findBySlugIgnoreCase(SLUG)).thenReturn(Optional.of(tenant));
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches(RAW_PWD, HASH)).thenReturn(true);

		assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, RAW_PWD), SLUG))
				.isInstanceOf(UnauthorizedException.class);
	}

	// =========================================================================
	// Refresh
	// =========================================================================

	@Test
	@DisplayName("refresh OK rotates the token: revokes old, persists new with parent link")
	void refreshOk() {
		Tenant tenant = newTenant(SLUG, TenantStatus.ACTIVE);
		User user = newUser(EMAIL, UserStatus.ACTIVE, HASH);
		UserSummary summary = new UserSummary(user.getPublicUuid(), "Admin Demo",
				EMAIL, null, UserStatus.ACTIVE);
		String rawRefresh = "old.refresh.token";

		// Pre-existing active token that was issued during login.
		RefreshToken existing = newRefreshToken(user.getId(), tenant.getId(),
				/* hash for raw input */ sha256HexLikeService(rawRefresh),
				/* expires in 1h */ Instant.now().plusSeconds(3600),
				/* not revoked */ null, /* parent */ null);

		when(jwtService.parseAndValidate(rawRefresh)).thenReturn(refreshClaims(user, tenant));
		when(refreshTokenRepository.findByTokenHash(existing.getTokenHash()))
				.thenReturn(Optional.of(existing));
		when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));
		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
		when(jwtService.issueAccessToken(eq(user), eq(tenant), any())).thenReturn("new.access");
		when(jwtService.issueRefreshToken(user, tenant)).thenReturn("new.refresh");
		when(jwtService.accessTokenTtlSeconds()).thenReturn(900L);
		when(userMapper.toSummary(user)).thenReturn(summary);
		when(refreshTokenRepository.saveAndFlush(any(RefreshToken.class)))
				.thenAnswer(inv -> inv.getArgument(0));

		AuthResponse response = authService.refresh(rawRefresh);

		assertThat(response.accessToken()).isEqualTo("new.access");
		assertThat(response.refreshToken()).isEqualTo("new.refresh");

		// Old token must be revoked with reason ROTATED.
		assertThat(existing.getRevokedAt()).isNotNull();
		assertThat(existing.getRevokedReason()).isEqualTo(RevocationReason.ROTATED);

		// Two saveAndFlush calls: one for revoking old, one for inserting new.
		ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
		verify(refreshTokenRepository, times(2)).saveAndFlush(captor.capture());
		RefreshToken newToken = captor.getAllValues().get(1);
		assertThat(newToken.getParentTokenId()).isEqualTo(existing.getId());
		assertThat(newToken.getUserId()).isEqualTo(user.getId());
	}

	@Test
	@DisplayName("refresh KO when token unknown → UnauthorizedException(INVALID_TOKEN)")
	void refreshRejectsUnknownToken() {
		Tenant tenant = newTenant(SLUG, TenantStatus.ACTIVE);
		User user = newUser(EMAIL, UserStatus.ACTIVE, HASH);
		String raw = "ghost.token";

		when(jwtService.parseAndValidate(raw)).thenReturn(refreshClaims(user, tenant));
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.refresh(raw))
				.isInstanceOf(UnauthorizedException.class)
				.hasMessageContaining("unknown");

		verify(refreshTokenRepository, never()).revokeChain(any(UUID.class), any(RevocationReason.class));
	}

	@Test
	@DisplayName("refresh THEFT DETECTION: revoked token replayed → revoke chain + 401 TOKEN_REUSED")
	void refreshDetectsTheft() {
		Tenant tenant = newTenant(SLUG, TenantStatus.ACTIVE);
		User user = newUser(EMAIL, UserStatus.ACTIVE, HASH);
		String raw = "stolen.token";

		// Already-revoked token (e.g. previously rotated; attacker uses the old one).
		RefreshToken revoked = newRefreshToken(user.getId(), tenant.getId(),
				sha256HexLikeService(raw),
				Instant.now().plusSeconds(3600),
				/* revokedAt */ Instant.now().minusSeconds(60),
				/* parent */ null);
		revoked.setRevokedReason(RevocationReason.ROTATED);

		when(jwtService.parseAndValidate(raw)).thenReturn(refreshClaims(user, tenant));
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revoked));
		when(refreshTokenRepository.revokeChain(eq(revoked.getId()), eq(RevocationReason.COMPROMISED)))
				.thenReturn(2);

		assertThatThrownBy(() -> authService.refresh(raw))
				.isInstanceOf(UnauthorizedException.class)
				.hasMessageContaining("revoked");

		verify(refreshTokenRepository, times(1))
				.revokeChain(revoked.getId(), RevocationReason.COMPROMISED);
		verify(jwtService, never()).issueAccessToken(any(), any(), any());
	}

	@Test
	@DisplayName("refresh KO when token expired → UnauthorizedException(TOKEN_EXPIRED) and marks EXPIRED")
	void refreshRejectsExpired() {
		Tenant tenant = newTenant(SLUG, TenantStatus.ACTIVE);
		User user = newUser(EMAIL, UserStatus.ACTIVE, HASH);
		String raw = "expired.token";

		RefreshToken expired = newRefreshToken(user.getId(), tenant.getId(),
				sha256HexLikeService(raw),
				/* expired 1h ago */ Instant.now().minusSeconds(3600),
				null, null);

		when(jwtService.parseAndValidate(raw)).thenReturn(refreshClaims(user, tenant));
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));
		when(refreshTokenRepository.saveAndFlush(any(RefreshToken.class)))
				.thenAnswer(inv -> inv.getArgument(0));

		assertThatThrownBy(() -> authService.refresh(raw))
				.isInstanceOf(UnauthorizedException.class)
				.hasMessageContaining("expired");

		assertThat(expired.getRevokedReason()).isEqualTo(RevocationReason.EXPIRED);
	}

	@Test
	@DisplayName("refresh KO when claims.type=ACCESS (not REFRESH) → UnauthorizedException")
	void refreshRejectsAccessTokenType() {
		Tenant tenant = newTenant(SLUG, TenantStatus.ACTIVE);
		User user = newUser(EMAIL, UserStatus.ACTIVE, HASH);
		JwtClaims accessClaims = new JwtClaims(user.getPublicUuid().toString(),
				tenant.getId(), tenant.getSlug(), Set.of(),
				TokenType.ACCESS, Instant.now(), Instant.now().plusSeconds(900));

		when(jwtService.parseAndValidate("any")).thenReturn(accessClaims);

		assertThatThrownBy(() -> authService.refresh("any"))
				.isInstanceOf(UnauthorizedException.class)
				.hasMessageContaining("not a refresh");

		verify(refreshTokenRepository, never()).findByTokenHash(anyString());
	}

	// =========================================================================
	// Logout
	// =========================================================================

	@Test
	@DisplayName("logout OK marks active token as revoked with reason LOGOUT")
	void logoutOk() {
		Tenant tenant = newTenant(SLUG, TenantStatus.ACTIVE);
		User user = newUser(EMAIL, UserStatus.ACTIVE, HASH);
		String raw = "active.refresh";

		RefreshToken active = newRefreshToken(user.getId(), tenant.getId(),
				sha256HexLikeService(raw),
				Instant.now().plusSeconds(3600),
				null, null);

		when(jwtService.parseAndValidate(raw)).thenReturn(refreshClaims(user, tenant));
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(active));
		when(refreshTokenRepository.saveAndFlush(any(RefreshToken.class)))
				.thenAnswer(inv -> inv.getArgument(0));

		authService.logout(raw);

		assertThat(active.getRevokedAt()).isNotNull();
		assertThat(active.getRevokedReason()).isEqualTo(RevocationReason.LOGOUT);
		verify(refreshTokenRepository, atLeastOnce()).saveAndFlush(active);
	}

	@Test
	@DisplayName("logout is idempotent: already-revoked token is a no-op (no exception, no save)")
	void logoutIdempotent() {
		Tenant tenant = newTenant(SLUG, TenantStatus.ACTIVE);
		User user = newUser(EMAIL, UserStatus.ACTIVE, HASH);
		String raw = "already.revoked";

		Instant originalRevokedAt = Instant.now().minusSeconds(120);
		RefreshToken revoked = newRefreshToken(user.getId(), tenant.getId(),
				sha256HexLikeService(raw),
				Instant.now().plusSeconds(3600),
				originalRevokedAt, null);
		revoked.setRevokedReason(RevocationReason.LOGOUT);

		when(jwtService.parseAndValidate(raw)).thenReturn(refreshClaims(user, tenant));
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revoked));

		authService.logout(raw);

		// State unchanged.
		assertThat(revoked.getRevokedAt()).isEqualTo(originalRevokedAt);
		verify(refreshTokenRepository, never()).saveAndFlush(any(RefreshToken.class));
	}

	@Test
	@DisplayName("logout swallows malformed token: returns silently, no DB call")
	void logoutSwallowsMalformed() {
		when(jwtService.parseAndValidate("garbage"))
				.thenThrow(new UnauthorizedException("INVALID_TOKEN", "broken"));

		authService.logout("garbage");

		verify(refreshTokenRepository, never()).findByTokenHash(anyString());
	}

	@Test
	@DisplayName("logout with unknown hash is a no-op (token never existed)")
	void logoutUnknownHash() {
		Tenant tenant = newTenant(SLUG, TenantStatus.ACTIVE);
		User user = newUser(EMAIL, UserStatus.ACTIVE, HASH);

		when(jwtService.parseAndValidate("ghost")).thenReturn(refreshClaims(user, tenant));
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

		authService.logout("ghost");

		verify(refreshTokenRepository, never()).saveAndFlush(any(RefreshToken.class));
	}

	@Test
	@DisplayName("logout with null/blank token is a silent no-op")
	void logoutSilentForBlank() {
		authService.logout(null);
		authService.logout("");
		verify(jwtService, never()).parseAndValidate(anyString());
	}

	// =========================================================================
	// Helpers
	// =========================================================================

	private static Tenant newTenant(String slug, TenantStatus status) {
		Tenant t = new Tenant();
		setIdViaReflection(t, UUID.randomUUID());
		t.setName("Demo Institution");
		t.setSlug(slug);
		t.setStatus(status);
		t.setPublicUuid(UUID.randomUUID());
		return t;
	}

	private static User newUser(String email, UserStatus status, String hash) {
		User u = new User();
		setIdViaReflection(u, UUID.randomUUID());
		u.setPublicUuid(UUID.randomUUID());
		u.setFirstName("Admin");
		u.setLastName("Demo");
		u.setEmail(email);
		u.setStatus(status);
		u.setEmailVerified(true);
		u.setPasswordHash(hash);
		return u;
	}

	private static RefreshToken newRefreshToken(UUID userId, UUID tenantId, String tokenHash,
	                                             Instant expiresAt, Instant revokedAt,
	                                             UUID parentTokenId) {
		RefreshToken t = new RefreshToken();
		setIdViaReflection(t, UUID.randomUUID());
		t.setTokenHash(tokenHash);
		t.setUserId(userId);
		t.setTenantId(tenantId);
		t.setExpiresAt(expiresAt);
		t.setRevokedAt(revokedAt);
		t.setParentTokenId(parentTokenId);
		return t;
	}

	private static JwtClaims refreshClaims(User user, Tenant tenant) {
		return new JwtClaims(user.getPublicUuid().toString(),
				tenant.getId(),
				tenant.getSlug(),
				Set.of(),
				TokenType.REFRESH,
				Instant.now().minusSeconds(60),
				Instant.now().plusSeconds(3600));
	}

	/**
	 * Mirrors {@code AuthServiceImpl#sha256Hex} so test fixtures can produce the
	 * exact hash that the production code will compute for a given raw input.
	 */
	private static String sha256HexLikeService(String value) {
		try {
			java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
			byte[] digest = md.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(digest);
		}
		catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Walks the class hierarchy to find {@code BaseEntity#id} and set it.
	 * Required because the {@code id} setter is intentionally package-private
	 * in production code — tests need this access to seed mock entities.
	 */
	private static void setIdViaReflection(Object entity, UUID id) {
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

}
