package com.edushift.modules.auth.service;

import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.auth.dto.LoginRequest;
import com.edushift.modules.auth.dto.UserResponse;

/**
 * Authentication operations exposed to controllers.
 *
 * <h3>Errors (mapped by GlobalExceptionHandler)</h3>
 * <ul>
 *   <li>{@code TenantNotFoundException}      → 404 {@code TENANT_NOT_FOUND}</li>
 *   <li>{@code UnauthorizedException}        → 401, with code:
 *     <ul>
 *       <li>{@code BAD_CREDENTIALS}      — email or password incorrect</li>
 *       <li>{@code TENANT_INACTIVE}      — tenant is not ACTIVE</li>
 *       <li>{@code USER_NOT_AUTHENTICATABLE} — user status forbids login (LOCKED, INACTIVE, …)</li>
 *       <li>{@code EMAIL_NOT_VERIFIED}   — email_verified=false (when MFA gate is on)</li>
 *       <li>{@code INVALID_TOKEN}        — refresh token malformed or expired</li>
 *     </ul>
 *   </li>
 *   <li>{@code BusinessException}            → 422 {@code NOT_YET_IMPLEMENTED} (BE-1.4)</li>
 * </ul>
 */
public interface AuthService {

	/**
	 * Verifies credentials, issues a fresh access + refresh JWT pair, and
	 * stamps {@code last_login_at} on the user.
	 *
	 * @param request    validated login payload
	 * @param tenantSlug slug carried in the {@code X-Tenant-Slug} header
	 *                   (or, in Sprint 2, derived from the subdomain)
	 */
	AuthResponse login(LoginRequest request, String tenantSlug);

	/**
	 * Validates the refresh token, rotates it (revokes old + issues new), and
	 * returns a fresh access + refresh pair.
	 *
	 * <p><strong>Sprint 1 (BE-1.3) status:</strong> throws {@code BusinessException}
	 * with code {@code NOT_YET_IMPLEMENTED} until BE-1.4 ships the
	 * {@code refresh_tokens} table.
	 */
	AuthResponse refresh(String refreshToken);

	/**
	 * Marks a refresh token as revoked. Idempotent.
	 *
	 * <p><strong>Sprint 1 (BE-1.3) status:</strong> throws {@code BusinessException}
	 * with code {@code NOT_YET_IMPLEMENTED} until BE-1.4.
	 */
	void logout(String refreshToken);

	/**
	 * Returns the user owning the current Spring Security principal, or throws
	 * {@code UnauthorizedException} when no authentication is bound.
	 */
	UserResponse currentUser();

}
