package com.edushift.modules.admin.auth.controller;

import com.edushift.modules.admin.auth.AdminAuthService;
import com.edushift.modules.admin.auth.AdminAuthService.LoginOutcome;
import com.edushift.modules.admin.auth.AdminLoginRequest;
import com.edushift.modules.admin.auth.AdminLoginResponse;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@Validated
@Tag(name = "Admin Auth", description = "SUPER_ADMIN authentication (Sprint 15)")
@RequiredArgsConstructor
public class AdminAuthController {

	private final AdminAuthService adminAuthService;

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
			HttpServletRequest httpRequest) {
		String ip = resolveClientIp(httpRequest);
		LoginOutcome outcome = adminAuthService.login(request, ip);
		return ResponseEntity.ok(outcome);
	}

	@PostMapping("/logout")
	@Operation(summary = "Revoke the admin refresh token")
	public ApiResponse<Void> logout(@Valid @RequestBody com.edushift.modules.auth.dto.RefreshTokenRequest request) {
		adminAuthService.logout(request.refreshToken());
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
}
