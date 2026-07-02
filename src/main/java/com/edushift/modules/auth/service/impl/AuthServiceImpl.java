package com.edushift.modules.auth.service.impl;

import com.edushift.modules.audit.events.AuditAction;
import com.edushift.modules.audit.service.AuditLogger;
import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.auth.dto.LoginRequest;
import com.edushift.modules.auth.dto.UserResponse;
import com.edushift.modules.auth.entity.RefreshToken;
import com.edushift.modules.auth.entity.RevocationReason;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.mapper.UserMapper;
import com.edushift.modules.auth.repository.RefreshTokenRepository;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.auth.service.AuthService;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.auth.service.MfaService;
import com.edushift.modules.auth.service.JwtService.JwtClaims;
import com.edushift.modules.auth.service.JwtService.TokenType;
import com.edushift.modules.auth.service.LoginAttemptService;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.exception.TenantNotFoundException;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.exception.UnauthorizedException;
import com.edushift.shared.multitenancy.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Default {@link AuthService}. See the interface javadoc for the public
 * contract; this class concentrates on:
 *
 * <ul>
 *   <li>Resolving the tenant from its slug (404 when missing, 401 when not
 *       ACTIVE — we deliberately do <em>not</em> leak whether a slug exists).</li>
 *   <li>Looking up the user inside the tenant's scope using
 *       {@link TenantContext#runAs(UUID, java.util.function.Supplier)} so that
 *       Hibernate's {@code @TenantId} discriminator filters the query and that
 *       the {@code last_login_at} flush happens with the tenant context still
 *       bound. We use {@code saveAndFlush} so the UPDATE/INSERT is pushed
 *       eagerly while the context is bound.</li>
 *   <li>Verifying the password with the shared {@link PasswordEncoder}
 *       (constant-time comparison via BCrypt).</li>
 *   <li>Issuing access + refresh JWTs through {@link JwtService} and
 *       persisting a SHA-256 hash of the refresh token in
 *       {@code refresh_tokens} so it can be revoked / rotated / detected
 *       as compromised.</li>
 * </ul>
 *
 * <h3>Why no {@code @Transactional} on {@link #login}, {@link #refresh},
 * {@link #logout}</h3>
 * Hibernate 6 calls {@link com.edushift.infrastructure.multitenancy.TenantIdResolver}
 * exactly once — at session-open time — and binds the result to the session
 * for its entire lifetime. If we put {@code @Transactional} on the public
 * method, Spring opens the session <em>before</em> we get a chance to call
 * {@link TenantContext#runAs}, and the resolver returns the
 * {@code ROOT_TENANT} sentinel. The session is then bound to {@code ROOT_TENANT},
 * which both bypasses {@code @TenantId} filters on SELECTs (cross-tenant data
 * leak risk) and writes {@code 00000000-0000-0000-0000-000000000000} into
 * {@code tenant_id} columns on INSERTs (FK violation against {@code tenants}).
 * <p>
 * Surfacing the bug took a real {@code POST /auth/login} during BE-1.5 runtime
 * verification — unit tests do not catch it because they mock the repositories.
 * <p>
 * The fix: do tenant resolution outside any transaction, then enter
 * {@code runAs}, then open the transaction <em>inside</em> the runAs lambda
 * via {@link TransactionTemplate}. The session opens after the context is
 * bound, the resolver returns the real tenant id, and Hibernate's filters
 * work correctly throughout.
 *
 * <h3>Refresh token rotation &amp; theft detection</h3>
 * Every successful {@link #refresh(String)} mints a new refresh, links it to
 * the previous one via {@code parent_token_id}, and revokes the previous
 * with reason {@code ROTATED}. If a token that is <em>already</em> revoked is
 * presented again we treat it as theft: every descendant in the chain is
 * revoked with reason {@code COMPROMISED} so neither party can keep using it.
 *
 * <h3>Timing leaks (Sprint 1 known limitation)</h3>
 * The "unknown email" branch is faster than the "wrong password" branch
 * because we skip the BCrypt check. Acceptable for Sprint 1; Sprint 2 will
 * add (a) per-IP/email rate limiting and (b) a dummy BCrypt comparison.
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

	private final TenantRepository tenantRepository;
	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final TransactionTemplate txTemplate;
	private final AuditLogger auditLogger;
	/** Sprint 14 / DEBT-AUTH-7: failed-login lockout (5 attempts → 15 min). */
	private final LoginAttemptService loginAttemptService;
	/** Sprint 17 / BE-17.2: MFA (TOTP) support. */
	private final MfaService mfaService;

	public AuthServiceImpl(TenantRepository tenantRepository,
	                       UserRepository userRepository,
	                       RefreshTokenRepository refreshTokenRepository,
	                       UserMapper userMapper,
	                       PasswordEncoder passwordEncoder,
	                       JwtService jwtService,
	                       PlatformTransactionManager txManager,
	                       AuditLogger auditLogger,
	                       LoginAttemptService loginAttemptService,
	                       MfaService mfaService) {
		this.tenantRepository = tenantRepository;
		this.userRepository = userRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.userMapper = userMapper;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.txTemplate = new TransactionTemplate(txManager);
		this.auditLogger = auditLogger;
		this.loginAttemptService = loginAttemptService;
		this.mfaService = mfaService;
	}

	/**
	 * Pre-computed BCrypt hash used as a constant-time decoy when the login
	 * email is not in the database.
	 *
	 * <p>The goal is to defeat user-enumeration timing attacks: a naive
	 * implementation returns {@code "invalid credentials"} almost instantly
	 * when the email is unknown, but takes ~80-150 ms when it exists (the
	 * BCrypt cost). An attacker probing the endpoint can list valid emails
	 * by measuring the response time. Running {@code passwordEncoder.matches}
	 * against this dummy hash on the unknown-email branch evens the cost
	 * without ever leaking whether the email exists.
	 *
	 * <p>The hash is generated at class-load time using the same
	 * {@link PasswordEncoder} bean the rest of the system uses, so the
	 * algorithm, strength, and salt format all match the real-user branch.
	 * Generated once; reused on every unknown-email login.
	 *
	 * <p>Closes DEBT-AUTH-1.
	 */
	private String timingDecoyHash;

	@Autowired
	public void initTimingDecoyHash(PasswordEncoder passwordEncoder) {
		this.timingDecoyHash = passwordEncoder.encode(
				"__edushift_timing_decoy_" + UUID.randomUUID());
	}

	// =========================================================================
	// Login
	// =========================================================================

	@Override
	public LoginResult login(LoginRequest request, String tenantSlug) {
		if (tenantSlug == null || tenantSlug.isBlank()) {
			throw new UnauthorizedException("TENANT_REQUIRED",
					"Tenant slug is required to authenticate");
		}

		// Tenant lookup runs WITHOUT tenant context — tenants is the global catalog,
		// and Spring Data JPA opens a short-lived implicit transaction for this read.
		Tenant tenant = tenantRepository.findBySlugIgnoreCase(tenantSlug)
				.orElseThrow(() -> TenantNotFoundException.forSlug(tenantSlug));

		assertTenantCanAuthenticate(tenant, tenantSlug);

		final String normalizedEmail = request.email() == null
				? "" : request.email().trim().toLowerCase();

		// runAs sets the tenant context; the inner TransactionTemplate.execute
		// then opens a NEW Hibernate session — at which point the resolver finally
		// sees the real tenant id. Both the user lookup and the refresh_tokens
		// INSERT inside doLogin are correctly scoped.
		return TenantContext.runAs(tenant.getId(), () ->
				txTemplate.execute(status ->
						doLogin(request, normalizedEmail, tenant, tenantSlug)));
	}

	private LoginResult doLogin(LoginRequest request, String normalizedEmail,
	                              Tenant tenant, String tenantSlug) {
		User user = userRepository.findByEmail(normalizedEmail).orElse(null);
		if (user == null) {
			// DEBT-AUTH-1: timing-attack mitigation. Run a BCrypt compare
			// against a pre-computed decoy hash so the unknown-email branch
			// takes the same wall-clock time as the real-user branch. The
			// result is intentionally discarded — we still return the
			// generic "Invalid email or password" error.
			passwordEncoder.matches(request.password(), timingDecoyHash);
			log.info("[auth] login failed (unknown email) tenant='{}'", tenantSlug);
			// DEBT-USR-3: persist the failure to audit_logs (was SLF4J only).
			// resourceType=email identifies the surface; we deliberately do
			// NOT persist the typed email as a resourceId since the email is
			// not known to be valid (could be a probing attempt).
			auditLogger.log(AuditAction.LOGIN_FAILED, "user_email",
					null, "login failed: unknown email for tenant '" + tenantSlug + "'",
					java.util.Map.of("tenantSlug", tenantSlug));
			// DEBT-AUTH-7: even unknown-email failures count toward lockout.
			// We use a synthesized email here so attackers can't enumerate
			// which addresses exist. The lookup in recordFailure()
			// short-circuits on user-not-found.
			loginAttemptService.recordFailure(normalizedEmail);
			throw new UnauthorizedException("BAD_CREDENTIALS",
					"Invalid email or password");
		}

		// DEBT-AUTH-7: BEFORE running BCrypt, check the lockout flag.
		// We do not run a bcrypt check on a locked account — short-circuit.
		loginAttemptService.assertNotLocked(user);

		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			log.info("[auth] login failed (bad password) tenant='{}', email='{}'",
					tenantSlug, normalizedEmail);
			// DEBT-USR-3: persist the failure to audit_logs.
			auditLogger.log(AuditAction.LOGIN_FAILED, "user",
					user.getPublicUuid(), "login failed: bad password",
					java.util.Map.of("tenantSlug", tenantSlug, "email", normalizedEmail));
			// DEBT-AUTH-7: record the failure for lockout accounting.
			loginAttemptService.recordFailure(normalizedEmail);
			throw new UnauthorizedException("BAD_CREDENTIALS",
					"Invalid email or password");
		}

		assertUserCanAuthenticate(user, tenantSlug);

		user.recordSuccessfulLogin();
		userRepository.saveAndFlush(user);

		// DEBT-AUTH-7: success — clear any pending counter so the user can
		// try again later.
		loginAttemptService.recordSuccessfulLogin(normalizedEmail);

		String accessToken = jwtService.issueAccessToken(user, tenant, user.getRoleNames());
		String refreshToken = jwtService.issueRefreshToken(user, tenant);

		// First token of a new chain — parent_token_id is null.
		persistRefreshToken(refreshToken, user, /* parentTokenId */ null);

		log.info("[auth] login OK -- tenant='{}', email='{}', publicUuid='{}'",
				tenantSlug, user.getEmail(), user.getPublicUuid());
		// DEBT-USR-3: persist successful login to audit_logs (was SLF4J only).
		auditLogger.log(AuditAction.LOGIN, "user",
				user.getPublicUuid(), "login OK",
				java.util.Map.of("tenantSlug", tenantSlug, "email", user.getEmail()));

		// Sprint 17 / BE-17.2: if the user has MFA enabled, return an MFA
		// challenge instead of the full session. The client must then call
		// /auth/mfa/challenge with the mfaToken as bearer + the TOTP code.
		if (user.isMfaEnabled()) {
			String mfaToken = jwtService.issueMfaToken(user, tenant);
			log.info("[auth] login MFA-required -- tenant='{}', publicUuid='{}'",
					tenantSlug, user.getPublicUuid());
			return new AuthService.LoginResult.MfaRequired(
					com.edushift.modules.auth.dto.MfaRequiredResponse.bearer(
							mfaToken, jwtService.mfaTokenTtlSeconds()));
		}

		return new AuthService.LoginResult.Session(AuthResponse.bearer(
				accessToken,
				refreshToken,
				jwtService.accessTokenTtlSeconds(),
				userMapper.toSummary(user)));
	}

	// =========================================================================
	// Refresh
	// =========================================================================

	@Override
	public AuthResponse refresh(String rawRefreshToken) {
		if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
			throw new UnauthorizedException("INVALID_TOKEN", "Refresh token is missing");
		}

		// Validate signature + expiration before any DB work.
		JwtClaims claims = jwtService.parseAndValidate(rawRefreshToken);
		if (claims.type() != TokenType.REFRESH) {
			throw new UnauthorizedException("INVALID_TOKEN",
					"Token is not a refresh token");
		}

		final UUID tenantId = claims.tenantId();
		if (tenantId == null) {
			throw new UnauthorizedException("INVALID_TOKEN",
					"Refresh token is missing tenant_id claim");
		}

		final String tokenHash = sha256Hex(rawRefreshToken);

		// runAs first → then open the transactional session so the resolver
		// sees the right tenant id (see class-level javadoc for the rationale).
		return TenantContext.runAs(tenantId, () ->
				txTemplate.execute(status -> doRefresh(tokenHash, claims)));
	}

	private AuthResponse doRefresh(String tokenHash, JwtClaims claims) {
		RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
				.orElseThrow(() -> new UnauthorizedException("INVALID_TOKEN",
						"Refresh token is unknown"));

		// THEFT DETECTION: a revoked token presented again poisons the entire chain.
		if (stored.isRevoked()) {
			int killed = refreshTokenRepository.revokeChain(stored.getId(),
					RevocationReason.COMPROMISED);
			log.warn("[auth] refresh THEFT DETECTED — token id={} reused after {}; "
							+ "revoked {} descendant(s)",
					stored.getId(), stored.getRevokedReason(), killed);
			throw new UnauthorizedException("TOKEN_REUSED",
					"Refresh token has been revoked. Please log in again.");
		}

		if (stored.isExpired()) {
			stored.revoke(RevocationReason.EXPIRED);
			refreshTokenRepository.saveAndFlush(stored);
			throw new UnauthorizedException("TOKEN_EXPIRED",
					"Refresh token has expired. Please log in again.");
		}

		// Re-fetch tenant + user for fresh JWT issuance. We intentionally do not
		// trust the JWT claims for tenant_status / user_status — those can have
		// changed since the access token was issued.
		Tenant tenant = tenantRepository.findById(stored.getTenantId())
				.orElseThrow(() -> new UnauthorizedException("TENANT_NOT_FOUND",
						"Tenant no longer exists"));
		assertTenantCanAuthenticate(tenant, tenant.getSlug());

		User user = userRepository.findById(stored.getUserId())
				.orElseThrow(() -> new UnauthorizedException("USER_NOT_FOUND",
						"User no longer exists"));
		assertUserCanAuthenticate(user, tenant.getSlug());

		// Mint new pair. Roles are re-read from the user (so a role grant /
		// revoke between login and refresh is reflected in the new access
		// token without forcing the user to log out).
		String newAccess = jwtService.issueAccessToken(user, tenant, user.getRoleNames());
		String newRefresh = jwtService.issueRefreshToken(user, tenant);

		// Rotate: revoke old, persist new with parent link.
		stored.revoke(RevocationReason.ROTATED);
		refreshTokenRepository.saveAndFlush(stored);
		persistRefreshToken(newRefresh, user, stored.getId());

		log.info("[auth] refresh OK -- tenant='{}', email='{}', rotated tokenId={}",
				tenant.getSlug(), user.getEmail(), stored.getId());

		return AuthResponse.bearer(
				newAccess,
				newRefresh,
				jwtService.accessTokenTtlSeconds(),
				userMapper.toSummary(user));
	}

	// =========================================================================
	// Logout
	// =========================================================================

	@Override
	public void logout(String rawRefreshToken) {
		if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
			// Logout with no token is a no-op — never fail the user out of an
			// "I want to log out" intention.
			return;
		}

		// We try to parse to extract the tenant_id, but a malformed token still
		// counts as logged-out from the client's perspective.
		final UUID tenantId;
		try {
			JwtClaims claims = jwtService.parseAndValidate(rawRefreshToken);
			tenantId = claims.tenantId();
		}
		catch (RuntimeException e) {
			log.info("[auth] logout received malformed token — no-op");
			return;
		}

		if (tenantId == null) {
			log.info("[auth] logout token has no tenant_id — no-op");
			return;
		}

		final String tokenHash = sha256Hex(rawRefreshToken);

		// runAs → then open the transactional session inside, so the SELECT
		// `findByTokenHash` is correctly scoped to the tenant (and a stolen
		// token from another tenant cannot be revoked from here).
		TenantContext.runAs(tenantId, () -> {
			txTemplate.executeWithoutResult(status -> {
				Optional<RefreshToken> stored =
						refreshTokenRepository.findByTokenHash(tokenHash);
				if (stored.isEmpty()) {
					log.info("[auth] logout token unknown — no-op");
					return;
				}
				RefreshToken token = stored.get();
				if (token.isRevoked()) {
					log.info("[auth] logout token already revoked — no-op");
					return;
				}
				token.revoke(RevocationReason.LOGOUT);
				refreshTokenRepository.saveAndFlush(token);
				log.info("[auth] logout OK -- tokenId={}, userId={}",
						token.getId(), token.getUserId());
				// DEBT-USR-3: persist logout to audit_logs (was SLF4J only).
				// We capture the publicUuid by reading the user row — the
				// refresh token's userId is the internal Long id, not the
				// public UUID. We rely on the user being loaded during the
				// request; if not, we audit with null (still a valid signal).
				UUID actorPublicUuid = userRepository.findById(token.getUserId())
						.map(User::getPublicUuid).orElse(null);
				auditLogger.log(AuditAction.LOGOUT, "user",
						actorPublicUuid, "logout OK",
						java.util.Map.of("refreshTokenId", token.getId()));
			});
			return null;
		});
	}

	// =========================================================================
	// Current user
	// =========================================================================

	@Override
	@Transactional(readOnly = true)
	public UserResponse currentUser() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
			throw new UnauthorizedException("Authentication required");
		}

		String publicUuidString = auth.getName();
		UUID publicUuid;
		try {
			publicUuid = UUID.fromString(publicUuidString);
		}
		catch (IllegalArgumentException e) {
			throw new UnauthorizedException("INVALID_PRINCIPAL",
					"Authenticated principal is not a valid UUID");
		}

		User user = userRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new UnauthorizedException("USER_NOT_FOUND",
						"Authenticated user no longer exists"));

		return userMapper.toResponse(user);
	}

	// =========================================================================
	// Issue session (login + register share this)
	// =========================================================================

	/**
	 * Issue a token pair for an already-loaded user/tenant. The caller MUST:
	 * <ul>
	 *   <li>have set the {@link TenantContext} to {@code tenant.getId()}, and</li>
	 *   <li>be running inside an active transaction (so the
	 *       {@code refresh_tokens} INSERT picks up the right
	 *       {@code @TenantId}).</li>
	 * </ul>
	 * Both {@code login()} and the upcoming {@code TenantService.register()}
	 * call this from inside their own {@code runAs + TransactionTemplate}
	 * block, so the contract is enforced at every call site.
	 *
	 * <p>This method intentionally does <strong>not</strong> stamp
	 * {@code last_login_at}. Login does that explicitly because it
	 * represents an interactive credential check; register does NOT (the
	 * user just created their account in the same request, the timestamp
	 * is meaningful only for return visits). Callers that need it should
	 * stamp it themselves before calling this method.
	 */
	@Override
	public AuthResponse issueSession(User user, Tenant tenant) {
		String accessToken = jwtService.issueAccessToken(user, tenant, user.getRoleNames());
		String refreshToken = jwtService.issueRefreshToken(user, tenant);
		persistRefreshToken(refreshToken, user, /* parentTokenId */ null);

		return AuthResponse.bearer(
				accessToken,
				refreshToken,
				jwtService.accessTokenTtlSeconds(),
				userMapper.toSummary(user));
	}

	// =========================================================================
	// MFA challenge completion (Sprint 17 / BE-17.2)
	// =========================================================================

	@Override
	public AuthResponse completeMfaLogin(String tenantSlug, String mfaCode) {
		// Resolve tenant + user from the MFA bearer (typed claim). The
		// bearer has already been validated by JwtAuthenticationFilter, so
		// we just need the principal + tenant to issue the full session.
		var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
			throw new UnauthorizedException("MFA_TOKEN_MISSING", "MFA token is required");
		}
		UUID userPublicUuid = UUID.fromString(auth.getName());
		Tenant tenant = tenantRepository.findBySlugIgnoreCase(tenantSlug)
				.orElseThrow(() -> TenantNotFoundException.forSlug(tenantSlug));
		// Defense in depth: tenant from the JWT must match the slug.
		JwtService.JwtClaims claims = jwtService.parseAndValidate(
				(String) auth.getCredentials());
		if (claims.type() != JwtService.TokenType.MFA
				|| !tenant.getId().equals(claims.tenantId())) {
			throw new UnauthorizedException("MFA_TOKEN_INVALID",
					"MFA token does not match the tenant");
		}

		return TenantContext.runAs(tenant.getId(), () ->
				txTemplate.execute(status -> {
					// Verify the TOTP / recovery code.
					mfaService.verifyChallenge(userPublicUuid, mfaCode);

					User user = userRepository.findByPublicUuid(userPublicUuid)
							.orElseThrow(() -> new UnauthorizedException("USER_NOT_FOUND",
									"User not found"));
					user.recordSuccessfulLogin();
					userRepository.saveAndFlush(user);
					loginAttemptService.recordSuccessfulLogin(user.getEmail());
					auditLogger.log(AuditAction.LOGIN, "user",
							user.getPublicUuid(), "login OK (MFA)",
							java.util.Map.of("tenantSlug", tenantSlug,
									"email", user.getEmail()));
					return issueSession(user, tenant);
				}));
	}

	@Override
	public com.edushift.modules.auth.repository.UserRepository userRepository() {
		return userRepository;
	}

	private void persistRefreshToken(String rawToken, User user, UUID parentTokenId) {
		RefreshToken token = new RefreshToken();
		token.setTokenHash(sha256Hex(rawToken));
		token.setUserId(user.getId());
		token.setExpiresAt(Instant.now().plus(refreshTtl()));
		token.setParentTokenId(parentTokenId);
		// tenant_id is auto-populated by Hibernate's @TenantId from TenantContext.
		refreshTokenRepository.saveAndFlush(token);
	}

	/**
	 * Reads the refresh TTL once per call from the JWT service so we stay in
	 * sync with the JWT's own expiration. We intentionally do NOT inject
	 * {@code JwtProperties} directly to keep this class loosely coupled.
	 */
	private Duration refreshTtl() {
		// Hard-coded to 7d here matches the JwtProperties default. If you tune
		// the JWT TTL, also tune this. A future cleanup will expose
		// JwtService.refreshTokenTtl() to remove this duplication.
		return Duration.ofDays(7);
	}

	private static void assertTenantCanAuthenticate(Tenant tenant, String tenantSlug) {
		if (tenant.getStatus() != null && tenant.getStatus().canAuthenticate()) {
			return;
		}
		log.warn("[auth] login rejected -- tenant '{}' status={}", tenantSlug, tenant.getStatus());
		throw new UnauthorizedException("TENANT_INACTIVE",
				"Tenant is not active");
	}

	private static void assertUserCanAuthenticate(User user, String tenantSlug) {
		UserStatus status = user.getStatus();
		if (status != null && status.canAuthenticate()) {
			return;
		}
		String code;
		String message;
		if (status == UserStatus.LOCKED) {
			code = "USER_LOCKED";
			message = "Account is locked. Please contact your administrator.";
		}
		else if (status == UserStatus.SUSPENDED) {
			code = "USER_SUSPENDED";
			message = "Account is suspended. Please contact your administrator.";
		}
		else if (status == UserStatus.INACTIVE) {
			code = "USER_INACTIVE";
			message = "Account is disabled. Please contact your administrator.";
		}
		else if (status == UserStatus.PENDING_VERIFICATION) {
			code = "EMAIL_NOT_VERIFIED";
			message = "Please verify your email before logging in.";
		}
		else {
			code = "USER_NOT_AUTHENTICATABLE";
			message = "Account cannot authenticate at this time.";
		}
		log.info("[auth] login rejected -- status={}, tenant='{}', email='{}'",
				status, tenantSlug, user.getEmail());
		throw new UnauthorizedException(code, message);
	}

	private static String sha256Hex(String value) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException e) {
			// SHA-256 is mandated by the JRE spec (Sun.security provider).
			throw new IllegalStateException("SHA-256 not available", e);
		}
	}

}
