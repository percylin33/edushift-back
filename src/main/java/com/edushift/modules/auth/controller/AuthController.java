package com.edushift.modules.auth.controller;

import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.auth.dto.LoginRequest;
import com.edushift.modules.auth.dto.RefreshTokenRequest;
import com.edushift.modules.auth.dto.UserResponse;
import com.edushift.modules.auth.service.AuthService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

	public AuthController(AuthService authService) {
		this.authService = authService;
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
	public ResponseEntity<AuthResponse> login(
			@Valid @RequestBody LoginRequest request,
			@Parameter(description = "Slug of the tenant the user belongs to",
					required = true, example = "demo")
			@RequestHeader(value = TENANT_SLUG_HEADER)
			@NotBlank(message = "X-Tenant-Slug header is required")
			@Size(max = 64, message = "X-Tenant-Slug must not exceed 64 characters")
			String tenantSlug) {

		AuthResponse response = authService.login(request, tenantSlug);
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

}
