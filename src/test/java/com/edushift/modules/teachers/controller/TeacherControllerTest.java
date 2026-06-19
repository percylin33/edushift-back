package com.edushift.modules.teachers.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.infrastructure.multitenancy.MultiTenancyConfiguration;
import com.edushift.infrastructure.multitenancy.TenantInterceptor;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.Gender;
import com.edushift.modules.teachers.dto.CreateTeacherRequest;
import com.edushift.modules.teachers.dto.InviteTeacherResponse;
import com.edushift.modules.teachers.dto.LinkTeacherUserRequest;
import com.edushift.modules.teachers.dto.TeacherListItem;
import com.edushift.modules.teachers.dto.TeacherResponse;
import com.edushift.modules.teachers.entity.EmploymentStatus;
import com.edushift.modules.teachers.service.TeacherService;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.GlobalExceptionHandler;
import com.edushift.shared.exception.ResourceNotFoundException;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
		controllers = TeacherController.class,
		excludeFilters = @ComponentScan.Filter(
				type = FilterType.ASSIGNABLE_TYPE,
				classes = {MultiTenancyConfiguration.class, TenantInterceptor.class}))
@Import({
		GlobalExceptionHandler.class,
		com.edushift.config.SecurityConfig.class,
		com.edushift.config.WebConfiguration.class
})
class TeacherControllerTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;

	@MockitoBean private TeacherService service;
	@MockitoBean private JwtService jwtService;
	@MockitoBean private com.edushift.shared.security.LmsRoleAuthorityMapper roleAuthorityMapper;

private static final String BASE = "/v1/teachers";

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

	private TeacherResponse stubResponse(UUID publicUuid) {
		return new TeacherResponse(
				publicUuid,
				DocumentType.DNI, "12345678",
				"Ada", "Lovelace", null, null,
				Gender.FEMALE,
				"ada@acme.test", null, "Mg.",
				List.of("Matematica"), null,
				EmploymentStatus.ACTIVE,
				null,
				new HashMap<>(),
				Instant.parse("2026-01-01T00:00:00Z"),
				Instant.parse("2026-06-01T00:00:00Z"));
	}

	private TeacherListItem stubListItem(String docNumber) {
		return new TeacherListItem(
				UUID.randomUUID(),
				DocumentType.DNI, docNumber,
				"Ada", "Lovelace", null,
				"ada@acme.test", "Mg.",
				List.of("Matematica"),
				EmploymentStatus.ACTIVE, false);
	}

	// =========================================================================
	// GET /
	// =========================================================================

	@Nested
	@DisplayName("GET /v1/teachers — list")
	class ListTeachers {

		@Test
		@DisplayName("TENANT_ADMIN → 200 with a page of items")
		void happyPath() throws Exception {
			given(service.listTeachers(any(), any(), any(), any(Pageable.class)))
					.willReturn(new PageImpl<>(List.of(
							stubListItem("11111111"), stubListItem("22222222"))));

			mockMvc.perform(get(BASE).with(authentication(adminAuth())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.content[0].documentNumber").value("11111111"));
		}

		@Test
		@DisplayName("anonymous → 401")
		void anonymous() throws Exception {
			mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
		}

		@Test
		@DisplayName("authenticated but no role → 403")
		void noRole() throws Exception {
			mockMvc.perform(get(BASE).with(authentication(plainAuth())))
					.andExpect(status().isForbidden());
		}
	}

	// =========================================================================
	// GET /{publicUuid}
	// =========================================================================

	@Nested
	@DisplayName("GET /v1/teachers/{publicUuid}")
	class GetOne {

		@Test
		@DisplayName("happy path — 200 with envelope")
		void happyPath() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.getTeacher(id)).willReturn(stubResponse(id));

			mockMvc.perform(get(BASE + "/" + id).with(authentication(adminAuth())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success").value(true))
					.andExpect(jsonPath("$.data.publicUuid").value(id.toString()));
		}

		@Test
		@DisplayName("unknown publicUuid → 404 RESOURCE_NOT_FOUND")
		void notFound() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.getTeacher(id))
					.willThrow(new ResourceNotFoundException("Teacher", id));

			mockMvc.perform(get(BASE + "/" + id).with(authentication(adminAuth())))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_FOUND"));
		}
	}

	// =========================================================================
	// POST /
	// =========================================================================

	@Nested
	@DisplayName("POST /v1/teachers")
	class Create {

		@Test
		@DisplayName("TENANT_ADMIN + valid body → 201")
		void happyPath() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.createTeacher(any(CreateTeacherRequest.class)))
					.willReturn(stubResponse(id));

			CreateTeacherRequest body = new CreateTeacherRequest(
					DocumentType.DNI, "12345678", "Ada", "Lovelace", null,
					null, null, null, null, null, null, null, null, null);

			mockMvc.perform(post(BASE)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(body)))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.publicUuid").value(id.toString()));
		}

		@Test
		@DisplayName("document collision → 409 TEACHER_DOCUMENT_TAKEN")
		void documentCollision() throws Exception {
			given(service.createTeacher(any(CreateTeacherRequest.class)))
					.willThrow(new ConflictException("TEACHER_DOCUMENT_TAKEN", "dup"));

			CreateTeacherRequest body = new CreateTeacherRequest(
					DocumentType.DNI, "12345678", "Ada", "Lovelace", null,
					null, null, null, null, null, null, null, null, null);

			mockMvc.perform(post(BASE)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(body)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code").value("TEACHER_DOCUMENT_TAKEN"));
		}

		@Test
		@DisplayName("invalid body (missing documentType) → 400 with REQUIRED error")
		void invalidBody() throws Exception {
			String body = "{\"documentNumber\":\"12345678\",\"firstName\":\"Ada\",\"lastName\":\"Lovelace\"}";

			mockMvc.perform(post(BASE)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(body))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors[0].field").value("documentType"));
		}
	}

	// =========================================================================
	// POST /{uuid}/link-user
	// =========================================================================

	@Nested
	@DisplayName("POST /v1/teachers/{publicUuid}/link-user")
	class LinkUser {

		@Test
		@DisplayName("happy path → 200 with envelope")
		void happyPath() throws Exception {
			UUID teacherId = UUID.randomUUID();
			UUID userId = UUID.randomUUID();
			given(service.linkUser(eq(teacherId), any(LinkTeacherUserRequest.class)))
					.willReturn(stubResponse(teacherId));

			mockMvc.perform(post(BASE + "/" + teacherId + "/link-user")
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(new LinkTeacherUserRequest(userId))))
					.andExpect(status().isOk());
		}

		@Test
		@DisplayName("user not teacher role → 409 USER_NOT_TEACHER_ROLE")
		void notTeacherRole() throws Exception {
			UUID teacherId = UUID.randomUUID();
			given(service.linkUser(eq(teacherId), any()))
					.willThrow(new ConflictException("USER_NOT_TEACHER_ROLE", "no role"));

			mockMvc.perform(post(BASE + "/" + teacherId + "/link-user")
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(new LinkTeacherUserRequest(UUID.randomUUID()))))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code").value("USER_NOT_TEACHER_ROLE"));
		}
	}

	// =========================================================================
	// POST /{uuid}/invite
	// =========================================================================

	@Nested
	@DisplayName("POST /v1/teachers/{publicUuid}/invite")
	class Invite {

		@Test
		@DisplayName("happy path → 200 with token + expiresAt")
		void happyPath() throws Exception {
			UUID teacherId = UUID.randomUUID();
			given(service.invite(teacherId)).willReturn(new InviteTeacherResponse(
					UUID.randomUUID(), "tok", Instant.parse("2026-12-31T00:00:00Z"),
					teacherId, "ada@acme.test"));

			mockMvc.perform(post(BASE + "/" + teacherId + "/invite")
							.with(csrf())
							.with(authentication(adminAuth())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.invitationToken").value("tok"));
		}

		@Test
		@DisplayName("teacher already has user → 409")
		void alreadyHasUser() throws Exception {
			UUID teacherId = UUID.randomUUID();
			given(service.invite(teacherId))
					.willThrow(new ConflictException("TEACHER_ALREADY_HAS_USER", "linked"));

			mockMvc.perform(post(BASE + "/" + teacherId + "/invite")
							.with(csrf())
							.with(authentication(adminAuth())))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code").value("TEACHER_ALREADY_HAS_USER"));
		}

		@Test
		@DisplayName("teacher has no email → 422 TEACHER_NEEDS_EMAIL_TO_INVITE")
		void noEmail() throws Exception {
			UUID teacherId = UUID.randomUUID();
			given(service.invite(teacherId))
					.willThrow(new BusinessException("TEACHER_NEEDS_EMAIL_TO_INVITE", "missing email"));

			mockMvc.perform(post(BASE + "/" + teacherId + "/invite")
							.with(csrf())
							.with(authentication(adminAuth())))
					.andExpect(status().isUnprocessableEntity())
					.andExpect(jsonPath("$.errors[0].code").value("TEACHER_NEEDS_EMAIL_TO_INVITE"));
		}
	}

	// =========================================================================
	// DELETE /{publicUuid}
	// =========================================================================

	@Test
	@DisplayName("DELETE /v1/teachers/{publicUuid} → 204")
	void deleteHappyPath() throws Exception {
		UUID id = UUID.randomUUID();

		mockMvc.perform(delete(BASE + "/" + id)
						.with(csrf())
						.with(authentication(adminAuth())))
				.andExpect(status().isNoContent());
	}
}
