package com.edushift.modules.teachers.assignments.controller;

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
import com.edushift.modules.academic.period.entity.PeriodType;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.teachers.assignments.dto.AssignmentListItem;
import com.edushift.modules.teachers.assignments.dto.AssignmentResponse;
import com.edushift.modules.teachers.assignments.dto.CreateAssignmentRequest;
import com.edushift.modules.teachers.assignments.dto.SectionTeacherItem;
import com.edushift.modules.teachers.assignments.service.TeacherAssignmentService;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.GlobalExceptionHandler;
import com.edushift.shared.exception.ResourceNotFoundException;
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

@WebMvcTest(
		controllers = TeacherAssignmentController.class,
		excludeFilters = @ComponentScan.Filter(
				type = FilterType.ASSIGNABLE_TYPE,
				classes = {MultiTenancyConfiguration.class, TenantInterceptor.class}))
@Import({
		GlobalExceptionHandler.class,
		com.edushift.config.SecurityConfig.class,
		com.edushift.config.WebConfiguration.class,
		com.edushift.test.EdushiftWebMvcTestConfig.class,
})
class TeacherAssignmentControllerTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;

	@MockitoBean private TeacherAssignmentService service;
	@MockitoBean private JwtService jwtService;
	@MockitoBean private com.edushift.shared.security.LmsRoleAuthorityMapper roleAuthorityMapper;

private static final String TEACHERS_BASE = "/v1/teachers";
	private static final String ASSIGNMENTS_BASE = "/v1/assignments";
	private static final String SECTIONS_BASE = "/v1/academic/sections";

	private String json(Object value) throws Exception {
		return objectMapper.writeValueAsString(value);
	}

	private static JwtAuthenticationToken adminAuth() {
		JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
				UUID.randomUUID(), UUID.randomUUID(),
				"acme", "admin@acme.test");
		return new JwtAuthenticationToken(principal, "fake.token",
				List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")));
	}

	private static JwtAuthenticationToken plainAuth() {
		JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
				UUID.randomUUID(), UUID.randomUUID(),
				"acme", "u@acme.test");
		return new JwtAuthenticationToken(principal, "fake.token", List.<GrantedAuthority>of());
	}

	private static AssignmentResponse stubResponse(UUID publicUuid, UUID teacherUuid) {
		return new AssignmentResponse(
				publicUuid,
				teacherUuid, "Ada Lovelace",
				UUID.randomUUID(), "A",
				UUID.randomUUID(), "MAT", "Matematica",
				UUID.randomUUID(), PeriodType.BIMESTRE, 1, "I Bimestre",
				UUID.randomUUID(), "2026",
				Instant.parse("2026-03-01T00:00:00Z"), null, true, null,
				Instant.parse("2026-03-01T00:00:00Z"),
				Instant.parse("2026-03-01T00:00:00Z"));
	}

	private static AssignmentListItem stubListItem() {
		return new AssignmentListItem(
				UUID.randomUUID(),
				UUID.randomUUID(), "Ada Lovelace",
				UUID.randomUUID(), "A",
				UUID.randomUUID(), "MAT", "Matematica",
				UUID.randomUUID(), PeriodType.BIMESTRE, 1,
				Instant.parse("2026-03-01T00:00:00Z"), null, true);
	}

	private static SectionTeacherItem stubSectionItem() {
		return new SectionTeacherItem(
				UUID.randomUUID(),
				UUID.randomUUID(), "Ada Lovelace", "ada@acme.test",
				UUID.randomUUID(), "MAT", "Matematica",
				UUID.randomUUID(), PeriodType.BIMESTRE, 1,
				Instant.parse("2026-03-01T00:00:00Z"));
	}

	// =========================================================================
	// POST /v1/teachers/{teacherUuid}/assignments
	// =========================================================================

	@Nested
	@DisplayName("POST /v1/teachers/{teacherUuid}/assignments")
	class Create {

		@Test
		@DisplayName("TENANT_ADMIN + valid body → 201")
		void happyPath() throws Exception {
			UUID teacherUuid = UUID.randomUUID();
			UUID publicUuid = UUID.randomUUID();
			given(service.createAssignment(eq(teacherUuid), any(CreateAssignmentRequest.class)))
					.willReturn(stubResponse(publicUuid, teacherUuid));

			CreateAssignmentRequest body = new CreateAssignmentRequest(
					UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null);

			mockMvc.perform(post(TEACHERS_BASE + "/" + teacherUuid + "/assignments")
							.with(csrf()).with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(body)))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.publicUuid").value(publicUuid.toString()))
					.andExpect(jsonPath("$.data.active").value(true));
		}

		@Test
		@DisplayName("year mismatch → 409 ASSIGNMENT_YEAR_MISMATCH")
		void yearMismatch() throws Exception {
			UUID teacherUuid = UUID.randomUUID();
			given(service.createAssignment(any(), any()))
					.willThrow(new ConflictException("ASSIGNMENT_YEAR_MISMATCH", "diff years"));

			CreateAssignmentRequest body = new CreateAssignmentRequest(
					UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null);

			mockMvc.perform(post(TEACHERS_BASE + "/" + teacherUuid + "/assignments")
							.with(csrf()).with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(body)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code").value("ASSIGNMENT_YEAR_MISMATCH"));
		}

		@Test
		@DisplayName("course not applicable to level → 409 COURSE_NOT_APPLICABLE_TO_SECTION_LEVEL")
		void courseNotApplicable() throws Exception {
			UUID teacherUuid = UUID.randomUUID();
			given(service.createAssignment(any(), any()))
					.willThrow(new ConflictException("COURSE_NOT_APPLICABLE_TO_SECTION_LEVEL",
							"course not in level"));

			mockMvc.perform(post(TEACHERS_BASE + "/" + teacherUuid + "/assignments")
							.with(csrf()).with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(new CreateAssignmentRequest(
									UUID.randomUUID(), UUID.randomUUID(),
									UUID.randomUUID(), null))))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code")
							.value("COURSE_NOT_APPLICABLE_TO_SECTION_LEVEL"));
		}

		@Test
		@DisplayName("RESIGNED teacher → 409 TEACHER_NOT_ACTIVE")
		void teacherNotActive() throws Exception {
			UUID teacherUuid = UUID.randomUUID();
			given(service.createAssignment(any(), any()))
					.willThrow(new ConflictException("TEACHER_NOT_ACTIVE", "resigned"));

			mockMvc.perform(post(TEACHERS_BASE + "/" + teacherUuid + "/assignments")
							.with(csrf()).with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(new CreateAssignmentRequest(
									UUID.randomUUID(), UUID.randomUUID(),
									UUID.randomUUID(), null))))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code").value("TEACHER_NOT_ACTIVE"));
		}

		@Test
		@DisplayName("invalid body (missing sectionPublicUuid) → 400 with field error")
		void invalidBody() throws Exception {
			UUID teacherUuid = UUID.randomUUID();
			String body = "{\"coursePublicUuid\":\"" + UUID.randomUUID()
					+ "\",\"academicPeriodPublicUuid\":\"" + UUID.randomUUID() + "\"}";

			mockMvc.perform(post(TEACHERS_BASE + "/" + teacherUuid + "/assignments")
							.with(csrf()).with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(body))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors[0].field").value("sectionPublicUuid"));
		}

		@Test
		@DisplayName("anonymous → 401")
		void anonymous() throws Exception {
			mockMvc.perform(post(TEACHERS_BASE + "/" + UUID.randomUUID() + "/assignments")
							.with(csrf()).contentType(MediaType.APPLICATION_JSON)
							.content("{}"))
					.andExpect(status().isUnauthorized());
		}

		@Test
		@DisplayName("authenticated but no role → 403")
		void noRole() throws Exception {
			mockMvc.perform(post(TEACHERS_BASE + "/" + UUID.randomUUID() + "/assignments")
							.with(csrf()).with(authentication(plainAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(new CreateAssignmentRequest(
									UUID.randomUUID(), UUID.randomUUID(),
									UUID.randomUUID(), null))))
					.andExpect(status().isForbidden());
		}
	}

	// =========================================================================
	// GET /v1/teachers/{teacherUuid}/assignments
	// =========================================================================

	@Nested
	@DisplayName("GET /v1/teachers/{teacherUuid}/assignments")
	class ListForTeacher {

		@Test
		@DisplayName("default → activeOnly=true, no period")
		void defaultActive() throws Exception {
			UUID teacherUuid = UUID.randomUUID();
			given(service.listForTeacher(eq(teacherUuid), eq(null), eq(true)))
					.willReturn(List.of(stubListItem()));

			mockMvc.perform(get(TEACHERS_BASE + "/" + teacherUuid + "/assignments")
							.with(authentication(adminAuth())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(1))
					.andExpect(jsonPath("$[0].active").value(true));
		}

		@Test
		@DisplayName("?periodId=... &active=false → routes through to service")
		void historicalWithPeriod() throws Exception {
			UUID teacherUuid = UUID.randomUUID();
			UUID periodId = UUID.randomUUID();
			given(service.listForTeacher(eq(teacherUuid), eq(periodId), eq(false)))
					.willReturn(List.of(stubListItem()));

			mockMvc.perform(get(TEACHERS_BASE + "/" + teacherUuid + "/assignments")
							.param("periodId", periodId.toString())
							.param("active", "false")
							.with(authentication(adminAuth())))
					.andExpect(status().isOk());
		}

		@Test
		@DisplayName("teacher unknown → 404")
		void notFound() throws Exception {
			UUID teacherUuid = UUID.randomUUID();
			given(service.listForTeacher(eq(teacherUuid), any(), eq(true)))
					.willThrow(new ResourceNotFoundException("Teacher", teacherUuid));

			mockMvc.perform(get(TEACHERS_BASE + "/" + teacherUuid + "/assignments")
							.with(authentication(adminAuth())))
					.andExpect(status().isNotFound());
		}
	}

	// =========================================================================
	// DELETE /v1/assignments/{publicUuid}
	// =========================================================================

	@Test
	@DisplayName("DELETE /v1/assignments/{publicUuid} → 204")
	void softEnd() throws Exception {
		UUID publicUuid = UUID.randomUUID();

		mockMvc.perform(delete(ASSIGNMENTS_BASE + "/" + publicUuid)
						.with(csrf()).with(authentication(adminAuth())))
				.andExpect(status().isNoContent());
	}

	// =========================================================================
	// GET /v1/academic/sections/{sectionUuid}/teachers
	// =========================================================================

	@Test
	@DisplayName("GET /v1/academic/sections/{uuid}/teachers → 200 list")
	void listForSectionHappy() throws Exception {
		UUID sectionUuid = UUID.randomUUID();
		given(service.listForSection(eq(sectionUuid), eq(null)))
				.willReturn(List.of(stubSectionItem()));

		mockMvc.perform(get(SECTIONS_BASE + "/" + sectionUuid + "/teachers")
						.with(authentication(adminAuth())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].teacherFullName").value("Ada Lovelace"));
	}
}
