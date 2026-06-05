package com.edushift.modules.tenants.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.infrastructure.multitenancy.MultiTenancyConfiguration;
import com.edushift.infrastructure.multitenancy.TenantInterceptor;
import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.auth.dto.UserSummary;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.tenants.dto.BrandingDto;
import com.edushift.modules.tenants.dto.RegisterTenantRequest;
import com.edushift.modules.tenants.dto.TenantResponse;
import com.edushift.modules.tenants.dto.TenantSummary;
import com.edushift.modules.tenants.dto.UpdateTenantRequest;
import com.edushift.modules.tenants.entity.TenantPlan;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.exception.TenantNotFoundException;
import com.edushift.modules.tenants.service.TenantService;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashMap;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Controller-slice tests for {@link TenantController}.
 *
 * <p>Same shape as {@code AuthControllerTest}: {@link WebMvcTest} so the
 * slice stays narrow (no JPA / Flyway / DB), the {@link TenantService}
 * is mocked, and we exercise <em>only</em> the HTTP layer — routing,
 * request validation, exception → JSON envelope mapping, and the
 * {@code @PreAuthorize("hasRole('TENANT_ADMIN')")} role gate.
 *
 * <h3>Why JpaAuditing has to be excluded explicitly</h3>
 * The production {@code MultiTenancyConfiguration} brings JPA auditing
 * along with it; excluding the configuration also drops the auditing
 * import. Same rationale as {@code AuthControllerTest} — see the
 * Javadoc there for the long version.
 *
 * <h3>Role gate fixture</h3>
 * {@link #adminAuth()} produces a real {@link JwtAuthenticationToken}
 * with a single {@code ROLE_TENANT_ADMIN} authority — exactly what
 * {@code JwtAuthenticationFilter} would publish in production once
 * BE-2.4 added roles to the JWT. {@link #userAuth()} is the same shape
 * but with no authorities, so we can pin the 403 path on
 * {@code PATCH /me} and {@code POST /me/activate}.
 */
@WebMvcTest(
		controllers = TenantController.class,
		excludeFilters = @ComponentScan.Filter(
				type = FilterType.ASSIGNABLE_TYPE,
				classes = {MultiTenancyConfiguration.class, TenantInterceptor.class}))
@Import({
		GlobalExceptionHandler.class,
		com.edushift.config.SecurityConfig.class,
		com.edushift.config.WebConfiguration.class
})
class TenantControllerTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;

	@MockitoBean private TenantService tenantService;

	/** Filter dependency — never invoked in this slice (auth is simulated). */
	@MockitoBean private JwtService jwtService;

	private static final String BASE = "/v1/tenants";
	private static final String SLUG = "acme";

	private String json(Object value) throws Exception {
		return objectMapper.writeValueAsString(value);
	}

	private static JwtAuthenticationToken adminAuth() {
		return authWith(List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")));
	}

	private static JwtAuthenticationToken userAuth() {
		return authWith(List.<GrantedAuthority>of());
	}

	private static JwtAuthenticationToken authWith(List<GrantedAuthority> authorities) {
		JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
				UUID.randomUUID(),
				UUID.randomUUID(),
				SLUG,
				"admin@" + SLUG + ".test");
		return new JwtAuthenticationToken(principal, "fake.access.jwt", authorities);
	}

	private TenantSummary stubSummary() {
		return new TenantSummary(
				UUID.randomUUID(),
				"Acme Corp",
				SLUG,
				TenantStatus.ACTIVE,
				new BrandingDto("#0F62FE", null, null, null));
	}

	private TenantResponse stubResponse(TenantStatus status) {
		return new TenantResponse(
				UUID.randomUUID(),
				"Acme Corp",
				SLUG,
				null,
				status,
				TenantPlan.TRIAL,
				Instant.parse("2030-01-01T00:00:00Z"),
				new BrandingDto("#0F62FE", null, null, null),
				new HashMap<>(),
				new HashMap<>(),
				500,
				50,
				Instant.now(),
				Instant.now());
	}

	// ===========================================================================
	// GET /by-slug/{slug}  (public)
	// ===========================================================================

	@Nested
	@DisplayName("GET /v1/tenants/by-slug/{slug}")
	class GetBySlug {

		@Test
		@DisplayName("happy path — 200 with TenantSummary inside ApiResponse envelope")
		void happyPath() throws Exception {
			given(tenantService.findBySlug(SLUG)).willReturn(stubSummary());

			mockMvc.perform(get(BASE + "/by-slug/" + SLUG))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success").value(true))
					.andExpect(jsonPath("$.data.slug").value(SLUG))
					.andExpect(jsonPath("$.data.status").value("ACTIVE"))
					.andExpect(jsonPath("$.data.branding.primaryColor").value("#0F62FE"));
		}

		@Test
		@DisplayName("unknown slug → 404 RESOURCE_NOT_FOUND")
		void notFound() throws Exception {
			given(tenantService.findBySlug("ghost")).willThrow(TenantNotFoundException.forSlug("ghost"));

			mockMvc.perform(get(BASE + "/by-slug/ghost"))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.success").value(false))
					.andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_FOUND"));
		}

		@Test
		@DisplayName("slug shorter than 2 chars → 400 (validation on path variable)")
		void shortSlugRejected() throws Exception {
			mockMvc.perform(get(BASE + "/by-slug/a"))
					.andExpect(status().isBadRequest());

			then(tenantService).should(never()).findBySlug(any());
		}
	}

	// ===========================================================================
	// GET /me  (authenticated)
	// ===========================================================================

	@Nested
	@DisplayName("GET /v1/tenants/me")
	class GetMe {

		@Test
		@DisplayName("authenticated → 200 with TenantResponse inside envelope")
		void happyPath() throws Exception {
			given(tenantService.findCurrent()).willReturn(stubResponse(TenantStatus.ACTIVE));

			mockMvc.perform(get(BASE + "/me").with(authentication(userAuth())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.slug").value(SLUG))
					.andExpect(jsonPath("$.data.plan").value("TRIAL"))
					.andExpect(jsonPath("$.data.maxStudents").value(500));
		}

		@Test
		@DisplayName("anonymous → 401 UNAUTHORIZED")
		void unauthenticated() throws Exception {
			mockMvc.perform(get(BASE + "/me"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.errors[0].code").value("UNAUTHORIZED"));

			then(tenantService).should(never()).findCurrent();
		}
	}

	// ===========================================================================
	// PATCH /me  (TENANT_ADMIN)
	// ===========================================================================

	@Nested
	@DisplayName("PATCH /v1/tenants/me")
	class PatchMe {

		@Test
		@DisplayName("TENANT_ADMIN → 200 with the post-merge TenantResponse")
		void happyPath() throws Exception {
			UpdateTenantRequest patch = new UpdateTenantRequest(
					"New Name", null,
					new BrandingDto("#FF6900", null, null, null),
					null, null, null, null);
			given(tenantService.updateCurrent(any(UpdateTenantRequest.class)))
					.willReturn(stubResponse(TenantStatus.ACTIVE));

			mockMvc.perform(patch(BASE + "/me")
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(patch)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success").value(true));
		}

		@Test
		@DisplayName("authenticated but missing role → 403 FORBIDDEN")
		void forbiddenWithoutRole() throws Exception {
			// Important: the body has to satisfy validation. If validation
			// fails (400) we never reach the @PreAuthorize gate, and we'd be
			// asserting the wrong thing.
			UpdateTenantRequest patch = new UpdateTenantRequest(
					"Valid Name", null, null, null, null, null, null);

			mockMvc.perform(patch(BASE + "/me")
							.with(csrf())
							.with(authentication(userAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(patch)))
					.andExpect(status().isForbidden());

			then(tenantService).should(never()).updateCurrent(any());
		}

		@Test
		@DisplayName("anonymous → 401 (filter-level, before @PreAuthorize)")
		void anonymousRejected() throws Exception {
			UpdateTenantRequest patch = new UpdateTenantRequest(
					"Valid Name", null, null, null, null, null, null);

			mockMvc.perform(patch(BASE + "/me")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(patch)))
					.andExpect(status().isUnauthorized());
		}

		@Test
		@DisplayName("invalid branding (bad hex) → 400 with field=branding.primaryColor")
		void invalidBrandingRejected() throws Exception {
			UpdateTenantRequest patch = new UpdateTenantRequest(
					null, null,
					new BrandingDto("not-a-color", null, null, null),
					null, null, null, null);

			mockMvc.perform(patch(BASE + "/me")
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(patch)))
					.andExpect(status().isBadRequest());

			then(tenantService).should(never()).updateCurrent(any());
		}
	}

	// ===========================================================================
	// POST /register  (public)
	// ===========================================================================

	@Nested
	@DisplayName("POST /v1/tenants/register")
	class RegisterTenant {

		private RegisterTenantRequest validBody() {
			return new RegisterTenantRequest(
					"Acme Corp",
					"acme-co",
					"founder@acme.test",
					"Sup3rSecret!",
					"Founder",
					"Doe");
		}

		@Test
		@DisplayName("happy path — 201 with raw AuthResponse (no envelope, OAuth-shape)")
		void happyPath() throws Exception {
			AuthResponse session = new AuthResponse(
					"access.token", "refresh.token", "Bearer", 900L,
					new UserSummary(UUID.randomUUID(), "Founder Doe",
							"founder@acme.test", null, UserStatus.ACTIVE));
			given(tenantService.register(any(RegisterTenantRequest.class))).willReturn(session);

			mockMvc.perform(post(BASE + "/register")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(validBody())))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.accessToken").value("access.token"))
					.andExpect(jsonPath("$.refreshToken").value("refresh.token"))
					.andExpect(jsonPath("$.user.email").value("founder@acme.test"));
		}

		@Test
		@DisplayName("slug already taken → 409 TENANT_SLUG_TAKEN")
		void slugTakenSurfacesAs409() throws Exception {
			given(tenantService.register(any(RegisterTenantRequest.class)))
					.willThrow(new ConflictException("TENANT_SLUG_TAKEN", "slug 'acme-co' is taken"));

			mockMvc.perform(post(BASE + "/register")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(validBody())))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code").value("TENANT_SLUG_TAKEN"));
		}

		@Test
		@DisplayName("malformed slug (uppercase) → 400 with validation error, never reaches service")
		void malformedSlugRejected() throws Exception {
			RegisterTenantRequest body = new RegisterTenantRequest(
					"Acme Corp", "BAD slug!!!", "founder@acme.test",
					"Sup3rSecret!", "Founder", "Doe");

			mockMvc.perform(post(BASE + "/register")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(body)))
					.andExpect(status().isBadRequest());

			then(tenantService).should(never()).register(any());
		}

		@Test
		@DisplayName("password shorter than 8 chars → 400, never reaches service")
		void shortPasswordRejected() throws Exception {
			RegisterTenantRequest body = new RegisterTenantRequest(
					"Acme Corp", "acme-co", "founder@acme.test",
					"short", "Founder", "Doe");

			mockMvc.perform(post(BASE + "/register")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(body)))
					.andExpect(status().isBadRequest());

			then(tenantService).should(never()).register(any());
		}
	}

	// ===========================================================================
	// POST /me/activate  (TENANT_ADMIN, BE-2.6)
	// ===========================================================================

	@Nested
	@DisplayName("POST /v1/tenants/me/activate")
	class ActivateMe {

		@Test
		@DisplayName("TENANT_ADMIN → 200 with status=ACTIVE in the response payload")
		void happyPath() throws Exception {
			given(tenantService.activateCurrent()).willReturn(stubResponse(TenantStatus.ACTIVE));

			mockMvc.perform(post(BASE + "/me/activate")
							.with(csrf())
							.with(authentication(adminAuth())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success").value(true))
					.andExpect(jsonPath("$.data.status").value("ACTIVE"));
		}

		@Test
		@DisplayName("TENANT_ADMIN on SUSPENDED tenant → 409 TENANT_NOT_ACTIVATABLE")
		void notActivatableSurfacesAs409() throws Exception {
			given(tenantService.activateCurrent())
					.willThrow(new ConflictException("TENANT_NOT_ACTIVATABLE",
							"Tenant cannot be activated from status SUSPENDED"));

			mockMvc.perform(post(BASE + "/me/activate")
							.with(csrf())
							.with(authentication(adminAuth())))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code").value("TENANT_NOT_ACTIVATABLE"));
		}

		@Test
		@DisplayName("authenticated but missing role → 403 FORBIDDEN, service never invoked")
		void forbiddenWithoutRole() throws Exception {
			mockMvc.perform(post(BASE + "/me/activate")
							.with(csrf())
							.with(authentication(userAuth())))
					.andExpect(status().isForbidden());

			then(tenantService).should(never()).activateCurrent();
		}

		@Test
		@DisplayName("anonymous → 401 UNAUTHORIZED")
		void anonymousRejected() throws Exception {
			mockMvc.perform(post(BASE + "/me/activate").with(csrf()))
					.andExpect(status().isUnauthorized());

			then(tenantService).should(never()).activateCurrent();
		}
	}

}
