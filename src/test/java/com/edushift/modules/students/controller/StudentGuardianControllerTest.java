package com.edushift.modules.students.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.infrastructure.multitenancy.MultiTenancyConfiguration;
import com.edushift.infrastructure.multitenancy.TenantInterceptor;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.students.dto.AddGuardianRequest;
import com.edushift.modules.students.dto.GuardianResponse;
import com.edushift.modules.students.dto.UpdateGuardianLinkRequest;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.RelationshipType;
import com.edushift.modules.students.service.StudentGuardianService;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.GlobalExceptionHandler;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@WebMvcTest(
		controllers = StudentGuardianController.class,
		excludeFilters = @ComponentScan.Filter(
				type = FilterType.ASSIGNABLE_TYPE,
				classes = {MultiTenancyConfiguration.class, TenantInterceptor.class}))
@Import({
		GlobalExceptionHandler.class,
		com.edushift.config.SecurityConfig.class,
		com.edushift.config.WebConfiguration.class
})
class StudentGuardianControllerTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;

	@MockitoBean private StudentGuardianService service;
	@MockitoBean private JwtService jwtService;
	@MockitoBean private com.edushift.shared.security.LmsRoleAuthorityMapper roleAuthorityMapper;

	private static final UUID STUDENT_ID = UUID.randomUUID();

	private String base() {
		return "/v1/students/" + STUDENT_ID + "/guardians";
	}

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

	private GuardianResponse stubResponse(UUID guardianPublicUuid, String firstName,
			RelationshipType type, boolean primary) {
		return new GuardianResponse(
				UUID.randomUUID(),
				guardianPublicUuid,
				DocumentType.DNI, "11111111",
				firstName, "Lovelace",
				firstName + " Lovelace",
				"anna@acme.test", "+51 999", "Engineer",
				type, primary, true);
	}

	// ===========================================================================
	// GET /
	// ===========================================================================

	@Nested
	@DisplayName("GET /v1/students/{id}/guardians")
	class List_ {

		@Test
		@DisplayName("TENANT_ADMIN → 200 with the list")
		void happyPath() throws Exception {
			UUID g1 = UUID.randomUUID();
			UUID g2 = UUID.randomUUID();
			given(service.listGuardians(STUDENT_ID)).willReturn(List.of(
					stubResponse(g1, "Anna", RelationshipType.MOTHER, true),
					stubResponse(g2, "Bob", RelationshipType.FATHER, false)));

			mockMvc.perform(get(base()).with(authentication(adminAuth())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success").value(true))
					.andExpect(jsonPath("$.data[0].firstName").value("Anna"))
					.andExpect(jsonPath("$.data[1].firstName").value("Bob"));
		}

		@Test
		@DisplayName("anonymous → 401")
		void anonymousRejected() throws Exception {
			mockMvc.perform(get(base())).andExpect(status().isUnauthorized());
		}

		@Test
		@DisplayName("authenticated but no role → 403")
		void forbiddenWithoutRole() throws Exception {
			mockMvc.perform(get(base()).with(authentication(plainAuth())))
					.andExpect(status().isForbidden());
		}

		@Test
		@DisplayName("unknown student → 404 RESOURCE_NOT_FOUND")
		void unknownStudent() throws Exception {
			given(service.listGuardians(STUDENT_ID))
					.willThrow(new ResourceNotFoundException("Student", STUDENT_ID));

			mockMvc.perform(get(base()).with(authentication(adminAuth())))
					.andExpect(status().isNotFound());
		}
	}

	// ===========================================================================
	// POST /
	// ===========================================================================

	@Nested
	@DisplayName("POST /v1/students/{id}/guardians")
	class Add {

		private AddGuardianRequest validBody() {
			return new AddGuardianRequest(
					DocumentType.DNI, "11111111",
					"Anna", "Lovelace",
					"anna@acme.test", "+51 999", "Engineer",
					RelationshipType.MOTHER, true, true);
		}

		@Test
		@DisplayName("TENANT_ADMIN + valid body → 201")
		void happyPath() throws Exception {
			UUID guardianId = UUID.randomUUID();
			given(service.addGuardian(eq(STUDENT_ID), any(AddGuardianRequest.class)))
					.willReturn(stubResponse(guardianId, "Anna", RelationshipType.MOTHER, true));

			mockMvc.perform(post(base())
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(validBody())))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.guardianPublicUuid").value(guardianId.toString()))
					.andExpect(jsonPath("$.data.relationship").value("MOTHER"))
					.andExpect(jsonPath("$.data.isPrimaryContact").value(true));
		}

		@Test
		@DisplayName("duplicate active link → 409 GUARDIAN_ALREADY_LINKED")
		void duplicate() throws Exception {
			given(service.addGuardian(eq(STUDENT_ID), any()))
					.willThrow(new ConflictException("GUARDIAN_ALREADY_LINKED", "linked"));

			mockMvc.perform(post(base())
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(validBody())))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code").value("GUARDIAN_ALREADY_LINKED"));
		}

		@Test
		@DisplayName("missing required field → 400, service never invoked")
		void validationFails() throws Exception {
			AddGuardianRequest body = new AddGuardianRequest(
					null, "11111111",
					"Anna", "Lovelace",
					null, null, null,
					RelationshipType.MOTHER, true, true);

			mockMvc.perform(post(base())
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(body)))
					.andExpect(status().isBadRequest());

			then(service).should(never()).addGuardian(any(), any());
		}

		@Test
		@DisplayName("missing role → 403")
		void forbiddenWithoutRole() throws Exception {
			mockMvc.perform(post(base())
							.with(csrf())
							.with(authentication(plainAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(validBody())))
					.andExpect(status().isForbidden());
		}
	}

	// ===========================================================================
	// PUT /{guardianPublicUuid}
	// ===========================================================================

	@Nested
	@DisplayName("PUT /v1/students/{id}/guardians/{guardianId}")
	class Update {

		@Test
		@DisplayName("happy path — 200")
		void happyPath() throws Exception {
			UUID guardianId = UUID.randomUUID();
			given(service.updateLink(eq(STUDENT_ID), eq(guardianId),
					any(UpdateGuardianLinkRequest.class)))
					.willReturn(stubResponse(guardianId, "Anna", RelationshipType.MOTHER, true));

			UpdateGuardianLinkRequest patch = new UpdateGuardianLinkRequest(
					null, null, true);

			mockMvc.perform(put(base() + "/" + guardianId)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(patch)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.canPickupStudent").value(true));
		}

		@Test
		@DisplayName("removing the last primary contact → 422 LAST_PRIMARY_CONTACT")
		void lastPrimary() throws Exception {
			UUID guardianId = UUID.randomUUID();
			given(service.updateLink(eq(STUDENT_ID), eq(guardianId), any()))
					.willThrow(new BusinessException("LAST_PRIMARY_CONTACT",
							"primary contact required"));

			UpdateGuardianLinkRequest patch = new UpdateGuardianLinkRequest(
					null, false, null);

			mockMvc.perform(put(base() + "/" + guardianId)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(patch)))
					.andExpect(status().isUnprocessableEntity())
					.andExpect(jsonPath("$.errors[0].code").value("LAST_PRIMARY_CONTACT"));
		}

		@Test
		@DisplayName("unknown link → 404")
		void unknownLink() throws Exception {
			UUID guardianId = UUID.randomUUID();
			given(service.updateLink(eq(STUDENT_ID), eq(guardianId), any()))
					.willThrow(new ResourceNotFoundException(
							"StudentGuardian link", STUDENT_ID + "/" + guardianId));

			mockMvc.perform(put(base() + "/" + guardianId)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(new UpdateGuardianLinkRequest(
									RelationshipType.GUARDIAN, null, null))))
					.andExpect(status().isNotFound());
		}
	}

	// ===========================================================================
	// DELETE /{guardianPublicUuid}
	// ===========================================================================

	@Nested
	@DisplayName("DELETE /v1/students/{id}/guardians/{guardianId}")
	class Unlink {

		@Test
		@DisplayName("happy path — 204")
		void happyPath() throws Exception {
			UUID guardianId = UUID.randomUUID();

			mockMvc.perform(delete(base() + "/" + guardianId)
							.with(csrf())
							.with(authentication(adminAuth())))
					.andExpect(status().isNoContent());

			then(service).should().unlinkGuardian(STUDENT_ID, guardianId);
		}

		@Test
		@DisplayName("removing the last primary → 422 LAST_PRIMARY_CONTACT")
		void lastPrimary() throws Exception {
			UUID guardianId = UUID.randomUUID();
			willThrow(new BusinessException("LAST_PRIMARY_CONTACT", "primary contact required"))
					.given(service).unlinkGuardian(STUDENT_ID, guardianId);

			mockMvc.perform(delete(base() + "/" + guardianId)
							.with(csrf())
							.with(authentication(adminAuth())))
					.andExpect(status().isUnprocessableEntity())
					.andExpect(jsonPath("$.errors[0].code").value("LAST_PRIMARY_CONTACT"));
		}

		@Test
		@DisplayName("missing role → 403, service never invoked")
		void forbiddenWithoutRole() throws Exception {
			UUID guardianId = UUID.randomUUID();

			mockMvc.perform(delete(base() + "/" + guardianId)
							.with(csrf())
							.with(authentication(plainAuth())))
					.andExpect(status().isForbidden());

			then(service).should(never()).unlinkGuardian(any(), any());
		}
	}
}
