package com.edushift.modules.admin.auth.controller;

import com.edushift.modules.admin.auth.AdminAuthService;
import com.edushift.modules.admin.auth.AdminMfaOnboardingService;
import com.edushift.modules.admin.auth.AdminMfaOnboardingService.EnrolmentPayload;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

	private final AdminMfaOnboardingService onboardingService;

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
			HttpServletRequest httpRequest) {
		String token = stripBearer(authHeader);
		String ip = resolveClientIp(httpRequest);
		return ResponseEntity.ok(onboardingService.verifyEnrolment(
				token, request.secretBase32(), request.totpCode(), ip));
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
}
