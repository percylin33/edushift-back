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
import com.edushift.modules.students.dto.CreateStudentRequest;
import com.edushift.modules.students.dto.StudentListItem;
import com.edushift.modules.students.dto.StudentResponse;
import com.edushift.modules.students.dto.UpdateStudentRequest;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.EnrollmentStatus;
import com.edushift.modules.students.entity.Gender;
import com.edushift.modules.students.service.StudentService;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.GlobalExceptionHandler;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
		controllers = StudentController.class,
		excludeFilters = @ComponentScan.Filter(
				type = FilterType.ASSIGNABLE_TYPE,
				classes = {MultiTenancyConfiguration.class, TenantInterceptor.class}))
@Import({
		GlobalExceptionHandler.class,
		com.edushift.config.SecurityConfig.class,
		com.edushift.config.WebConfiguration.class,
		com.edushift.test.EdushiftWebMvcTestConfig.class,
})
class StudentControllerTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;

	@MockitoBean private StudentService service;
	@MockitoBean private JwtService jwtService;
	@MockitoBean private com.edushift.shared.security.LmsRoleAuthorityMapper roleAuthorityMapper;

private static final String BASE = "/v1/students";

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

	private StudentResponse stubResponse(UUID publicUuid) {
		return new StudentResponse(
				publicUuid,
				DocumentType.DNI, "12345678",
				"Ada", "Lovelace", "Byron",
				"Ada Lovelace Byron",
				LocalDate.of(1815, 12, 10),
				Gender.FEMALE,
				"ada@acme.test", null, null,
				EnrollmentStatus.ENROLLED, LocalDate.of(2026, 3, 1),
				null,
				new HashMap<>(),
				Instant.parse("2026-01-01T00:00:00Z"),
				Instant.parse("2026-06-01T00:00:00Z"));
	}

	private StudentListItem stubListItem(String docNumber) {
		return new StudentListItem(
				UUID.randomUUID(),
				DocumentType.DNI, docNumber,
				"Ada", "Lovelace", "Ada Lovelace",
				"ada@acme.test",
				EnrollmentStatus.ENROLLED, null);
	}

	// ===========================================================================
	// GET / (list)
	// ===========================================================================

	@Nested
	@DisplayName("GET /v1/students — list")
	class ListStudents {

		@Test
		@DisplayName("TENANT_ADMIN → 200 with a page of list items")
		void happyPath() throws Exception {
			given(service.listStudents(any(), any(Pageable.class)))
					.willReturn(new PageImpl<>(List.of(
							stubListItem("11111111"), stubListItem("22222222"))));

			mockMvc.perform(get(BASE).with(authentication(adminAuth())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.content[0].documentNumber").value("11111111"));
		}

		@Test
		@DisplayName("anonymous → 401")
		void anonymousRejected() throws Exception {
			mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
		}

		@Test
		@DisplayName("authenticated but no role → 403")
		void forbiddenWithoutRole() throws Exception {
			mockMvc.perform(get(BASE).with(authentication(plainAuth())))
					.andExpect(status().isForbidden());
		}
	}

	// ===========================================================================
	// GET /{publicUuid}
	// ===========================================================================

	@Nested
	@DisplayName("GET /v1/students/{publicUuid}")
	class GetOne {

		@Test
		@DisplayName("happy path — 200 with envelope")
		void happyPath() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.getStudent(id)).willReturn(stubResponse(id));

			mockMvc.perform(get(BASE + "/" + id).with(authentication(adminAuth())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success").value(true))
					.andExpect(jsonPath("$.data.fullName").value("Ada Lovelace Byron"));
		}

		@Test
		@DisplayName("unknown publicUuid → 404 RESOURCE_NOT_FOUND")
		void notFound() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.getStudent(id))
					.willThrow(new ResourceNotFoundException("Student", id));

			mockMvc.perform(get(BASE + "/" + id).with(authentication(adminAuth())))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_FOUND"));
		}
	}

	// ===========================================================================
	// POST /
	// ===========================================================================

	@Nested
	@DisplayName("POST /v1/students")
	class Create {

		@Test
		@DisplayName("TENANT_ADMIN + valid body → 201")
		void happyPath() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.createStudent(any(CreateStudentRequest.class))).willReturn(stubResponse(id));

			CreateStudentRequest body = new CreateStudentRequest(
					DocumentType.DNI, "12345678",
					"Ada", "Lovelace", null,
					null, null, null, null, null,
					null, null, null);

			mockMvc.perform(post(BASE)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(body)))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.publicUuid").value(id.toString()));
		}

		@Test
		@DisplayName("duplicate document → 409 STUDENT_DOCUMENT_TAKEN")
		void documentTaken() throws Exception {
			given(service.createStudent(any()))
					.willThrow(new ConflictException("STUDENT_DOCUMENT_TAKEN", "taken"));

			CreateStudentRequest body = new CreateStudentRequest(
					DocumentType.DNI, "12345678",
					"Ada", "Lovelace", null,
					null, null, null, null, null,
					null, null, null);

			mockMvc.perform(post(BASE)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(body)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code").value("STUDENT_DOCUMENT_TAKEN"));
		}

		@Test
		@DisplayName("missing required field → 400 (validation, service never invoked)")
		void missingFieldRejected() throws Exception {
			CreateStudentRequest body = new CreateStudentRequest(
					null, "12345678",
					"Ada", "Lovelace", null,
					null, null, null, null, null,
					null, null, null);

			mockMvc.perform(post(BASE)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(body)))
					.andExpect(status().isBadRequest());

			then(service).should(never()).createStudent(any());
		}

		@Test
		@DisplayName("missing role → 403")
		void forbiddenWithoutRole() throws Exception {
			CreateStudentRequest body = new CreateStudentRequest(
					DocumentType.DNI, "12345678",
					"Ada", "Lovelace", null,
					null, null, null, null, null,
					null, null, null);

			mockMvc.perform(post(BASE)
							.with(csrf())
							.with(authentication(plainAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(body)))
					.andExpect(status().isForbidden());
		}
	}

	// ===========================================================================
	// PUT /{publicUuid}
	// ===========================================================================

	@Nested
	@DisplayName("PUT /v1/students/{publicUuid}")
	class Update {

		@Test
		@DisplayName("happy path — 200")
		void happyPath() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.updateStudent(eq(id), any(UpdateStudentRequest.class)))
					.willReturn(stubResponse(id));

			UpdateStudentRequest patch = new UpdateStudentRequest(
					null, null, "Augusta", null, null, null, null,
					null, null, null, null, null, null);

			mockMvc.perform(put(BASE + "/" + id)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(patch)))
					.andExpect(status().isOk());
		}

		@Test
		@DisplayName("collision on document change → 409 STUDENT_DOCUMENT_TAKEN")
		void documentCollision() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.updateStudent(eq(id), any()))
					.willThrow(new ConflictException("STUDENT_DOCUMENT_TAKEN", "taken"));

			UpdateStudentRequest patch = new UpdateStudentRequest(
					null, "99999999", null, null, null, null, null,
					null, null, null, null, null, null);

			mockMvc.perform(put(BASE + "/" + id)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(patch)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code").value("STUDENT_DOCUMENT_TAKEN"));
		}
	}

	// ===========================================================================
	// DELETE /{publicUuid}
	// ===========================================================================

	@Nested
	@DisplayName("DELETE /v1/students/{publicUuid}")
	class Delete {

		@Test
		@DisplayName("happy path — 204")
		void happyPath() throws Exception {
			UUID id = UUID.randomUUID();

			mockMvc.perform(delete(BASE + "/" + id)
							.with(csrf())
							.with(authentication(adminAuth())))
					.andExpect(status().isNoContent());

			then(service).should().deleteStudent(id);
		}

		@Test
		@DisplayName("unknown publicUuid → 404")
		void notFound() throws Exception {
			UUID id = UUID.randomUUID();
			willThrow(new ResourceNotFoundException("Student", id))
					.given(service).deleteStudent(id);

			mockMvc.perform(delete(BASE + "/" + id)
							.with(csrf())
							.with(authentication(adminAuth())))
					.andExpect(status().isNotFound());
		}

		@Test
		@DisplayName("missing role → 403, service never invoked")
		void forbiddenWithoutRole() throws Exception {
			UUID id = UUID.randomUUID();

			mockMvc.perform(delete(BASE + "/" + id)
							.with(csrf())
							.with(authentication(plainAuth())))
					.andExpect(status().isForbidden());

			then(service).should(never()).deleteStudent(any());
		}
	}
}
