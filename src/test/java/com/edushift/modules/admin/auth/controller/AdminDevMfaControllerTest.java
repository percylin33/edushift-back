package com.edushift.modules.admin.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.infrastructure.multitenancy.MultiTenancyConfiguration;
import com.edushift.infrastructure.multitenancy.TenantInterceptor;
import com.edushift.modules.admin.auth.AdminLoginResponse;
import com.edushift.modules.admin.auth.AdminLoginResponse.AdminUserSummary;
import com.edushift.modules.admin.auth.AdminMfaOnboardingService;
import com.edushift.modules.admin.auth.AdminMfaOnboardingService.DevEnrolmentResult;
import com.edushift.modules.admin.auth.dto.AdminDevMfaCompleteResponse;
import com.edushift.modules.admin.auth.dto.AdminDevMfaCompleteResponse.Bootstrap;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.GlobalExceptionHandler;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Controller-slice tests for {@link AdminDevMfaController}.
 *
 * <p>Two angles are covered:
 * <ol>
 *   <li><strong>Functional</strong>: under {@code @ActiveProfiles("dev")} the
 *       bean is registered and the four documented branches behave correctly
 *       (missing/wrong code → 401, already enrolled → 409, happy path → 200
 *       with session + bootstrap payload).</li>
 *   <li><strong>Profile gate</strong>: under {@code @ActiveProfiles("prod")}
 *       the bean is NOT registered, so a request to the path falls through
 *       to the standard 404 handler — proving the dev surface is invisible
 *       in non-dev profiles.</li>
 * </ol>
 *
 * <p>The {@code @TestPropertySource} override forces a known dev code
 * ("test-code") instead of the default so the test never depends on
 * external environment variables.</p>
 */
@WebMvcTest(
		controllers = AdminDevMfaController.class,
		excludeFilters = @ComponentScan.Filter(
				type = FilterType.ASSIGNABLE_TYPE,
				classes = {MultiTenancyConfiguration.class, TenantInterceptor.class}))
@Import({
		GlobalExceptionHandler.class,
		com.edushift.config.SecurityConfig.class,
		com.edushift.config.WebConfiguration.class,
		com.edushift.test.EdushiftWebMvcTestConfig.class,
})
@ActiveProfiles("dev")
@TestPropertySource(properties = {
		"edushift.admin.dev-bypass.code=test-code"
})
class AdminDevMfaControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AdminMfaOnboardingService onboardingService;

	private static final String URL = "/v1/admin/dev/complete-mfa";

	private static AdminLoginResponse sampleSession() {
		return AdminLoginResponse.bearer(
				"access-xyz", "refresh-xyz", 900L,
				new AdminUserSummary(
						UUID.randomUUID().toString(),
						"super@edushift.pe",
						"Super", "Admin",
						"Super Admin",
						List.of("SUPER_ADMIN")));
	}

	private static DevEnrolmentResult sampleResult() {
		return new DevEnrolmentResult(
				sampleSession(),
				new Bootstrap(
						"JBSWY3DPEHPK3PXP",
						"otpauth://totp/EduShift:super@edushift.pe?secret=JBSWY3DPEHPK3PXP",
						List.of("ABCDE-FGHIJ", "KLMNO-PQRST")));
	}

	@Nested
	@DisplayName("dev profile active — bean registered")
	class WithDevProfile {

		@Test
		@DisplayName("401 when X-Dev-Code header is missing")
		void rejectsMissingDevCode() throws Exception {
			mockMvc.perform(post(URL)
							.header(HttpHeaders.AUTHORIZATION, "Bearer onboarding.jwt"))
					.andExpect(status().isUnauthorized());

			verify(onboardingService, never()).completeEnrolmentDev(any(), any(), any());
		}

		@Test
		@DisplayName("401 when X-Dev-Code does not match the configured value")
		void rejectsWrongDevCode() throws Exception {
			mockMvc.perform(post(URL)
							.header(HttpHeaders.AUTHORIZATION, "Bearer onboarding.jwt")
							.header("X-Dev-Code", "not-the-right-one"))
					.andExpect(status().isUnauthorized());

			verify(onboardingService, never()).completeEnrolmentDev(any(), any(), any());
		}

		@Test
		@DisplayName("401 when Authorization header is missing")
		void rejectsMissingBearer() throws Exception {
			mockMvc.perform(post(URL)
							.header("X-Dev-Code", "test-code"))
					.andExpect(status().isUnauthorized());

			verify(onboardingService, never()).completeEnrolmentDev(any(), any(), any());
		}

		@Test
		@DisplayName("409 ALREADY_ENROLLED when service throws ConflictException")
		void mapsAlreadyEnrolled() throws Exception {
			given(onboardingService.completeEnrolmentDev(
					eq("onboarding.jwt"), eq("test-code"), anyString()))
					.willThrow(new ConflictException("ALREADY_ENROLLED",
							"MFA is already enrolled"));

			mockMvc.perform(post(URL)
							.header(HttpHeaders.AUTHORIZATION, "Bearer onboarding.jwt")
							.header("X-Dev-Code", "test-code"))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.success").value(false))
					.andExpect(jsonPath("$.errors[0].code").value("ALREADY_ENROLLED"));
		}

		@Test
		@DisplayName("200 OK with session + bootstrap payload on happy path")
		void happyPath() throws Exception {
			given(onboardingService.completeEnrolmentDev(
					eq("onboarding.jwt"), eq("test-code"), anyString()))
					.willReturn(sampleResult());

			mockMvc.perform(post(URL)
							.header(HttpHeaders.AUTHORIZATION, "Bearer onboarding.jwt")
							.header("X-Dev-Code", "test-code"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success").value(true))
					.andExpect(jsonPath("$.data.session.accessToken").value("access-xyz"))
					.andExpect(jsonPath("$.data.session.refreshToken").value("refresh-xyz"))
					.andExpect(jsonPath("$.data.session.tokenType").value("Bearer"))
					.andExpect(jsonPath("$.data.session.user.email").value("super@edushift.pe"))
					.andExpect(jsonPath("$.data.session.user.roles[0]").value("SUPER_ADMIN"))
					.andExpect(jsonPath("$.data.bootstrap.totpSecret").value("JBSWY3DPEHPK3PXP"))
					.andExpect(jsonPath("$.data.bootstrap.otpauthUri",
							org.hamcrest.Matchers.startsWith("otpauth://totp/")))
					.andExpect(jsonPath("$.data.bootstrap.recoveryCodes.length()").value(2));

			verify(onboardingService, times(1))
					.completeEnrolmentDev(eq("onboarding.jwt"), eq("test-code"), anyString());
		}
	}
}
