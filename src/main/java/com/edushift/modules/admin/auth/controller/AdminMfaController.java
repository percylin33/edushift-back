package com.edushift.modules.admin.auth.controller;

import com.edushift.modules.admin.auth.AdminAuthService;
import com.edushift.modules.admin.auth.AdminLoginResponse;
import com.edushift.modules.admin.auth.AdminMfaOnboardingService;
import com.edushift.modules.admin.auth.AdminMfaOnboardingService.EnrolmentPayload;
import com.edushift.modules.auth.controller.CookieHelper;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 15 / F-02 / H-02: TOTP enrolment handshake for SUPER_ADMIN.
 *
 * <p>Both endpoints require the {@code ONBOARDING} bearer produced by
 * {@code POST /admin/login} when MFA is not yet enabled.</p>
 */
@RestController
@RequestMapping("/admin/mfa")
@Validated
@Tag(name = "Admin MFA", description = "TOTP enrolment for SUPER_ADMIN (Sprint 15 / F-02)")
@RequiredArgsConstructor
public class AdminMfaController {
	private static final long REFRESH_COOKIE_MAX_AGE_SEC = 7L * 24 * 60 * 60;

	private final AdminMfaOnboardingService onboardingService;
	private final CookieHelper cookieHelper;

	@PostMapping("/challenge")
	@Operation(summary = "Verify the login TOTP and issue the Super Admin session")
	public ResponseEntity<AdminAuthService.LoginOutcome> challenge(
			@RequestHeader(value = "Authorization", required = false) String authHeader,
			@Valid @RequestBody VerifyChallengeRequest request,
			HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		AdminAuthService.LoginOutcome outcome = onboardingService.completeLoginChallenge(
				stripBearer(authHeader), request.code(), resolveClientIp(httpRequest));
		writeSessionCookies(outcome, httpResponse);
		return ResponseEntity.ok(outcome);
	}

	@PostMapping("/enrol")
	@Operation(summary = "Start TOTP enrolment (step 1) — returns QR + secret")
	public ApiResponse<EnrolmentPayload> enrol(
			@RequestHeader(value = "Authorization", required = false) String authHeader,
			HttpServletRequest httpRequest) {
		String token = stripBearer(authHeader);
		String ip = resolveClientIp(httpRequest);
		return ApiResponse.ok(onboardingService.startEnrolment(token, ip));
	}

	@PostMapping("/verify-enrol")
	@Operation(summary = "Complete TOTP enrolment (step 2) — issues full session")
	public ResponseEntity<AdminAuthService.LoginOutcome> verifyEnrol(
			@RequestHeader(value = "Authorization", required = false) String authHeader,
			@Valid @RequestBody VerifyEnrolRequest request,
			HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		String token = stripBearer(authHeader);
		String ip = resolveClientIp(httpRequest);
		AdminAuthService.LoginOutcome outcome = onboardingService.verifyEnrolment(
				token, request.secretBase32(), request.totpCode(), ip);
		writeSessionCookies(outcome, httpResponse);
		return ResponseEntity.ok(outcome);
	}

	private void writeSessionCookies(AdminAuthService.LoginOutcome outcome, HttpServletResponse response) {
		if (outcome instanceof AdminLoginResponse session) {
			ResponseCookie accessCookie = cookieHelper.createAccessTokenCookie(session.accessToken(), session.expiresInSec());
			ResponseCookie refreshCookie = cookieHelper.createRefreshTokenCookie(session.refreshToken(), REFRESH_COOKIE_MAX_AGE_SEC);
			response.addHeader("Set-Cookie", accessCookie.toString());
			response.addHeader("Set-Cookie", refreshCookie.toString());
		}
	}

	private static String stripBearer(String header) {
		if (header == null) return null;
		String prefix = "Bearer ";
		return header.regionMatches(true, 0, prefix, 0, prefix.length())
				? header.substring(prefix.length()).trim()
				: header.trim();
	}

	private static String resolveClientIp(HttpServletRequest request) {
		String xff = request.getHeader("X-Forwarded-For");
		if (xff != null && !xff.isBlank()) {
			int comma = xff.indexOf(',');
			return comma < 0 ? xff.trim() : xff.substring(0, comma).trim();
		}
		return request.getRemoteAddr();
	}

	public record VerifyEnrolRequest(
			@NotBlank String secretBase32,
			@NotNull @Min(0) Integer totpCode
	) {
		// Validation handled at the call site.
	}

	public record VerifyChallengeRequest(@NotBlank String code) {}
}
