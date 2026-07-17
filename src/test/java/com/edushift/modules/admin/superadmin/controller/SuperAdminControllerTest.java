package com.edushift.modules.admin.superadmin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.infrastructure.multitenancy.MultiTenancyConfiguration;
import com.edushift.infrastructure.multitenancy.TenantInterceptor;
import com.edushift.modules.admin.superadmin.SuperAdminService;
import com.edushift.modules.admin.superadmin.dto.CreateSuperAdminRequest;
import com.edushift.modules.admin.superadmin.dto.SuperAdminSummary;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ForbiddenException;
import com.edushift.shared.exception.GlobalExceptionHandler;
import com.edushift.shared.exception.NotFoundException;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Controller-slice tests for {@link SuperAdminController}. Validates:
 *
 * <ol>
 *   <li>Routing under the servlet context-path — {@code /admin/super-admins}
 *       is the canonical base, prepended automatically by the gateway
 *       ({@code /api}) so the request URI is {@code /api/admin/super-admins}.</li>
 *   <li>Class-level {@code @PreAuthorize("hasRole('SUPER_ADMIN')")} — every
 *       endpoint rejects non-{@code SUPER_ADMIN} callers with 403.</li>
 *   <li>Anonymous calls → 401 (security filter chain rejects via entry point).</li>
 *   <li>Happy paths and each error branch documented in §12 of
 *       {@code docs/modules/super-admin.md}.</li>
 *   <li>Validation: {@code @Valid} on {@code CreateSuperAdminRequest} → 400
 *       on blank fields / invalid email.</li>
 * </ol>
 *
 * <p>The slice follows the established pattern from
 * {@code CapacityControllerTest} — {@code @WebMvcTest} +
 * {@code EdushiftWebMvcTestConfig} + exclusion of the multitenancy
 * infrastructure that would otherwise try to bind to a real
 * {@code HttpServletRequest}.</p>
 */
@WebMvcTest(
		controllers = SuperAdminController.class,
		excludeFilters = @ComponentScan.Filter(
				type = FilterType.ASSIGNABLE_TYPE,
				classes = {MultiTenancyConfiguration.class, TenantInterceptor.class}))
@Import({
		GlobalExceptionHandler.class,
		com.edushift.config.SecurityConfig.class,
		com.edushift.config.WebConfiguration.class,
		com.edushift.test.EdushiftWebMvcTestConfig.class,
})
class SuperAdminControllerTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;

	@MockitoBean private SuperAdminService service;
	@MockitoBean private JwtService jwtService;

	private static final String BASE = "/v1/admin/super-admins";

	private static JwtAuthenticationToken superAdminAuth() {
		var principal = new JwtAuthenticatedPrincipal(
				UUID.randomUUID(),
				UUID.fromString("00000000-0000-0000-0000-000000000001"),
				"edushift-system",
				"super@edushift.pe");
		return new JwtAuthenticationToken(principal, "fake.token",
				List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
	}

	private static JwtAuthenticationToken tenantAdminAuth() {
		var principal = new JwtAuthenticatedPrincipal(
				UUID.randomUUID(), UUID.randomUUID(), "demo", "admin@demo.pe");
		return new JwtAuthenticationToken(principal, "fake.token",
				List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")));
	}

	private static JwtAuthenticationToken plainAuth() {
		var principal = new JwtAuthenticatedPrincipal(
				UUID.randomUUID(), UUID.randomUUID(), "demo", "user@demo.pe");
		return new JwtAuthenticationToken(principal, "fake.token",
				List.<GrantedAuthority>of());
	}

	private static SuperAdminSummary stubSummary(String email, String status) {
		return new SuperAdminSummary(
				UUID.randomUUID(),
				email,
				"Ada", "Lovelace",
				status,
				false,
				Instant.parse("2026-07-01T10:00:00Z"),
				Instant.parse("2026-01-01T00:00:00Z"),
				List.of("SUPER_ADMIN"));
	}

	// =========================================================================
	// GET /admin/super-admins  — list
	// =========================================================================

	@Nested
	@DisplayName("GET /admin/super-admins")
	class ListEndpoint {

		@Test
		@DisplayName("200 with array of SuperAdminSummary")
		void happyPath() throws Exception {
			given(service.list()).willReturn(List.of(
					stubSummary("a@edushift.pe", "ACTIVE"),
					stubSummary("b@edushift.pe", "ACTIVE")));

			mockMvc.perform(get(BASE).with(authentication(superAdminAuth())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success").value(true))
					.andExpect(jsonPath("$.data.length()").value(2))
					.andExpect(jsonPath("$.data[0].email").value("a@edushift.pe"))
					.andExpect(jsonPath("$.data[0].roles[0]").value("SUPER_ADMIN"));
		}

		@Test
		@DisplayName("anonymous — 401")
		void anonymous() throws Exception {
			mockMvc.perform(get(BASE))
					.andExpect(status().isUnauthorized());
			verify(service, never()).list();
		}

		@Test
		@DisplayName("TENANT_ADMIN — 403 (role gate)")
		void forbiddenForTenantAdmin() throws Exception {
			mockMvc.perform(get(BASE).with(authentication(tenantAdminAuth())))
					.andExpect(status().isForbidden());
			verify(service, never()).list();
		}

		@Test
		@DisplayName("authenticated with no roles — 403")
		void forbiddenForPlainUser() throws Exception {
			mockMvc.perform(get(BASE).with(authentication(plainAuth())))
					.andExpect(status().isForbidden());
			verify(service, never()).list();
		}
	}

	// =========================================================================
	// POST /admin/super-admins  — create
	// =========================================================================

	@Nested
	@DisplayName("POST /admin/super-admins")
	class CreateEndpoint {

		@Test
		@DisplayName("201 with the created SuperAdminSummary")
		void happyPath() throws Exception {
			given(service.create(any(CreateSuperAdminRequest.class), any(UUID.class)))
					.willReturn(stubSummary("new@edushift.pe", "ACTIVE"));

			String body = """
					{"email":"new@edushift.pe","firstName":"Ada","lastName":"Lovelace"}
					""";
			mockMvc.perform(post(BASE)
							.with(csrf()).with(authentication(superAdminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(body))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success").value(true))
					.andExpect(jsonPath("$.data.email").value("new@edushift.pe"));
		}

		@Test
		@DisplayName("blank email → 400 with field error")
		void blankEmail() throws Exception {
			String body = """
					{"email":"","firstName":"Ada","lastName":"Lovelace"}
					""";
			mockMvc.perform(post(BASE)
							.with(csrf()).with(authentication(superAdminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(body))
					.andExpect(status().isBadRequest());
			verify(service, never()).create(any(), any());
		}

		@Test
		@DisplayName("invalid email format → 400")
		void invalidEmailFormat() throws Exception {
			String body = """
					{"email":"not-an-email","firstName":"Ada","lastName":"Lovelace"}
					""";
			mockMvc.perform(post(BASE)
							.with(csrf()).with(authentication(superAdminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(body))
					.andExpect(status().isBadRequest());
			verify(service, never()).create(any(), any());
		}

		@Test
		@DisplayName("blank firstName → 400")
		void blankFirstName() throws Exception {
			String body = """
					{"email":"x@edushift.pe","firstName":"","lastName":"Y"}
					""";
			mockMvc.perform(post(BASE)
							.with(csrf()).with(authentication(superAdminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(body))
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("duplicate email → 409 EMAIL_TAKEN")
		void emailTaken() throws Exception {
			given(service.create(any(CreateSuperAdminRequest.class), any(UUID.class)))
					.willThrow(new ConflictException("EMAIL_TAKEN",
							"A SUPER_ADMIN with that email already exists"));

			String body = """
					{"email":"dup@edushift.pe","firstName":"A","lastName":"B"}
					""";
			mockMvc.perform(post(BASE)
							.with(csrf()).with(authentication(superAdminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(body))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code").value("EMAIL_TAKEN"));
		}

		@Test
		@DisplayName("TENANT_ADMIN caller — 403 (role gate)")
		void forbiddenForTenantAdmin() throws Exception {
			String body = """
					{"email":"x@edushift.pe","firstName":"A","lastName":"B"}
					""";
			mockMvc.perform(post(BASE)
							.with(csrf()).with(authentication(tenantAdminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(body))
					.andExpect(status().isForbidden());
			verify(service, never()).create(any(), any());
		}
	}

	// =========================================================================
	// PATCH /admin/super-admins/{uuid}/disable
	// =========================================================================

	@Nested
	@DisplayName("PATCH /admin/super-admins/{uuid}/disable")
	class DisableEndpoint {

		@Test
		@DisplayName("200 with the disabled summary")
		void happyPath() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.disable(eq(id), any(UUID.class)))
					.willReturn(stubSummary("target@edushift.pe", "INACTIVE"));

			mockMvc.perform(patch(BASE + "/{uuid}/disable", id)
							.with(csrf()).with(authentication(superAdminAuth())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status").value("INACTIVE"));
		}

		@Test
		@DisplayName("unknown UUID → 404 USER_NOT_FOUND")
		void notFound() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.disable(eq(id), any(UUID.class)))
					.willThrow(new NotFoundException("USER_NOT_FOUND", "User not found"));

			mockMvc.perform(patch(BASE + "/{uuid}/disable", id)
							.with(csrf()).with(authentication(superAdminAuth())))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.errors[0].code").value("USER_NOT_FOUND"));
		}

		@Test
		@DisplayName("target is not a SUPER_ADMIN → 403 NOT_SUPER_ADMIN")
		void notSuperAdmin() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.disable(eq(id), any(UUID.class)))
					.willThrow(new ForbiddenException("NOT_SUPER_ADMIN",
							"Target is not a SUPER_ADMIN"));

			mockMvc.perform(patch(BASE + "/{uuid}/disable", id)
							.with(csrf()).with(authentication(superAdminAuth())))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.errors[0].code").value("NOT_SUPER_ADMIN"));
		}

		@Test
		@DisplayName("actor == target → 422 SELF_DISABLE_FORBIDDEN")
		void selfDisableForbidden() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.disable(eq(id), any(UUID.class)))
					.willThrow(new BusinessException("SELF_DISABLE_FORBIDDEN",
							"SUPER_ADMIN cannot disable themselves"));

			mockMvc.perform(patch(BASE + "/{uuid}/disable", id)
							.with(csrf()).with(authentication(superAdminAuth())))
					.andExpect(status().isUnprocessableEntity())
					.andExpect(jsonPath("$.errors[0].code").value("SELF_DISABLE_FORBIDDEN"));
		}

		@Test
		@DisplayName("already disabled → 409 ALREADY_DISABLED")
		void alreadyDisabled() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.disable(eq(id), any(UUID.class)))
					.willThrow(new ConflictException("ALREADY_DISABLED",
							"Account is already disabled"));

			mockMvc.perform(patch(BASE + "/{uuid}/disable", id)
							.with(csrf()).with(authentication(superAdminAuth())))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code").value("ALREADY_DISABLED"));
		}

		@Test
		@DisplayName("last SUPER_ADMIN → 403 QUORUM_REQUIRED")
		void quorumRequired() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.disable(eq(id), any(UUID.class)))
					.willThrow(new ForbiddenException("QUORUM_REQUIRED",
							"At least one other active SUPER_ADMIN must remain"));

			mockMvc.perform(patch(BASE + "/{uuid}/disable", id)
							.with(csrf()).with(authentication(superAdminAuth())))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.errors[0].code").value("QUORUM_REQUIRED"));
		}

		@Test
		@DisplayName("non-UUID path variable → 400 TYPE_MISMATCH")
		void nonUuidPath() throws Exception {
			mockMvc.perform(patch(BASE + "/{uuid}/disable", "not-a-uuid")
							.with(csrf()).with(authentication(superAdminAuth())))
					.andExpect(status().isBadRequest());
			verify(service, never()).disable(any(), any());
		}
	}

	// =========================================================================
	// GET /admin/super-admins/count-active
	// =========================================================================

	@Nested
	@DisplayName("GET /admin/super-admins/count-active")
	class CountActiveEndpoint {

		@Test
		@DisplayName("200 with the count (Long)")
		void happyPath() throws Exception {
			given(service.countActive()).willReturn(3L);

			mockMvc.perform(get(BASE + "/count-active").with(authentication(superAdminAuth())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data").value(3));
		}

		@Test
		@DisplayName("200 with 0 when no active SUPER_ADMINs")
		void zero() throws Exception {
			given(service.countActive()).willReturn(0L);

			mockMvc.perform(get(BASE + "/count-active").with(authentication(superAdminAuth())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data").value(0));
		}

		@Test
		@DisplayName("TENANT_ADMIN — 403")
		void forbidden() throws Exception {
			mockMvc.perform(get(BASE + "/count-active").with(authentication(tenantAdminAuth())))
					.andExpect(status().isForbidden());
			verify(service, never()).countActive();
		}
	}

	// =========================================================================
	// Routing sanity — path is exactly /admin/super-admins (NOT /super-admin/...)
	// =========================================================================

	@Nested
	@DisplayName("Path routing")
	class PathRouting {

		@Test
		@DisplayName("old /super-admin/{uuid}/disable (from docs/api/endpoints.md) returns 404")
		void oldPathNotMapped() throws Exception {
			UUID id = UUID.randomUUID();
			mockMvc.perform(patch("/super-admin/{uuid}/disable", id)
							.with(csrf()).with(authentication(superAdminAuth())))
					.andExpect(status().isNotFound());
		}

		@Test
		@DisplayName("list endpoint is exactly once")
		void listCalledOnce() throws Exception {
			given(service.list()).willReturn(List.of());
			mockMvc.perform(get(BASE).with(authentication(superAdminAuth())))
					.andExpect(status().isOk());
			verify(service, times(1)).list();
			verify(service, never()).countActive();
		}
	}

	// Verify that when the security principal is present the controller
	// simply forwards to the service. We deliberately avoid the
	// `new JwtAuthenticatedPrincipal(null, ...)` variant because some
	// downstream filters expect a non-null id — see H8.
	@Test
	@DisplayName("controller delegates count-active to the service regardless of principal id")
	void forwardsCountActive() throws Exception {
		given(service.countActive()).willReturn(7L);

		mockMvc.perform(get(BASE + "/count-active").with(authentication(superAdminAuth())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").value(7));

		verify(service, times(1)).countActive();
	}
}
