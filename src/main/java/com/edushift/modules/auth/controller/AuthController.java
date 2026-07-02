package com.edushift.modules.auth.controller;

import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.auth.dto.ForgotPasswordRequest;
import com.edushift.modules.auth.dto.GoogleLoginRequest;
import com.edushift.modules.auth.dto.LoginRequest;
import com.edushift.modules.auth.dto.MfaChallengeRequest;
import com.edushift.modules.auth.dto.MfaDisableRequest;
import com.edushift.modules.auth.dto.MfaEnrollVerifyRequest;
import com.edushift.modules.auth.dto.MfaRegenerateRequest;
import com.edushift.modules.auth.dto.MfaResponse;
import com.edushift.modules.auth.dto.RefreshTokenRequest;
import com.edushift.modules.auth.dto.ResetPasswordRequest;
import com.edushift.modules.auth.dto.ResetPasswordValidateResponse;
import com.edushift.modules.auth.dto.UserResponse;
import com.edushift.modules.auth.service.AuthService;
import com.edushift.modules.auth.service.GoogleAuthService;
import com.edushift.modules.auth.service.MfaService;
import com.edushift.modules.auth.service.PasswordResetService;
import com.edushift.infrastructure.integrations.google.GoogleIdentityProvider;
import com.edushift.infrastructure.integrations.google.GoogleProfile;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.api.ApiResponse;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.NotFoundException;
import com.edushift.shared.exception.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the {@code auth} module.
 *
 * <h3>Endpoints (all under {@code /api/v1/auth} once the {@code /api}
 * context-path from {@code application.properties} is applied)</h3>
 * <table>
 *   <caption>Auth endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>POST</td><td>/login</td><td>—</td><td>{@link AuthResponse}</td></tr>
 *   <tr><td>POST</td><td>/google</td><td>—</td><td>{@link AuthResponse}</td></tr>
 *   <tr><td>POST</td><td>/refresh</td><td>—</td><td>{@link AuthResponse}</td></tr>
 *   <tr><td>POST</td><td>/logout</td><td>required<sup>*</sup></td><td>204 No Content</td></tr>
 *   <tr><td>GET</td><td>/me</td><td>required</td><td>{@link ApiResponse}&lt;{@link UserResponse}&gt;</td></tr>
 * </table>
 *
 * <p><sup>*</sup> "Required" is enforced once {@code BE-1.6} wires the
 * {@code JwtAuthenticationFilter} into the {@code SecurityFilterChain}. In
 * Sprint 1 the chain is permissive ({@code anyRequest().permitAll()}), so
 * {@code /me} relies on {@link AuthService#currentUser()} throwing
 * {@code UnauthorizedException} when {@link
 * org.springframework.security.core.context.SecurityContextHolder} has no
 * authentication. {@code /logout} is implementation-public for now: the
 * refresh token <em>itself</em> is the proof of identity required to revoke
 * it, so authentication on top is purely a defense-in-depth control.
 *
 * <h3>Response envelopes</h3>
 * <ul>
 *   <li>{@code /login} and {@code /refresh} return the raw {@link AuthResponse}
 *       (a Bearer-shaped record) so SDKs / mobile clients can consume it
 *       without unwrapping. This mirrors OAuth 2.0 token endpoint conventions
 *       (RFC 6749 §5).</li>
 *   <li>{@code /me} returns {@link ApiResponse}{@code <UserResponse>} — the
 *       project's standard success envelope with {@code success}, {@code data},
 *       {@code message}, {@code timestamp}.</li>
 *   <li>Errors are uniformly mapped to {@link
 *       com.edushift.shared.api.ApiErrorResponse} via
 *       {@link com.edushift.shared.exception.GlobalExceptionHandler}.</li>
 * </ul>
 *
 * <h3>Tenant resolution</h3>
 * The {@code X-Tenant-Slug} header is required only on {@code /login}; on
 * {@code /refresh} and {@code /logout} the tenant is derived from the
 * {@code tenant_id} claim inside the refresh JWT (the token already encodes
 * its tenant scope). This matches Sprint 1's design: a single login endpoint
 * for all tenants, and tenant-scoped tokens for everything else. Sprint 2
 * may introduce subdomain-based resolution, at which point the header will
 * become a fallback rather than the primary signal.
 */
@RestController
@RequestMapping("/auth")
@Validated
@Tag(name = "Auth", description = "Authentication and session lifecycle")
public class AuthController {

	/**
	 * Header carrying the slug of the tenant whose users the caller wants to
	 * authenticate against. Required on {@code /login}; ignored elsewhere.
	 */
	public static final String TENANT_SLUG_HEADER = "X-Tenant-Slug";

	private final AuthService authService;
	private final GoogleAuthService googleAuthService;
	private final GoogleIdentityProvider googleIdentityProvider;
	private final TenantRepository tenantRepository;
	private final PasswordResetService passwordResetService;
	private final MfaService mfaService;

	public AuthController(
			AuthService authService,
			GoogleAuthService googleAuthService,
			GoogleIdentityProvider googleIdentityProvider,
			TenantRepository tenantRepository,
			PasswordResetService passwordResetService,
			MfaService mfaService) {
		this.authService = authService;
		this.googleAuthService = googleAuthService;
		this.googleIdentityProvider = googleIdentityProvider;
		this.tenantRepository = tenantRepository;
		this.passwordResetService = passwordResetService;
		this.mfaService = mfaService;
	}

	// ---------------------------------------------------------------------------
	// POST /login
	// ---------------------------------------------------------------------------

	/**
	 * Verifies credentials and issues a fresh access + refresh JWT pair.
	 *
	 * @param request    validated login payload (email + password)
	 * @param tenantSlug slug of the tenant the user belongs to; carried in
	 *                   {@code X-Tenant-Slug} header
	 * @return 200 {@link AuthResponse} with bearer tokens and a minimal
	 *         user summary; the {@code AuthService} throws
	 *         {@code UnauthorizedException} (401) on bad credentials and
	 *         {@code TenantNotFoundException} (404) when the slug does not
	 *         match an existing tenant
	 */
	@PostMapping("/login")
	@Operation(
			summary = "Authenticate a user and issue tokens",
			description = """
					Verifies the credentials against the tenant identified by the \
					`X-Tenant-Slug` header, returns a short-lived access JWT plus \
					a long-lived refresh JWT (rotated on each `/refresh`), and \
					stamps `last_login_at` on the user.
					""",
			responses = {
					@io.swagger.v3.oas.annotations.responses.ApiResponse(
							responseCode = "200",
							description = "Authentication succeeded",
							content = @Content(schema = @Schema(implementation = AuthResponse.class))),
					@io.swagger.v3.oas.annotations.responses.ApiResponse(
							responseCode = "400",
							description = "Validation failed (missing email/password, malformed body)"),
					@io.swagger.v3.oas.annotations.responses.ApiResponse(
							responseCode = "401",
							description = "Bad credentials, inactive tenant, or non-authenticatable user"),
					@io.swagger.v3.oas.annotations.responses.ApiResponse(
							responseCode = "404",
							description = "Tenant slug does not exist")
			})
	public ResponseEntity<?> login(
			@Valid @RequestBody LoginRequest request,
			@Parameter(description = "Slug of the tenant the user belongs to",
					required = true, example = "demo")
			@RequestHeader(value = TENANT_SLUG_HEADER)
			@NotBlank(message = "X-Tenant-Slug header is required")
			@Size(max = 64, message = "X-Tenant-Slug must not exceed 64 characters")
			String tenantSlug) {

		AuthService.LoginResult result = authService.login(request, tenantSlug);
		// Sprint 17 / BE-17.2: branch on the result variant. An MFA-enabled
		// user gets a 200 OK with an MfaRequiredResponse body; everyone
		// else gets a 200 OK with a full AuthResponse. The FE must inspect
		// the response shape (presence of `mfaToken` vs `accessToken`)
		// to know which path to take.
		if (result instanceof AuthService.LoginResult.MfaRequired mfa) {
			return ResponseEntity.ok(mfa.mfaRequiredResponse());
		}
		AuthService.LoginResult.Session session = (AuthService.LoginResult.Session) result;
		return ResponseEntity.ok(session.authResponse());
	}

	// ---------------------------------------------------------------------------
	// POST /google
	// ---------------------------------------------------------------------------

	/**
	 * Verifies a Google {@code id_token} issued by the OAuth popup on the
	 * front-end, resolves or auto-provisions the matching user inside the
	 * tenant identified by {@code X-Tenant-Slug}, and returns the same
	 * {@link AuthResponse} envelope as {@code /login}.
	 *
	 * <p>Adding a new auth provider must follow two rules from
	 * {@code auth-rules.mdc §GOOGLE LOGIN}: never tightly couple auth
	 * providers, and prepare for Microsoft/SSO. This endpoint hides the
	 * provider behind {@link GoogleIdentityProvider} so the next provider
	 * can ship without touching the controller.
	 *
	 * <h3>Errors</h3>
	 * <ul>
	 *   <li>401 {@code GOOGLE_PROVIDER_DISABLED} - feature flag is off.</li>
	 *   <li>401 {@code INVALID_GOOGLE_TOKEN} - signature / aud / exp failed.</li>
	 *   <li>401 {@code EMAIL_NOT_VERIFIED} - Google says email_verified=false.</li>
	 *   <li>401 {@code BAD_CREDENTIALS} - user not in tenant and
	 *       auto-provision is disabled (future flag, currently always on).</li>
	 *   <li>401 {@code USER_LOCKED} / {@code USER_SUSPENDED} /
	 *       {@code USER_INACTIVE} - account status forbids login.</li>
	 *   <li>404 {@code TENANT_NOT_FOUND} - slug does not exist.</li>
	 *   <li>401 {@code TENANT_INACTIVE} - tenant is not ACTIVE.</li>
	 * </ul>
	 */
	@PostMapping("/google")
	@Operation(
			summary = "Authenticate via Google OAuth (id_token flow)",
			description = """
					Verifies the Google `id_token` returned by the FE popup, \
					resolves or auto-provisions the matching user inside the \
					tenant identified by `X-Tenant-Slug`, and returns the same \
					bearer pair as `/login`. The first login from a Google \
					account auto-provisions the user as `TEACHER` (subsequent \
					logins are no-ops).
					""",
			responses = {
					@io.swagger.v3.oas.annotations.responses.ApiResponse(
							responseCode = "200",
							description = "Authentication succeeded",
							content = @Content(schema = @Schema(implementation = AuthResponse.class))),
					@io.swagger.v3.oas.annotations.responses.ApiResponse(
							responseCode = "400",
							description = "Missing or malformed id_token"),
					@io.swagger.v3.oas.annotations.responses.ApiResponse(
							responseCode = "401",
							description = "Invalid Google token, disabled provider, or non-authenticatable user"),
					@io.swagger.v3.oas.annotations.responses.ApiResponse(
							responseCode = "404",
							description = "Tenant slug does not exist")
			})
	public ResponseEntity<AuthResponse> googleLogin(
			@Valid @RequestBody GoogleLoginRequest request,
			@Parameter(description = "Slug of the tenant the user belongs to",
					required = true, example = "demo")
			@RequestHeader(value = TENANT_SLUG_HEADER)
			@NotBlank(message = "X-Tenant-Slug header is required")
			@Size(max = 64, message = "X-Tenant-Slug must not exceed 64 characters")
			String tenantSlug,
			jakarta.servlet.http.HttpServletRequest httpRequest) {

		// Surface a 404 *before* we hit Google so callers (and the FE tenant
		// picker) can distinguish "unknown tenant" from "token rejected".
		// The service still re-checks the tenant under TenantContext, so
		// this is a UX shortcut, not the security boundary. A suspended
		// tenant still falls through to the service so we can return the
		// more specific 401 TENANT_INACTIVE code.
		tenantRepository.findBySlugIgnoreCase(tenantSlug)
				.orElseThrow(() -> new NotFoundException(
						"TENANT_NOT_FOUND",
						"Tenant slug does not exist"));

		GoogleProfile profile = googleIdentityProvider.verifyIdToken(request.idToken());
		AuthResponse response = googleAuthService.loginWithGoogle(
				profile, tenantSlug, httpRequest.getRemoteAddr());
		return ResponseEntity.ok(response);
	}

	// ---------------------------------------------------------------------------
	// POST /refresh
	// ---------------------------------------------------------------------------

	/**
	 * Validates the refresh token, rotates it (revokes the old, mints a new
	 * one linked via {@code parent_token_id}), and returns a fresh token pair.
	 * <p>
	 * Replaying a refresh token that has already been rotated triggers the
	 * theft-detection branch in {@code AuthService.refresh} which revokes
	 * the entire chain.
	 */
	@PostMapping("/refresh")
	@Operation(
			summary = "Rotate the refresh token",
			description = """
					Returns a new access + refresh pair and revokes the supplied \
					refresh token. Replaying an already-rotated token is treated as \
					theft: the entire chain is revoked and a 401 `TOKEN_REUSED` \
					is returned.
					""",
			responses = {
					@io.swagger.v3.oas.annotations.responses.ApiResponse(
							responseCode = "200",
							description = "New token pair issued",
							content = @Content(schema = @Schema(implementation = AuthResponse.class))),
					@io.swagger.v3.oas.annotations.responses.ApiResponse(
							responseCode = "400",
							description = "Empty or malformed body"),
					@io.swagger.v3.oas.annotations.responses.ApiResponse(
							responseCode = "401",
							description = "Token unknown, expired, reused, or signed with the wrong key")
			})
	public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
		AuthResponse response = authService.refresh(request.refreshToken());
		return ResponseEntity.ok(response);
	}

	// ---------------------------------------------------------------------------
	// POST /logout
	// ---------------------------------------------------------------------------

	/**
	 * Revokes the supplied refresh token. Idempotent: returns 204 even when
	 * the token is unknown, malformed, or already revoked — logging out is
	 * an intent we never want to fail the user out of.
	 */
	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(
			summary = "Revoke a refresh token",
			description = """
					Marks the refresh token as revoked. Idempotent: a malformed, \
					unknown, or already-revoked token still returns 204. The \
					corresponding access token will simply expire naturally.
					""",
			security = @SecurityRequirement(name = "bearerAuth"),
			responses = {
					@io.swagger.v3.oas.annotations.responses.ApiResponse(
							responseCode = "204",
							description = "Logout accepted (idempotent)")
			})
	public void logout(@Valid @RequestBody RefreshTokenRequest request) {
		authService.logout(request.refreshToken());
	}

	// ---------------------------------------------------------------------------
	// GET /me
	// ---------------------------------------------------------------------------

	/**
	 * Returns the authenticated user. Wrapped in {@link ApiResponse} for
	 * uniformity with the rest of the read API.
	 *
	 * <p>In Sprint 1 (until BE-1.6 wires the JWT filter), this endpoint will
	 * always 401 because no Authentication is bound to the SecurityContext.
	 */
	@GetMapping("/me")
	@Operation(
			summary = "Return the authenticated user",
			security = @SecurityRequirement(name = "bearerAuth"),
			responses = {
					@io.swagger.v3.oas.annotations.responses.ApiResponse(
							responseCode = "200",
							description = "Authenticated user payload"),
					@io.swagger.v3.oas.annotations.responses.ApiResponse(
							responseCode = "401",
							description = "No authentication or principal user no longer exists")
			})
	public ApiResponse<UserResponse> me() {
		return ApiResponse.ok(authService.currentUser());
	}

	// ---------------------------------------------------------------------------
	// POST /forgot-password (Sprint 17 / BE-17.1)
	// ---------------------------------------------------------------------------

	/**
	 * Initiates the password-reset flow. Always responds 200 OK to avoid
	 * leaking whether the email exists in the tenant (anti-enumeration).
	 * The actual email is queued via the existing {@code NotificationService}
	 * template engine.
	 */
	@PostMapping("/forgot-password")
	@ResponseStatus(HttpStatus.OK)
	@Operation(
			summary = "Request a password-reset email",
			description = """
					Always responds 200 OK (anti-enumeration). When the email \
					exists in an active tenant, a reset link with a 1h TTL is \
					emailed to the address. Otherwise the request is silently \
					discarded. Rate-limited per IP by the existing \
					`RateLimitInterceptor` (5 req/h). \
					Sprint 17 / BE-17.1.
					""",
			responses = {
					@io.swagger.v3.oas.annotations.responses.ApiResponse(
							responseCode = "200",
							description = "Request accepted (email sent or silently discarded)")
			})
	public void forgotPassword(
			@Valid @RequestBody ForgotPasswordRequest request,
			HttpServletRequest httpRequest) {
		String ip = resolveClientIp(httpRequest);
		passwordResetService.requestReset(request.email(), request.tenantSlug(), ip);
	}

	// ---------------------------------------------------------------------------
	// GET /reset-password/validate (Sprint 17 / BE-17.1)
	// ---------------------------------------------------------------------------

	/**
	 * Inspects a reset token without consuming it. Returns enough information
	 * for the FE to render a custom UX (expired link, used link, valid link).
	 */
	@GetMapping("/reset-password/validate")
	@Operation(
			summary = "Validate a reset token without consuming it",
			description = """
					Read-only inspection: returns `valid=false` with a stable \
					`reasonCode` for failure modes (expired, used, superseded, \
					malformed, cross-tenant). Never throws. \
					Sprint 17 / BE-17.1.
					""",
			responses = {
					@io.swagger.v3.oas.annotations.responses.ApiResponse(
							responseCode = "200",
							description = "Validation result (always 200; check `valid` field)")
			})
	public ApiResponse<ResetPasswordValidateResponse> validateResetToken(
			@Parameter(description = "Raw reset token from the email link",
					required = true, example = "eyJhbGciOi...")
			@RequestParam("token") String token) {
		return ApiResponse.ok(passwordResetService.validateToken(token));
	}

	// ---------------------------------------------------------------------------
	// POST /reset-password (Sprint 17 / BE-17.1)
	// ---------------------------------------------------------------------------

	/**
	 * Consumes a reset token: changes the password, marks the token used,
	 * and revokes all active refresh tokens (forces re-login on every other
	 * device the user had signed into).
	 */
	@PostMapping("/reset-password")
	@ResponseStatus(HttpStatus.OK)
	@Operation(
			summary = "Consume a reset token and set a new password",
			description = """
					Atomically: validates the token, sets the new password, \
					marks the token used, and revokes all refresh tokens. \
					Returns 401 with a stable code on any failure mode. \
					Sprint 17 / BE-17.1.
					""",
			responses = {
					@io.swagger.v3.oas.annotations.responses.ApiResponse(
							responseCode = "200",
							description = "Password changed"),
					@io.swagger.v3.oas.annotations.responses.ApiResponse(
							responseCode = "400",
							description = "Password fails policy / confirm mismatch"),
					@io.swagger.v3.oas.annotations.responses.ApiResponse(
							responseCode = "401",
							description = "Token invalid, expired, used, or cross-tenant")
			})
	public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
		if (request.confirmPassword() != null
				&& !request.confirmPassword().equals(request.newPassword())) {
			throw new BadRequestException("PASSWORD_CONFIRM_MISMATCH",
					"newPassword and confirmPassword do not match");
		}
		passwordResetService.consumeToken(request.token(), request.newPassword());
	}

	// ---------------------------------------------------------------------------
	// POST /mfa/enroll/start (Sprint 17 / BE-17.2)
	// ---------------------------------------------------------------------------

	/**
	 * Step 1 of MFA enrollment. Generates a fresh TOTP secret and returns
	 * the QR code data URL plus the base32 secret (so the FE can offer a
	 * manual entry fallback). The user row is NOT yet updated — see
	 * {@link #enrollVerify}.
	 */
	@PostMapping("/mfa/enroll/start")
	@Operation(
			summary = "Start MFA enrollment (TOTP)",
			description = """
					Generates a fresh TOTP secret and a QR code data URL. The \
					user row is NOT yet updated; the secret is only persisted \
					after a successful call to `/mfa/enroll/verify` with a \
					valid first code. Sprint 17 / BE-17.2.
					""",
			security = @SecurityRequirement(name = "bearerAuth"),
			responses = {
					@io.swagger.v3.oas.annotations.responses.ApiResponse(
							responseCode = "200",
							description = "Enrollment started",
							content = @Content(schema = @Schema(implementation = MfaResponse.class))),
					@io.swagger.v3.oas.annotations.responses.ApiResponse(
							responseCode = "400",
							description = "MFA already enabled for this user")
			})
	public ApiResponse<MfaResponse> enrollStart() {
		var user = currentUserEntity();
		var start = mfaService.startEnrollment(user.getPublicUuid());
		return ApiResponse.ok(MfaResponse.enrollmentStart(
				start.secretBase32(), start.qrCodeDataUrl(), start.otpauthUri()));
	}

	// ---------------------------------------------------------------------------
	// POST /mfa/enroll/verify (Sprint 17 / BE-17.2)
	// ---------------------------------------------------------------------------

	/**
	 * Step 2 of MFA enrollment. The user scans the QR, types the first 6-digit
	 * code from their authenticator app, and the FE echoes it back here.
	 * On success the secret is persisted and 10 recovery codes are returned
	 * (shown to the user exactly once).
	 */
	@PostMapping("/mfa/enroll/verify")
	@Operation(
			summary = "Verify the first TOTP code and complete enrollment",
			description = """
					Persists the TOTP secret and recovery codes, flips \
					`user.mfaEnabled=true`, and returns the 10 recovery codes \
					(plaintext — shown to the user only this once). \
					Sprint 17 / BE-17.2.
					""",
			security = @SecurityRequirement(name = "bearerAuth"),
			responses = {
					@io.swagger.v3.oas.annotations.responses.ApiResponse(
							responseCode = "200",
							description = "Enrollment complete; recovery codes returned",
							content = @Content(schema = @Schema(implementation = MfaResponse.class))),
					@io.swagger.v3.oas.annotations.responses.ApiResponse(
							responseCode = "401",
							description = "INVALID_TOTP_CODE")
			})
	public ApiResponse<MfaResponse> enrollVerify(@Valid @RequestBody MfaEnrollVerifyRequest request) {
		var user = currentUserEntity();
		var codes = mfaService.verifyEnrollment(
				user.getPublicUuid(), request.secret(), Integer.parseInt(request.totpCode()));
		return ApiResponse.ok(MfaResponse.recoveryCodes(codes));
	}

	// ---------------------------------------------------------------------------
	// POST /mfa/challenge (Sprint 17 / BE-17.2)
	// ---------------------------------------------------------------------------

	/**
	 * Completes the login flow for users with MFA enabled. The client
	 * presents the {@code mfaToken} (received from {@code /auth/login})
	 * as the bearer and the TOTP / recovery code in the body.
	 *
	 * <p>On success returns a full {@link AuthResponse} (same shape as
	 * {@code /auth/login}).
	 */
	@PostMapping("/mfa/challenge")
	@Operation(
			summary = "Complete MFA challenge and mint full session",
			description = """
					Verifies the TOTP or recovery code and, on success, returns \
					the same AuthResponse as /auth/login. The `mfaToken` is \
					consumed (single-use, enforced by the JWT TTL). \
					Sprint 17 / BE-17.2.
					""",
			security = @SecurityRequirement(name = "bearerAuth"),
			responses = {
					@io.swagger.v3.oas.annotations.responses.ApiResponse(
							responseCode = "200",
							description = "MFA verified, full session issued",
							content = @Content(schema = @Schema(implementation = AuthResponse.class))),
					@io.swagger.v3.oas.annotations.responses.ApiResponse(
							responseCode = "401",
							description = "INVALID_TOTP_CODE / INVALID_MFA_CODE / MFA_NOT_ENABLED")
			})
	public ApiResponse<AuthResponse> challenge(
			@Valid @RequestBody MfaChallengeRequest request,
			@RequestHeader(value = TENANT_SLUG_HEADER)
			@NotBlank(message = "X-Tenant-Slug header is required")
			@Size(max = 64, message = "X-Tenant-Slug must not exceed 64 characters")
			String tenantSlug) {
		// Resolve the user from the MFA bearer (typed claim) and complete
		// the login by re-using the regular login machinery.
		AuthResponse response = authService.completeMfaLogin(
				tenantSlug, request.code());
		return ApiResponse.ok(response);
	}

	// ---------------------------------------------------------------------------
	// POST /mfa/disable (Sprint 17 / BE-17.2)
	// ---------------------------------------------------------------------------

	@PostMapping("/mfa/disable")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(
			summary = "Disable MFA (requires current password + valid TOTP)",
			security = @SecurityRequirement(name = "bearerAuth"),
			responses = {
					@io.swagger.v3.oas.annotations.responses.ApiResponse(
							responseCode = "204",
							description = "MFA disabled"),
					@io.swagger.v3.oas.annotations.responses.ApiResponse(
							responseCode = "401",
							description = "INVALID_PASSWORD / INVALID_MFA_CODE")
			})
	public void disableMfa(@Valid @RequestBody MfaDisableRequest request) {
		var user = currentUserEntity();
		mfaService.disable(user.getPublicUuid(),
				request.currentPassword(), request.mfaCode());
	}

	// ---------------------------------------------------------------------------
	// POST /mfa/recovery-codes/regenerate (Sprint 17 / BE-17.2)
	// ---------------------------------------------------------------------------

	@PostMapping("/mfa/recovery-codes/regenerate")
	@Operation(
			summary = "Regenerate the 10 recovery codes (invalidates old ones)",
			description = """
					Requires the current password. The previous recovery codes \
					are invalidated; a fresh set is returned (shown to the user \
					once). Sprint 17 / BE-17.2.
					""",
			security = @SecurityRequirement(name = "bearerAuth"),
			responses = {
					@io.swagger.v3.oas.annotations.responses.ApiResponse(
							responseCode = "200",
							description = "New recovery codes issued",
							content = @Content(schema = @Schema(implementation = MfaResponse.class)))
			})
	public ApiResponse<MfaResponse> regenerateRecoveryCodes(
			@Valid @RequestBody MfaRegenerateRequest request) {
		var user = currentUserEntity();
		var codes = mfaService.regenerateRecoveryCodes(
				user.getPublicUuid(), request.currentPassword());
		return ApiResponse.ok(MfaResponse.recoveryCodes(codes));
	}

	// ---------------------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------------------

	/**
	 * Look up the current {@link com.edushift.modules.auth.entity.User}
	 * (rather than just the {@link UserResponse} DTO) so MFA endpoints
	 * can pass the publicUuid to {@link MfaService}.
	 */
	private com.edushift.modules.auth.entity.User currentUserEntity() {
		var userResponse = authService.currentUser();
		// We need the publicUuid; reload from the response's implicit id.
		// The currentUser() method already returns a UserResponse that
		// exposes the publicUuid; we use it directly.
		return userRepository().findByPublicUuid(userResponse.publicUuid())
				.orElseThrow(() -> new UnauthorizedException("USER_NOT_FOUND",
						"Authenticated user no longer exists"));
	}

	/** Re-resolve the user repository (used by currentUserEntity). */
	private com.edushift.modules.auth.repository.UserRepository userRepository() {
		return authService.userRepository();
	}

	// ---------------------------------------------------------------------------

	/**
	 * Resolve the originating client IP, honoring {@code X-Forwarded-For}
	 * when the request came through a trusted reverse proxy. Best-effort;
	 * falls back to {@code remoteAddr} when the header is absent.
	 */
	private static String resolveClientIp(HttpServletRequest request) {
		String xff = request.getHeader("X-Forwarded-For");
		if (xff != null && !xff.isBlank()) {
			int comma = xff.indexOf(',');
			return comma < 0 ? xff.trim() : xff.substring(0, comma).trim();
		}
		return request.getRemoteAddr();
	}

}
