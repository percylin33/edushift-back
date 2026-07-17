package com.edushift.modules.users.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.edushift.modules.users.dto.AcceptInvitationRequest;
import com.edushift.modules.users.dto.CreateInvitationRequest;
import com.edushift.modules.users.dto.InvitationPreflightResponse;
import com.edushift.modules.users.dto.InvitationResponse;
import com.edushift.modules.users.entity.InvitationStatus;
import com.edushift.modules.users.service.UserInvitationService;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.GlobalExceptionHandler;
import com.edushift.shared.exception.GoneException;
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

@WebMvcTest(
		controllers = UserInvitationController.class,
		excludeFilters = @ComponentScan.Filter(
				type = FilterType.ASSIGNABLE_TYPE,
				classes = {MultiTenancyConfiguration.class, TenantInterceptor.class}))
@Import({
		GlobalExceptionHandler.class,
		com.edushift.config.SecurityConfig.class,
		com.edushift.config.WebConfiguration.class,
		com.edushift.test.EdushiftWebMvcTestConfig.class,
})
class UserInvitationControllerTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;

	@MockitoBean private UserInvitationService service;
	@MockitoBean private JwtService jwtService;
	@MockitoBean private com.edushift.shared.security.LmsRoleAuthorityMapper roleAuthorityMapper;

private static final String BASE = "/v1/users/invitations";

	private String json(Object value) throws Exception {
		return objectMapper.writeValueAsString(value);
	}

	private static JwtAuthenticationToken adminAuth() {
		JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
				UUID.randomUUID(), UUID.randomUUID(),
				"acme", "admin@acme.test");
		return new JwtAuthenticationToken(
				principal, "fake.token",
				List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")));
	}

	private static JwtAuthenticationToken plainAuth() {
		JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
				UUID.randomUUID(), UUID.randomUUID(),
				"acme", "user@acme.test");
		return new JwtAuthenticationToken(principal, "fake.token", List.<GrantedAuthority>of());
	}

	private InvitationResponse stubInvitation(InvitationStatus status, String token) {
		return new InvitationResponse(
				UUID.randomUUID(),
				"teach@acme.test",
				"Teach",
				"Doe",
				Set.of("TEACHER"),
				status,
				token,
				Instant.parse("2026-06-11T00:00:00Z"),
				null, null,
				Instant.parse("2026-06-04T12:00:00Z"));
	}

	// ===========================================================================
	// POST / (admin)
	// ===========================================================================

	@Nested
	@DisplayName("POST /v1/users/invitations")
	class Create {

		@Test
		@DisplayName("TENANT_ADMIN → 201 with token in the envelope")
		void happyPath() throws Exception {
			given(service.createInvitation(any(CreateInvitationRequest.class)))
					.willReturn(stubInvitation(InvitationStatus.PENDING, "secret-token-1"));

			CreateInvitationRequest body = new CreateInvitationRequest(
					"teach@acme.test", "Teach", "Doe", Set.of("TEACHER"));

			mockMvc.perform(post(BASE)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(body)))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.success").value(true))
					.andExpect(jsonPath("$.data.token").value("secret-token-1"))
					.andExpect(jsonPath("$.data.status").value("PENDING"));
		}

		@Test
		@DisplayName("duplicate pending → 409 INVITATION_ALREADY_PENDING")
		void duplicatePendingSurfacesAs409() throws Exception {
			given(service.createInvitation(any()))
					.willThrow(new ConflictException("INVITATION_ALREADY_PENDING",
							"already pending"));

			CreateInvitationRequest body = new CreateInvitationRequest(
					"dup@acme.test", "Dup", "User", Set.of("TEACHER"));

			mockMvc.perform(post(BASE)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(body)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code").value("INVITATION_ALREADY_PENDING"));
		}

		@Test
		@DisplayName("invalid email → 400 (validation, never reaches service)")
		void invalidEmailRejected() throws Exception {
			CreateInvitationRequest body = new CreateInvitationRequest(
					"not-an-email", "Teach", "Doe", Set.of("TEACHER"));

			mockMvc.perform(post(BASE)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(body)))
					.andExpect(status().isBadRequest());

			then(service).should(never()).createInvitation(any());
		}

		@Test
		@DisplayName("missing role → 403 FORBIDDEN")
		void forbiddenWithoutRole() throws Exception {
			CreateInvitationRequest body = new CreateInvitationRequest(
					"teach@acme.test", "Teach", "Doe", Set.of("TEACHER"));

			mockMvc.perform(post(BASE)
							.with(csrf())
							.with(authentication(plainAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(body)))
					.andExpect(status().isForbidden());
		}
	}

	// ===========================================================================
	// GET / (admin)
	// ===========================================================================

	@Nested
	@DisplayName("GET /v1/users/invitations")
	class ListPending {

		@Test
		@DisplayName("TENANT_ADMIN → 200 with a page of token-stripped invitations")
		void happyPath() throws Exception {
			Page<InvitationResponse> page = new PageImpl<>(List.of(
					stubInvitation(InvitationStatus.PENDING, null),
					stubInvitation(InvitationStatus.PENDING, null)));
			given(service.listPendingInvitations(any(Pageable.class))).willReturn(page);

			mockMvc.perform(get(BASE).with(authentication(adminAuth())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.content[0].status").value("PENDING"))
					.andExpect(jsonPath("$.content[0].token").doesNotExist());
		}

		@Test
		@DisplayName("anonymous → 401")
		void anonymousRejected() throws Exception {
			mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
		}
	}

	// ===========================================================================
	// DELETE /{publicUuid} (admin)
	// ===========================================================================

	@Nested
	@DisplayName("DELETE /v1/users/invitations/{publicUuid}")
	class Cancel {

		@Test
		@DisplayName("happy path — 200 with status=CANCELLED")
		void happyPath() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.cancelInvitation(id))
					.willReturn(stubInvitation(InvitationStatus.CANCELLED, null));

			mockMvc.perform(delete(BASE + "/" + id)
							.with(csrf())
							.with(authentication(adminAuth())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status").value("CANCELLED"));
		}

		@Test
		@DisplayName("already accepted → 409 INVITATION_ALREADY_ACCEPTED")
		void alreadyAcceptedSurfacesAs409() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.cancelInvitation(id))
					.willThrow(new ConflictException("INVITATION_ALREADY_ACCEPTED",
							"already accepted"));

			mockMvc.perform(delete(BASE + "/" + id)
							.with(csrf())
							.with(authentication(adminAuth())))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code").value("INVITATION_ALREADY_ACCEPTED"));
		}
	}

	// ===========================================================================
	// GET /by-token/{token} (public)
	// ===========================================================================

	@Nested
	@DisplayName("GET /v1/users/invitations/by-token/{token}")
	class Preflight {

		@Test
		@DisplayName("public + valid token → 200 with preflight payload (no auth required)")
		void happyPath() throws Exception {
			String token = "valid-token-1234567890abcdef";
			given(service.getPreflight(token))
					.willReturn(new InvitationPreflightResponse(
							"teach@acme.test", "Teach", "Doe", "Acme Corp"));

			mockMvc.perform(get(BASE + "/by-token/" + token))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.email").value("teach@acme.test"))
					.andExpect(jsonPath("$.data.tenantName").value("Acme Corp"));
		}

		@Test
		@DisplayName("unknown token → 404 RESOURCE_NOT_FOUND")
		void unknownTokenSurfacesAs404() throws Exception {
			String token = "missing-token-1234567890abcdef";
			given(service.getPreflight(token))
					.willThrow(new ResourceNotFoundException("Invitation", "<by token>"));

			mockMvc.perform(get(BASE + "/by-token/" + token))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_FOUND"));
		}

		@Test
		@DisplayName("expired token → 410 INVITATION_EXPIRED")
		void expiredTokenSurfacesAs410() throws Exception {
			String token = "expired-token-1234567890abcdef";
			given(service.getPreflight(token))
					.willThrow(new GoneException("INVITATION_EXPIRED", "expired"));

			mockMvc.perform(get(BASE + "/by-token/" + token))
					.andExpect(status().isGone())
					.andExpect(jsonPath("$.errors[0].code").value("INVITATION_EXPIRED"));
		}

		@Test
		@DisplayName("token shorter than 16 chars → 400 (validation, service never invoked)")
		void shortTokenRejected() throws Exception {
			mockMvc.perform(get(BASE + "/by-token/short"))
					.andExpect(status().isBadRequest());

			then(service).should(never()).getPreflight(any());
		}
	}

	// ===========================================================================
	// POST /accept (public)
	// ===========================================================================

	@Nested
	@DisplayName("POST /v1/users/invitations/accept")
	class Accept {

		@Test
		@DisplayName("public + valid token + valid password → 201 with raw AuthResponse")
		void happyPath() throws Exception {
			AuthResponse session = new AuthResponse(
					"access.token", "refresh.token", "Bearer", 900L,
					new UserSummary(UUID.randomUUID(), "Teach Doe",
							"teach@acme.test", null, UserStatus.ACTIVE));
			given(service.acceptInvitation(any(AcceptInvitationRequest.class))).willReturn(session);

			AcceptInvitationRequest body = new AcceptInvitationRequest(
					"valid-token-1234567890abcdef", "Sup3rSecret!");

			mockMvc.perform(post(BASE + "/accept")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(body)))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.accessToken").value("access.token"))
					.andExpect(jsonPath("$.user.email").value("teach@acme.test"));
		}

		@Test
		@DisplayName("expired token → 410 INVITATION_EXPIRED")
		void expiredTokenSurfacesAs410() throws Exception {
			given(service.acceptInvitation(any()))
					.willThrow(new GoneException("INVITATION_EXPIRED", "expired"));

			AcceptInvitationRequest body = new AcceptInvitationRequest(
					"expired-token-1234567890abcdef", "Sup3rSecret!");

			mockMvc.perform(post(BASE + "/accept")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(body)))
					.andExpect(status().isGone())
					.andExpect(jsonPath("$.errors[0].code").value("INVITATION_EXPIRED"));
		}

		@Test
		@DisplayName("password too short → 400 validation, service never invoked")
		void shortPasswordRejected() throws Exception {
			AcceptInvitationRequest body = new AcceptInvitationRequest(
					"valid-token-1234567890abcdef", "short");

			mockMvc.perform(post(BASE + "/accept")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(body)))
					.andExpect(status().isBadRequest());

			then(service).should(never()).acceptInvitation(any());
		}
	}
}
