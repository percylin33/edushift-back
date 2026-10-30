package com.edushift.modules.admin.auth.controller;

import com.edushift.modules.admin.auth.AdminAuthService;
import com.edushift.modules.admin.auth.AdminAuthService.LoginOutcome;
import com.edushift.modules.admin.auth.AdminLoginRequest;
import com.edushift.modules.admin.auth.AdminLoginResponse;
import com.edushift.modules.admin.auth.dto.AdminInitialPasswordChangeRequest;
import com.edushift.modules.admin.auth.dto.AdminMfaRequiredResponse;
import com.edushift.modules.auth.controller.CookieHelper;
import com.edushift.modules.auth.dto.RefreshTokenRequest;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@Validated
@Tag(name = "Admin Auth", description = "SUPER_ADMIN authentication (Sprint 15)")
@RequiredArgsConstructor
public class AdminAuthController {

	private static final long REFRESH_COOKIE_MAX_AGE_SEC = 7L * 24 * 60 * 60;

	private final AdminAuthService adminAuthService;
	private final CookieHelper cookieHelper;

	@PostMapping("/login")
	@Operation(
			summary = "Authenticate a SUPER_ADMIN",
			description = """
					Verifies the credentials against the users table (cross-tenant).
					Does NOT require the `X-Tenant-Slug` header. Returns either:
					* a short-lived access JWT (15 min) plus a refresh JWT (2h)
					  (MFA already enrolled), or
					* an onboarding JWT that drives `POST /admin/mfa/enrol`
					  and `POST /admin/mfa/verify-enrol` (MFA not yet enrolled).
					Sprint 15 / BE-15.2 / F-02 / H-02.
					""",
			responses = {
					@io.swagger.v3.oas.annotations.responses.ApiResponse(
							responseCode = "200",
							description = "Authentication succeeded (session or onboarding payload)"),
					@io.swagger.v3.oas.annotations.responses.ApiResponse(
							responseCode = "401",
							description = "Bad credentials or seed sentinel"),
					@io.swagger.v3.oas.annotations.responses.ApiResponse(
							responseCode = "403",
							description = "User is not SUPER_ADMIN or account is inactive"),
					@io.swagger.v3.oas.annotations.responses.ApiResponse(
							responseCode = "429",
							description = "Too many admin login attempts")
			})
	public ResponseEntity<LoginOutcome> login(
			@Valid @RequestBody AdminLoginRequest request,
			HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		String ip = resolveClientIp(httpRequest);
		LoginOutcome outcome = adminAuthService.login(request, ip);
		if (outcome instanceof AdminLoginResponse session) {
			ResponseCookie accessCookie = cookieHelper.createAccessTokenCookie(
					session.accessToken(), session.expiresInSec());
			ResponseCookie refreshCookie = cookieHelper.createRefreshTokenCookie(
					session.refreshToken(), REFRESH_COOKIE_MAX_AGE_SEC);
			httpResponse.addHeader("Set-Cookie", accessCookie.toString());
			httpResponse.addHeader("Set-Cookie", refreshCookie.toString());
		}
		return ResponseEntity.ok(outcome);
	}

	@PostMapping("/initial-password")
	@Operation(summary = "Change the initial password before MFA enrolment",
			description = "Requires the short-lived onboarding bearer from login and only newPassword.")
	public ResponseEntity<AdminMfaRequiredResponse> changeInitialPassword(
			@RequestHeader(value = "Authorization", required = false) String authHeader,
			@Valid @RequestBody AdminInitialPasswordChangeRequest request,
			HttpServletRequest httpRequest) {
		String token = stripBearer(authHeader);
		return ResponseEntity.ok(adminAuthService.changeInitialPassword(
				token, request, resolveClientIp(httpRequest)));
	}

	@PostMapping("/logout")
	@Operation(summary = "Revoke the admin refresh token")
	public ApiResponse<Void> logout(
			@RequestBody(required = false) RefreshTokenRequest request,
			HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		String refreshToken = CookieHelper.getCookieValue(httpRequest, "edushift_refresh_token");
		if ((refreshToken == null || refreshToken.isBlank()) && request != null) {
			refreshToken = request.refreshToken();
		}
		adminAuthService.logout(refreshToken);
		httpResponse.addHeader("Set-Cookie", cookieHelper.clearAccessTokenCookie().toString());
		httpResponse.addHeader("Set-Cookie", cookieHelper.clearRefreshTokenCookie().toString());
		return ApiResponse.ok();
	}

	private static String resolveClientIp(HttpServletRequest request) {
		String xff = request.getHeader("X-Forwarded-For");
		if (xff != null && !xff.isBlank()) {
			int comma = xff.indexOf(',');
			return comma < 0 ? xff.trim() : xff.substring(0, comma).trim();
		}
		return request.getRemoteAddr();
	}

	private static String stripBearer(String header) {
		if (header == null) return null;
		String prefix = "Bearer ";
		return header.regionMatches(true, 0, prefix, 0, prefix.length())
				? header.substring(prefix.length()).trim() : header.trim();
	}
}
