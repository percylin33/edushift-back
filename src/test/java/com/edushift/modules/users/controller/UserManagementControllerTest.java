package com.edushift.modules.users.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.users.dto.AssignRolesRequest;
import com.edushift.modules.users.dto.UpdateUserRequest;
import com.edushift.modules.users.dto.UserDetailResponse;
import com.edushift.modules.users.dto.UserListItem;
import com.edushift.modules.users.service.UserManagementService;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.GlobalExceptionHandler;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Controller-slice tests for {@link UserManagementController}.
 *
 * <p>Same shape as {@code TenantControllerTest}: {@link WebMvcTest},
 * {@link UserManagementService} mocked, security simulated via
 * {@link org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors#authentication}.
 *
 * <p>The role gate {@code @PreAuthorize("hasRole('TENANT_ADMIN')")}
 * applies to every endpoint, so we test it once per HTTP verb to keep
 * the suite short while still covering the gate end-to-end.
 */
@WebMvcTest(
		controllers = UserManagementController.class,
		excludeFilters = @ComponentScan.Filter(
				type = FilterType.ASSIGNABLE_TYPE,
				classes = {MultiTenancyConfiguration.class, TenantInterceptor.class}))
@Import({
		GlobalExceptionHandler.class,
		com.edushift.config.SecurityConfig.class,
		com.edushift.config.WebConfiguration.class
})
class UserManagementControllerTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;

	@MockitoBean private UserManagementService service;
	@MockitoBean private JwtService jwtService;

	private static final String BASE = "/v1/users";

	private String json(Object value) throws Exception {
		return objectMapper.writeValueAsString(value);
	}

	private static JwtAuthenticationToken adminAuth() {
		return authWith(List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")));
	}

	private static JwtAuthenticationToken plainAuth() {
		return authWith(List.<GrantedAuthority>of());
	}

	private static JwtAuthenticationToken authWith(List<GrantedAuthority> authorities) {
		JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
				UUID.randomUUID(),
				UUID.randomUUID(),
				"acme",
				"admin@acme.test");
		return new JwtAuthenticationToken(principal, "fake.token", authorities);
	}

	private UserDetailResponse stubDetail(UUID publicUuid, UserStatus status, Set<String> roles) {
		return new UserDetailResponse(
				publicUuid,
				"ada@acme.test",
				"Ada",
				"Lovelace",
				"Ada Lovelace",
				null,
				null,
				status,
				true,
				false,
				roles,
				Instant.parse("2026-01-01T00:00:00Z"),
				Instant.parse("2025-01-01T00:00:00Z"),
				Instant.parse("2026-01-01T00:00:00Z"));
	}

	private UserListItem stubListItem(String email) {
		return new UserListItem(
				UUID.randomUUID(),
				email,
				"Ada",
				"Lovelace",
				"Ada Lovelace",
				UserStatus.ACTIVE,
				Set.of("TEACHER"),
				Instant.parse("2026-01-01T00:00:00Z"),
				Instant.parse("2025-01-01T00:00:00Z"));
	}

	// ===========================================================================
	// GET / (list)
	// ===========================================================================

	@Nested
	@DisplayName("GET /v1/users — list")
	class ListUsers {

		@Test
		@DisplayName("TENANT_ADMIN → 200 with a page of UserListItem")
		void happyPath() throws Exception {
			Page<UserListItem> page = new PageImpl<>(List.of(
					stubListItem("a@acme.test"), stubListItem("b@acme.test")));
			given(service.listUsers(any(), any(Pageable.class))).willReturn(page);

			mockMvc.perform(get(BASE).with(authentication(adminAuth())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.content[0].email").value("a@acme.test"))
					.andExpect(jsonPath("$.content[1].email").value("b@acme.test"));
		}

		@Test
		@DisplayName("authenticated but missing role → 403")
		void forbiddenWithoutRole() throws Exception {
			mockMvc.perform(get(BASE).with(authentication(plainAuth())))
					.andExpect(status().isForbidden());

			then(service).should(never()).listUsers(any(), any());
		}

		@Test
		@DisplayName("anonymous → 401")
		void anonymousRejected() throws Exception {
			mockMvc.perform(get(BASE))
					.andExpect(status().isUnauthorized());
		}
	}

	// ===========================================================================
	// GET /{publicUuid}
	// ===========================================================================

	@Nested
	@DisplayName("GET /v1/users/{publicUuid}")
	class GetOne {

		@Test
		@DisplayName("happy path — 200 with envelope.data populated")
		void happyPath() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.getUser(id)).willReturn(
					stubDetail(id, UserStatus.ACTIVE, Set.of("TEACHER")));

			mockMvc.perform(get(BASE + "/" + id).with(authentication(adminAuth())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success").value(true))
					.andExpect(jsonPath("$.data.email").value("ada@acme.test"))
					.andExpect(jsonPath("$.data.fullName").value("Ada Lovelace"))
					.andExpect(jsonPath("$.data.roles[0]").value("TEACHER"));
		}

		@Test
		@DisplayName("unknown publicUuid → 404 RESOURCE_NOT_FOUND")
		void notFound() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.getUser(id)).willThrow(new ResourceNotFoundException("User", id));

			mockMvc.perform(get(BASE + "/" + id).with(authentication(adminAuth())))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_FOUND"));
		}
	}

	// ===========================================================================
	// PATCH /{publicUuid}
	// ===========================================================================

	@Nested
	@DisplayName("PATCH /v1/users/{publicUuid}")
	class UpdateProfile {

		@Test
		@DisplayName("TENANT_ADMIN → 200 with merged response")
		void happyPath() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.updateUser(eq(id), any(UpdateUserRequest.class)))
					.willReturn(stubDetail(id, UserStatus.ACTIVE, Set.of("TEACHER")));

			UpdateUserRequest patch = new UpdateUserRequest("Augusta", null, null, null);

			mockMvc.perform(patch(BASE + "/" + id)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(patch)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success").value(true));
		}

		@Test
		@DisplayName("invalid phone format → 400 validation, service never invoked")
		void invalidPhoneRejected() throws Exception {
			UUID id = UUID.randomUUID();
			UpdateUserRequest patch = new UpdateUserRequest(null, null, "not a phone!", null);

			mockMvc.perform(patch(BASE + "/" + id)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(patch)))
					.andExpect(status().isBadRequest());

			then(service).should(never()).updateUser(any(), any());
		}

		@Test
		@DisplayName("missing role → 403 FORBIDDEN")
		void forbiddenWithoutRole() throws Exception {
			UUID id = UUID.randomUUID();
			UpdateUserRequest patch = new UpdateUserRequest("Augusta", null, null, null);

			mockMvc.perform(patch(BASE + "/" + id)
							.with(csrf())
							.with(authentication(plainAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(patch)))
					.andExpect(status().isForbidden());
		}
	}

	// ===========================================================================
	// POST /{publicUuid}/roles
	// ===========================================================================

	@Nested
	@DisplayName("POST /v1/users/{publicUuid}/roles")
	class AssignRoles {

		@Test
		@DisplayName("TENANT_ADMIN → 200 with the new role set in the response")
		void happyPath() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.assignRoles(eq(id), any(AssignRolesRequest.class)))
					.willReturn(stubDetail(id, UserStatus.ACTIVE, Set.of("TEACHER", "STAFF")));

			AssignRolesRequest body = new AssignRolesRequest(Set.of("TEACHER", "STAFF"));

			mockMvc.perform(post(BASE + "/" + id + "/roles")
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(body)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.roles", org.hamcrest.Matchers.hasItems("TEACHER", "STAFF")));
		}

		@Test
		@DisplayName("unknown role → 422 INVALID_ROLE")
		void invalidRoleSurfacesAs422() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.assignRoles(eq(id), any(AssignRolesRequest.class)))
					.willThrow(new BusinessException("INVALID_ROLE", "Unknown role: 'WIZARD'"));

			AssignRolesRequest body = new AssignRolesRequest(Set.of("WIZARD"));

			mockMvc.perform(post(BASE + "/" + id + "/roles")
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(body)))
					.andExpect(status().isUnprocessableEntity())
					.andExpect(jsonPath("$.errors[0].code").value("INVALID_ROLE"));
		}

		@Test
		@DisplayName("removing TENANT_ADMIN from the last admin → 409 LAST_ADMIN_PROTECTION")
		void lastAdminProtectionSurfacesAs409() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.assignRoles(eq(id), any(AssignRolesRequest.class)))
					.willThrow(new ConflictException("LAST_ADMIN_PROTECTION",
							"Cannot remove TENANT_ADMIN role from the last admin"));

			AssignRolesRequest body = new AssignRolesRequest(Set.of("TEACHER"));

			mockMvc.perform(post(BASE + "/" + id + "/roles")
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(body)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code").value("LAST_ADMIN_PROTECTION"));
		}

		@Test
		@DisplayName("empty roles array → 400 (validation, never reaches service)")
		void emptyRolesRejected() throws Exception {
			UUID id = UUID.randomUUID();
			AssignRolesRequest body = new AssignRolesRequest(Set.of());

			mockMvc.perform(post(BASE + "/" + id + "/roles")
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(body)))
					.andExpect(status().isBadRequest());

			then(service).should(never()).assignRoles(any(), any());
		}
	}

	// ===========================================================================
	// POST /{publicUuid}/disable
	// ===========================================================================

	@Nested
	@DisplayName("POST /v1/users/{publicUuid}/disable")
	class Disable {

		@Test
		@DisplayName("happy path — 200 with status=SUSPENDED")
		void happyPath() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.disableUser(id)).willReturn(
					stubDetail(id, UserStatus.SUSPENDED, Set.of("TEACHER")));

			mockMvc.perform(post(BASE + "/" + id + "/disable")
							.with(csrf())
							.with(authentication(adminAuth())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status").value("SUSPENDED"));
		}

		@Test
		@DisplayName("self-lockout → 422 SELF_LOCKOUT")
		void selfLockoutSurfacesAs422() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.disableUser(id))
					.willThrow(new BusinessException("SELF_LOCKOUT",
							"Admins cannot disable their own account"));

			mockMvc.perform(post(BASE + "/" + id + "/disable")
							.with(csrf())
							.with(authentication(adminAuth())))
					.andExpect(status().isUnprocessableEntity())
					.andExpect(jsonPath("$.errors[0].code").value("SELF_LOCKOUT"));
		}
	}

	// ===========================================================================
	// POST /{publicUuid}/enable
	// ===========================================================================

	@Nested
	@DisplayName("POST /v1/users/{publicUuid}/enable")
	class Enable {

		@Test
		@DisplayName("happy path — 200 with status=ACTIVE")
		void happyPath() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.enableUser(id)).willReturn(
					stubDetail(id, UserStatus.ACTIVE, Set.of("TEACHER")));

			mockMvc.perform(post(BASE + "/" + id + "/enable")
							.with(csrf())
							.with(authentication(adminAuth())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status").value("ACTIVE"));
		}

		@Test
		@DisplayName("PENDING_VERIFICATION user → 409 USER_NOT_ENABLEABLE")
		void notEnableableSurfacesAs409() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.enableUser(id))
					.willThrow(new ConflictException("USER_NOT_ENABLEABLE",
							"User cannot be enabled from status PENDING_VERIFICATION"));

			mockMvc.perform(post(BASE + "/" + id + "/enable")
							.with(csrf())
							.with(authentication(adminAuth())))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code").value("USER_NOT_ENABLEABLE"));
		}
	}

	// ===========================================================================
	// POST /{publicUuid}/reset-password
	// ===========================================================================

	@Nested
	@DisplayName("POST /v1/users/{publicUuid}/reset-password")
	class ResetPassword {

		@Test
		@DisplayName("happy path — 202 Accepted (email delivery is Sprint 9)")
		void happyPath() throws Exception {
			UUID id = UUID.randomUUID();

			mockMvc.perform(post(BASE + "/" + id + "/reset-password")
							.with(csrf())
							.with(authentication(adminAuth())))
					.andExpect(status().isAccepted());

			then(service).should().resetPassword(id);
		}

		@Test
		@DisplayName("missing role → 403 FORBIDDEN, service never invoked")
		void forbiddenWithoutRole() throws Exception {
			UUID id = UUID.randomUUID();

			mockMvc.perform(post(BASE + "/" + id + "/reset-password")
							.with(csrf())
							.with(authentication(plainAuth())))
					.andExpect(status().isForbidden());

			then(service).should(never()).resetPassword(any(UUID.class));
		}
	}
}
