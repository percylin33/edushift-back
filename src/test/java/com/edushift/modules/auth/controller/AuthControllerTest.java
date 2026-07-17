package com.edushift.modules.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.infrastructure.multitenancy.MultiTenancyConfiguration;
import com.edushift.infrastructure.multitenancy.TenantInterceptor;
import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.auth.dto.LoginRequest;
import com.edushift.modules.auth.dto.UserResponse;
import com.edushift.modules.auth.dto.UserSummary;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.auth.service.AuthService;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.tenants.exception.TenantNotFoundException;
import com.edushift.shared.exception.GlobalExceptionHandler;
import com.edushift.shared.exception.UnauthorizedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Controller-slice tests for {@link AuthController}.
 * <p>
 * These tests intentionally do <strong>not</strong> spin up the full
 * application context — they use {@link WebMvcTest} so that:
 * <ul>
 *   <li>JPA / Flyway / Redis are not required (fast, isolated, no Postgres
 *       running on the dev machine to pass).</li>
 *   <li>The {@link AuthService} is mocked so we exercise <em>only</em> the
 *       HTTP layer: routing, header binding, request validation, response
 *       envelopes, and exception → JSON mapping via
 *       {@link GlobalExceptionHandler}.</li>
 * </ul>
 *
 * <p>The Spring Security auto-configuration is loaded so the request flow
 * mirrors production (CSRF disabled in {@code SecurityConfig}; we still pass
 * {@link org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors#csrf()}
 * defensively in case BE-1.6 enables CSRF for some routes).
 */
/**
 * <h3>Slice scope</h3>
 * <p>Two infrastructure beans are explicitly excluded so the slice stays
 * narrow:
 * <ul>
 *   <li>{@link MultiTenancyConfiguration} — implements
 *       {@code WebMvcConfigurer}, which {@code @WebMvcTest} auto-scans.
 *       It also registers the tenant interceptor and Hibernate multi-tenancy
 *       wiring (none of which are exercised by HTTP-shape tests).</li>
 *   <li>{@link TenantInterceptor} — a {@code @Component} that implements
 *       {@code HandlerInterceptor}. Spring Boot's
 *       {@code WebMvcTypeExcludeFilter} <em>does</em> include
 *       {@code HandlerInterceptor} in the slice by default, so the interceptor
 *       would otherwise be instantiated and pull in {@code CurrentUserProvider},
 *       {@code TenantContext}, and the principal graph.</li>
 * </ul>
 * Cross-tenant interception is exercised by {@code AuthTenantIsolationIT}
 * (BE-1.7) which uses a full {@code @SpringBootTest} context.
 */
@WebMvcTest(
		controllers = AuthController.class,
		excludeFilters = @ComponentScan.Filter(
				type = FilterType.ASSIGNABLE_TYPE,
				classes = {MultiTenancyConfiguration.class, TenantInterceptor.class}))
@Import({
		GlobalExceptionHandler.class,
		com.edushift.config.SecurityConfig.class,
		com.edushift.config.WebConfiguration.class,
		com.edushift.test.EdushiftWebMvcTestConfig.class,
})
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private AuthService authService;

	@MockitoBean
	private com.edushift.modules.auth.service.PasswordResetService passwordResetService;

	/**
	 * BE-1.6 wired the JWT filter into the security chain via the production
	 * {@code SecurityConfig}. The filter is constructor-injected with
	 * {@link JwtService}, so the slice has to provide a bean for the chain to
	 * boot. The filter itself is never exercised in this slice — request
	 * authentication is simulated via
	 * {@link org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors#authentication SecurityMockMvcRequestPostProcessors.authentication(...)}
	 * which seeds the {@link org.springframework.security.core.context.SecurityContext SecurityContext}
	 * directly and short-circuits the filter.
	 */
	@MockitoBean
	private com.edushift.shared.security.LmsRoleAuthorityMapper roleAuthorityMapper;
	@MockitoBean
	private JwtService jwtService;

	// ---------------------------------------------------------------------------
	// Test fixtures
	// ---------------------------------------------------------------------------

	private static final String BASE = "/v1/auth";
	private static final String DEMO_SLUG = "demo";
	private static final String VALID_EMAIL = "admin@demo.edushift.pe";
	private static final String VALID_PASSWORD = "Edushift123!";
	private static final String FAKE_ACCESS = "fake.access.jwt";
	private static final String FAKE_REFRESH = "fake.refresh.jwt";

	private AuthResponse stubAuthResponse() {
		UserSummary summary = new UserSummary(
				UUID.randomUUID(), "Admin Demo", VALID_EMAIL, null, UserStatus.ACTIVE);
		return AuthResponse.bearer(FAKE_ACCESS, FAKE_REFRESH, 900L, summary);
	}

	private UserResponse stubUserResponse() {
		return new UserResponse(
				UUID.randomUUID(),
				"Admin",
				"Demo",
				"Admin Demo",
				VALID_EMAIL,
				null, null,
				UserStatus.ACTIVE,
				true, false,
				java.util.Set.of("TENANT_ADMIN"),
				java.util.Set.of("LMS_TASK_READ", "LMS_TASK_CREATE", "LMS_PAYMENT_ADMIN"),
				Instant.now(), Instant.now(), Instant.now());
	}

	private String json(Object value) throws Exception {
		return objectMapper.writeValueAsString(value);
	}

	// ===========================================================================
	// POST /login
	// ===========================================================================

	@Nested
	@DisplayName("POST /v1/auth/login")
	class Login {

		@Test
		@DisplayName("happy path — 200 with bearer envelope + tokens")
		void happyPath() throws Exception {
			given(authService.login(any(LoginRequest.class), eq(DEMO_SLUG)))
					.willReturn(new AuthService.LoginResult.Session(stubAuthResponse()));

			LoginRequest body = new LoginRequest(VALID_EMAIL, VALID_PASSWORD);

			mockMvc.perform(post(BASE + "/login")
							.with(csrf())
							.header(AuthController.TENANT_SLUG_HEADER, DEMO_SLUG)
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(body)))
					.andExpect(status().isOk())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
					.andExpect(jsonPath("$.accessToken").value(FAKE_ACCESS))
					.andExpect(jsonPath("$.refreshToken").value(FAKE_REFRESH))
					.andExpect(jsonPath("$.tokenType").value("Bearer"))
					.andExpect(jsonPath("$.expiresInSec").value(900))
					.andExpect(jsonPath("$.user.email").value(VALID_EMAIL));

			then(authService).should(times(1)).login(any(LoginRequest.class), eq(DEMO_SLUG));
		}

		@Test
		@DisplayName("missing X-Tenant-Slug header → 400 with field=X-Tenant-Slug")
		void missingTenantHeader() throws Exception {
			LoginRequest body = new LoginRequest(VALID_EMAIL, VALID_PASSWORD);

			mockMvc.perform(post(BASE + "/login")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(body)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors[0].field").value("X-Tenant-Slug"));

			then(authService).should(never()).login(any(), any());
		}

		@Test
		@DisplayName("blank email → 400 with VALIDATION error")
		void blankEmail() throws Exception {
			LoginRequest body = new LoginRequest("", VALID_PASSWORD);

			mockMvc.perform(post(BASE + "/login")
							.with(csrf())
							.header(AuthController.TENANT_SLUG_HEADER, DEMO_SLUG)
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(body)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.success").value(false))
					.andExpect(jsonPath("$.errors[0].field").value("email"));

			then(authService).should(never()).login(any(), any());
		}

		@Test
		@DisplayName("malformed JSON body → 400 MALFORMED_REQUEST")
		void malformedBody() throws Exception {
			mockMvc.perform(post(BASE + "/login")
							.with(csrf())
							.header(AuthController.TENANT_SLUG_HEADER, DEMO_SLUG)
							.contentType(MediaType.APPLICATION_JSON)
							.content("not even close to json"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors[0].code").value("MALFORMED_REQUEST"));

			then(authService).should(never()).login(any(), any());
		}

		@Test
		@DisplayName("unknown tenant → 404 RESOURCE_NOT_FOUND propagates from service")
		void unknownTenant() throws Exception {
			given(authService.login(any(LoginRequest.class), eq("ghost")))
					.willThrow(TenantNotFoundException.forSlug("ghost"));

			LoginRequest body = new LoginRequest(VALID_EMAIL, VALID_PASSWORD);

			mockMvc.perform(post(BASE + "/login")
							.with(csrf())
							.header(AuthController.TENANT_SLUG_HEADER, "ghost")
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(body)))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_FOUND"));
		}

		@Test
		@DisplayName("bad credentials → 401 BAD_CREDENTIALS propagates from service")
		void badCredentials() throws Exception {
			given(authService.login(any(LoginRequest.class), eq(DEMO_SLUG)))
					.willThrow(new UnauthorizedException("BAD_CREDENTIALS",
							"Invalid email or password"));

			LoginRequest body = new LoginRequest(VALID_EMAIL, "wrong");

			mockMvc.perform(post(BASE + "/login")
							.with(csrf())
							.header(AuthController.TENANT_SLUG_HEADER, DEMO_SLUG)
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(body)))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.errors[0].code").value("BAD_CREDENTIALS"));
		}

	}

	// ===========================================================================
	// POST /refresh
	// ===========================================================================

	@Nested
	@DisplayName("POST /v1/auth/refresh")
	class Refresh {

		@Test
		@DisplayName("happy path — 200 with new bearer envelope")
		void happyPath() throws Exception {
			given(authService.refresh(eq(FAKE_REFRESH))).willReturn(stubAuthResponse());

			mockMvc.perform(post(BASE + "/refresh")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"refreshToken\":\"" + FAKE_REFRESH + "\"}"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.accessToken").value(FAKE_ACCESS))
					.andExpect(jsonPath("$.tokenType").value("Bearer"));
		}

		@Test
		@DisplayName("blank token → 400 with VALIDATION error and service untouched")
		void blankToken() throws Exception {
			mockMvc.perform(post(BASE + "/refresh")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"refreshToken\":\"\"}"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors[0].field").value("refreshToken"));

			then(authService).should(never()).refresh(any());
		}

		@Test
		@DisplayName("token reused → 401 TOKEN_REUSED propagates from service")
		void tokenReused() throws Exception {
			given(authService.refresh(any())).willThrow(
					new UnauthorizedException("TOKEN_REUSED",
							"Refresh token has been revoked. Please log in again."));

			mockMvc.perform(post(BASE + "/refresh")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"refreshToken\":\"" + FAKE_REFRESH + "\"}"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.errors[0].code").value("TOKEN_REUSED"));
		}

	}

	// ===========================================================================
	// POST /logout
	// ===========================================================================

	@Nested
	@DisplayName("POST /v1/auth/logout")
	class Logout {

		@Test
		@DisplayName("happy path — 204 No Content with empty body")
		void happyPath() throws Exception {
			mockMvc.perform(post(BASE + "/logout")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"refreshToken\":\"" + FAKE_REFRESH + "\"}"))
					.andExpect(status().isNoContent())
					.andExpect(content().string(""));

			then(authService).should(times(1)).logout(eq(FAKE_REFRESH));
		}

		@Test
		@DisplayName("blank token → 400 (validation runs before idempotent logout)")
		void blankToken() throws Exception {
			mockMvc.perform(post(BASE + "/logout")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"refreshToken\":\"\"}"))
					.andExpect(status().isBadRequest());

			then(authService).should(never()).logout(any());
		}

	}

	// ===========================================================================
	// GET /me
	// ===========================================================================

	@Nested
	@DisplayName("GET /v1/auth/me")
	class Me {

		/**
		 * Builds a real {@link JwtAuthenticationToken} so the {@code Authentication}
		 * looks identical to what {@code JwtAuthenticationFilter} produces in
		 * production. We use {@code authentication(...)} (not {@code user(...)})
		 * because some downstream consumers — notably
		 * {@code AuthServiceImpl.currentUser()} — expect
		 * {@code Authentication#getName()} to be a UUID string. With
		 * {@code user(...)} the name would be the literal {@code "username"},
		 * which would cause unrelated test failures the moment we wire the real
		 * service in an integration test.
		 */
		private JwtAuthenticationToken jwtAuth() {
			JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
					UUID.randomUUID(),
					UUID.randomUUID(),
					DEMO_SLUG,
					VALID_EMAIL);
			return new JwtAuthenticationToken(principal, "test.access.jwt", List.of());
		}

		@Test
		@DisplayName("authenticated context → 200 wrapped in ApiResponse envelope")
		void happyPath() throws Exception {
			given(authService.currentUser()).willReturn(stubUserResponse());

			mockMvc.perform(get(BASE + "/me").with(authentication(jwtAuth())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success").value(true))
					.andExpect(jsonPath("$.data.email").value(VALID_EMAIL))
					.andExpect(jsonPath("$.data.firstName").value("Admin"))
					.andExpect(jsonPath("$.data.status").value("ACTIVE"));
		}

		@Test
		@DisplayName("no bearer → 401 UNAUTHORIZED via SecurityFilterChain entry point")
		void unauthenticated() throws Exception {
			// BE-1.6: with .anyRequest().authenticated(), an anonymous request
			// is rejected by the security chain BEFORE reaching the controller.
			// Spring Security wraps the access-denied as
			// InsufficientAuthenticationException and routes it to our
			// authenticationEntryPoint, which delegates to GlobalExceptionHandler.
			mockMvc.perform(get(BASE + "/me"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.errors[0].code").value("UNAUTHORIZED"));

			then(authService).should(never()).currentUser();
		}

	}

}
