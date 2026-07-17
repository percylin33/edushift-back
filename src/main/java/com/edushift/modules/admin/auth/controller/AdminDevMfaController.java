package com.edushift.modules.admin.auth.controller;

import com.edushift.modules.admin.auth.AdminMfaOnboardingService;
import com.edushift.modules.admin.auth.AdminMfaOnboardingService.DevEnrolmentResult;
import com.edushift.modules.admin.auth.dto.AdminDevMfaCompleteResponse;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 15 / dev-only MFA enrolment bypass.
 *
 * <p><strong>Profile gate.</strong> The bean is registered only when
 * the active Spring profile is {@code dev} or {@code local}. In
 * {@code prod} the bean does not exist and the URL is unmapped — Spring
 * returns a 404 (no controller intercepts the request) and the static
 * code below never runs.</p>
 *
 * <p><strong>Threat model.</strong> Same as {@code DevDataInitializer}:
 * a single operator running a local dev process. The dev code is read
 * from {@code edushift.admin.dev-bypass.code} (env
 * {@code EDUSHIFT_DEV_MFA_BYPASS_CODE}) and compared in constant time
 * via {@link MessageDigest#isEqual(byte[], byte[])}.</p>
 *
 * <p><strong>What it does.</strong> Validates the onboarding bearer,
 * generates a fresh TOTP secret + recovery codes, persists them on the
 * SUPER_ADMIN row, and mints a regular access/refresh pair. Subsequent
 * {@code POST /admin/login} calls succeed against the real
 * {@code /admin/mfa/challenge} flow because the secret is real and
 * importable into any authenticator app.</p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/dev")
@Profile({"dev", "local"})
@Tag(name = "Admin Dev Tools",
		description = "Dev-only MFA bypass — bean only registered in dev/local profiles")
@RequiredArgsConstructor
public class AdminDevMfaController {

	private final AdminMfaOnboardingService onboardingService;

	@Value("${edushift.admin.dev-bypass.code:dev-bypass}")
	private String expectedDevCode;

	@PostMapping(value = "/complete-mfa",
			produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(
			summary = "Complete MFA enrolment without an authenticator app (dev only)",
			description = """
					Bean-level @Profile gate: this endpoint does NOT exist in
					prod builds. Accepts the onboardingToken returned by
					`POST /admin/login` as bearer + the static dev code in the
					`X-Dev-Code` header (default "dev-bypass", override via
					EDUSHIFT_DEV_MFA_BYPASS_CODE). Persists a real TOTP secret
					and issues a full session. Idempotent guard returns 409
					when MFA is already enabled.
					""")
	public ResponseEntity<ApiResponse<AdminDevMfaCompleteResponse>> completeMfa(
			@RequestHeader(value = "Authorization", required = false) String authHeader,
			@RequestHeader(value = "X-Dev-Code", required = false) String devCode,
			HttpServletRequest httpRequest) {

		String ip = resolveClientIp(httpRequest);

		if (expectedDevCode == null || expectedDevCode.isBlank()) {
			log.warn("[admin-dev] dev-bypass endpoint called but no code is "
					+ "configured — rejecting");
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		if (devCode == null || !constantTimeEquals(devCode, expectedDevCode)) {
			log.warn("[admin-dev] X-Dev-Code mismatch from ip={}", ip);
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		String onboardingToken = stripBearer(authHeader);
		if (onboardingToken == null || onboardingToken.isBlank()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		DevEnrolmentResult result = onboardingService.completeEnrolmentDev(
				onboardingToken, devCode, ip);
		return ResponseEntity.ok(ApiResponse.ok(new AdminDevMfaCompleteResponse(
				result.session(), result.bootstrap())));
	}

	private static String stripBearer(String header) {
		if (header == null) return null;
		String prefix = "Bearer ";
		if (header.regionMatches(true, 0, prefix, 0, prefix.length())) {
			String token = header.substring(prefix.length()).trim();
			return token.isEmpty() ? null : token;
		}
		return header.trim();
	}

	private static String resolveClientIp(HttpServletRequest request) {
		String xff = request.getHeader("X-Forwarded-For");
		if (xff != null && !xff.isBlank()) {
			int comma = xff.indexOf(',');
			return comma < 0 ? xff.trim() : xff.substring(0, comma).trim();
		}
		return request.getRemoteAddr();
	}

	private static boolean constantTimeEquals(String a, String b) {
		byte[] aa = a.getBytes(StandardCharsets.UTF_8);
		byte[] bb = b.getBytes(StandardCharsets.UTF_8);
		return MessageDigest.isEqual(aa, bb);
	}
}
