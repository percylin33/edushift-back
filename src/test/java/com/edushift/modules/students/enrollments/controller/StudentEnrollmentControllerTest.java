package com.edushift.modules.students.enrollments.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.infrastructure.multitenancy.MultiTenancyConfiguration;
import com.edushift.infrastructure.multitenancy.TenantInterceptor;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.students.enrollments.dto.CreateEnrollmentRequest;
import com.edushift.modules.students.enrollments.dto.EnrollmentListItem;
import com.edushift.modules.students.enrollments.dto.EnrollmentResponse;
import com.edushift.modules.students.enrollments.dto.SectionStudentRosterItem;
import com.edushift.modules.students.enrollments.dto.WithdrawEnrollmentRequest;
import com.edushift.modules.students.enrollments.entity.StudentEnrollmentStatus;
import com.edushift.modules.students.enrollments.service.StudentEnrollmentService;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.GlobalExceptionHandler;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
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
		controllers = StudentEnrollmentController.class,
		excludeFilters = @ComponentScan.Filter(
				type = FilterType.ASSIGNABLE_TYPE,
				classes = {MultiTenancyConfiguration.class, TenantInterceptor.class}))
@Import({
		GlobalExceptionHandler.class,
		com.edushift.config.SecurityConfig.class,
		com.edushift.config.WebConfiguration.class,
		com.edushift.test.EdushiftWebMvcTestConfig.class,
})
class StudentEnrollmentControllerTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;

	@MockitoBean private StudentEnrollmentService service;
	@MockitoBean private JwtService jwtService;
	@MockitoBean private com.edushift.shared.security.LmsRoleAuthorityMapper roleAuthorityMapper;

private static final String STUDENTS_BASE = "/v1/students";
	private static final String ENROLLMENTS_BASE = "/v1/enrollments";
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

	private static EnrollmentResponse stubResponse(UUID publicUuid, UUID studentUuid) {
		return new EnrollmentResponse(
				publicUuid,
				studentUuid, "Ana Garcia", "12345678",
				UUID.randomUUID(), "A",
				UUID.randomUUID(), "2026",
				LocalDate.of(2026, 3, 1), null,
				StudentEnrollmentStatus.ACTIVE, true, null,
				Instant.parse("2026-03-01T00:00:00Z"),
				Instant.parse("2026-03-01T00:00:00Z"));
	}

	private static EnrollmentListItem stubListItem() {
		return new EnrollmentListItem(
				UUID.randomUUID(),
				UUID.randomUUID(), "Ana Garcia",
				UUID.randomUUID(), "A",
				UUID.randomUUID(), "2026",
				LocalDate.of(2026, 3, 1), null,
				StudentEnrollmentStatus.ACTIVE, true);
	}

	private static SectionStudentRosterItem stubRosterItem() {
		return new SectionStudentRosterItem(
				UUID.randomUUID(),
				UUID.randomUUID(), "Ana Garcia",
				DocumentType.DNI, "12345678", "ana@acme.test",
				LocalDate.of(2026, 3, 1), null,
				StudentEnrollmentStatus.ACTIVE, true);
	}

	// =========================================================================
	// POST /v1/students/{studentUuid}/enrollments
	// =========================================================================

	@Nested
	@DisplayName("POST /v1/students/{studentUuid}/enrollments")
	class Create {

		@Test
		@DisplayName("TENANT_ADMIN + valid body → 201")
		void happyPath() throws Exception {
			UUID studentUuid = UUID.randomUUID();
			UUID enrollmentUuid = UUID.randomUUID();
			given(service.createEnrollment(eq(studentUuid), any(CreateEnrollmentRequest.class)))
					.willReturn(stubResponse(enrollmentUuid, studentUuid));

			mockMvc.perform(post(STUDENTS_BASE + "/" + studentUuid + "/enrollments")
							.with(authentication(adminAuth()))
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(new CreateEnrollmentRequest(
									UUID.randomUUID(), UUID.randomUUID(),
									LocalDate.of(2026, 3, 1), null))))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.publicUuid").value(enrollmentUuid.toString()))
					.andExpect(jsonPath("$.data.active").value(true));
		}

		@Test
		@DisplayName("anonymous → 401")
		void anonymous() throws Exception {
			mockMvc.perform(post(STUDENTS_BASE + "/" + UUID.randomUUID() + "/enrollments")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(new CreateEnrollmentRequest(
									UUID.randomUUID(), UUID.randomUUID(),
									LocalDate.of(2026, 3, 1), null))))
					.andExpect(status().isUnauthorized());
		}

		@Test
		@DisplayName("non-admin → 403")
		void forbidden() throws Exception {
			mockMvc.perform(post(STUDENTS_BASE + "/" + UUID.randomUUID() + "/enrollments")
							.with(authentication(plainAuth()))
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(new CreateEnrollmentRequest(
									UUID.randomUUID(), UUID.randomUUID(),
									LocalDate.of(2026, 3, 1), null))))
					.andExpect(status().isForbidden());
		}

		@Test
		@DisplayName("missing required fields → 400 VALIDATION")
		void missingRequired() throws Exception {
			mockMvc.perform(post(STUDENTS_BASE + "/" + UUID.randomUUID() + "/enrollments")
							.with(authentication(adminAuth()))
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content("{}"))
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("section / year mismatch → 409 ENROLLMENT_YEAR_MISMATCH")
		void yearMismatch() throws Exception {
			UUID studentUuid = UUID.randomUUID();
			given(service.createEnrollment(eq(studentUuid), any(CreateEnrollmentRequest.class)))
					.willThrow(new ConflictException("ENROLLMENT_YEAR_MISMATCH",
							"Section belongs to year '2025'..."));

			mockMvc.perform(post(STUDENTS_BASE + "/" + studentUuid + "/enrollments")
							.with(authentication(adminAuth()))
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(new CreateEnrollmentRequest(
									UUID.randomUUID(), UUID.randomUUID(),
									LocalDate.of(2026, 3, 1), null))))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code").value("ENROLLMENT_YEAR_MISMATCH"));
		}

		@Test
		@DisplayName("date out of year → 409 ENROLLMENT_DATE_OUT_OF_YEAR")
		void dateOutOfYear() throws Exception {
			UUID studentUuid = UUID.randomUUID();
			given(service.createEnrollment(eq(studentUuid), any(CreateEnrollmentRequest.class)))
					.willThrow(new ConflictException("ENROLLMENT_DATE_OUT_OF_YEAR",
							"enrolledAt 2027-01-01 is outside..."));

			mockMvc.perform(post(STUDENTS_BASE + "/" + studentUuid + "/enrollments")
							.with(authentication(adminAuth()))
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(new CreateEnrollmentRequest(
									UUID.randomUUID(), UUID.randomUUID(),
									LocalDate.of(2027, 1, 1), null))))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code").value("ENROLLMENT_DATE_OUT_OF_YEAR"));
		}

		@Test
		@DisplayName("duplicate active → 409 STUDENT_ALREADY_ENROLLED")
		void duplicateActive() throws Exception {
			UUID studentUuid = UUID.randomUUID();
			given(service.createEnrollment(eq(studentUuid), any(CreateEnrollmentRequest.class)))
					.willThrow(new ConflictException("STUDENT_ALREADY_ENROLLED",
							"Student 'Ana' already has an active enrollment in year '2026'."));

			mockMvc.perform(post(STUDENTS_BASE + "/" + studentUuid + "/enrollments")
							.with(authentication(adminAuth()))
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(new CreateEnrollmentRequest(
									UUID.randomUUID(), UUID.randomUUID(),
									LocalDate.of(2026, 3, 1), null))))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code").value("STUDENT_ALREADY_ENROLLED"));
		}

		@Test
		@DisplayName("student not found → 404")
		void notFound() throws Exception {
			UUID studentUuid = UUID.randomUUID();
			given(service.createEnrollment(eq(studentUuid), any(CreateEnrollmentRequest.class)))
					.willThrow(new ResourceNotFoundException("Student", studentUuid));

			mockMvc.perform(post(STUDENTS_BASE + "/" + studentUuid + "/enrollments")
							.with(authentication(adminAuth()))
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(new CreateEnrollmentRequest(
									UUID.randomUUID(), UUID.randomUUID(),
									LocalDate.of(2026, 3, 1), null))))
					.andExpect(status().isNotFound());
		}
	}

	// =========================================================================
	// GET /v1/students/{studentUuid}/enrollments
	// =========================================================================

	@Nested
	@DisplayName("GET /v1/students/{studentUuid}/enrollments")
	class ListForStudent {

		@Test
		@DisplayName("TENANT_ADMIN → 200 with timeline")
		void happyPath() throws Exception {
			UUID studentUuid = UUID.randomUUID();
			given(service.listForStudent(studentUuid))
					.willReturn(List.of(stubListItem(), stubListItem()));

			mockMvc.perform(get(STUDENTS_BASE + "/" + studentUuid + "/enrollments")
							.with(authentication(adminAuth())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(2));
		}

		@Test
		@DisplayName("anonymous → 401")
		void anonymous() throws Exception {
			mockMvc.perform(get(STUDENTS_BASE + "/" + UUID.randomUUID() + "/enrollments"))
					.andExpect(status().isUnauthorized());
		}

		@Test
		@DisplayName("non-admin → 403")
		void forbidden() throws Exception {
			mockMvc.perform(get(STUDENTS_BASE + "/" + UUID.randomUUID() + "/enrollments")
							.with(authentication(plainAuth())))
					.andExpect(status().isForbidden());
		}
	}

	// =========================================================================
	// POST /v1/enrollments/{publicUuid}/withdraw
	// =========================================================================

	@Nested
	@DisplayName("POST /v1/enrollments/{publicUuid}/withdraw")
	class Withdraw {

		@Test
		@DisplayName("TENANT_ADMIN + valid body → 200")
		void happyPath() throws Exception {
			UUID enrollmentUuid = UUID.randomUUID();
			given(service.withdrawEnrollment(eq(enrollmentUuid), any(WithdrawEnrollmentRequest.class)))
					.willReturn(stubResponse(enrollmentUuid, UUID.randomUUID()));

			mockMvc.perform(post(ENROLLMENTS_BASE + "/" + enrollmentUuid + "/withdraw")
							.with(authentication(adminAuth()))
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(new WithdrawEnrollmentRequest(
									StudentEnrollmentStatus.TRANSFERRED,
									LocalDate.of(2026, 5, 15)))))
					.andExpect(status().isOk());
		}

		@Test
		@DisplayName("ACTIVE target → 400 INVALID_WITHDRAW_STATUS")
		void invalidStatus() throws Exception {
			UUID enrollmentUuid = UUID.randomUUID();
			given(service.withdrawEnrollment(eq(enrollmentUuid), any(WithdrawEnrollmentRequest.class)))
					.willThrow(new BadRequestException("INVALID_WITHDRAW_STATUS",
							"Withdraw target must be ..."));

			mockMvc.perform(post(ENROLLMENTS_BASE + "/" + enrollmentUuid + "/withdraw")
							.with(authentication(adminAuth()))
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(new WithdrawEnrollmentRequest(
									StudentEnrollmentStatus.ACTIVE,
									LocalDate.of(2026, 5, 15)))))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors[0].code").value("INVALID_WITHDRAW_STATUS"));
		}

		@Test
		@DisplayName("withdrawnAt < enrolledAt → 400 VALIDATION_ERROR")
		void datesOutOfOrder() throws Exception {
			UUID enrollmentUuid = UUID.randomUUID();
			given(service.withdrawEnrollment(eq(enrollmentUuid), any(WithdrawEnrollmentRequest.class)))
					.willThrow(new BadRequestException("VALIDATION_ERROR",
							"withdrawnAt cannot be earlier than enrolledAt"));

			mockMvc.perform(post(ENROLLMENTS_BASE + "/" + enrollmentUuid + "/withdraw")
							.with(authentication(adminAuth()))
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(new WithdrawEnrollmentRequest(
									StudentEnrollmentStatus.WITHDRAWN,
									LocalDate.of(2026, 1, 1)))))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors[0].code").value("VALIDATION_ERROR"));
		}

		@Test
		@DisplayName("not found → 404")
		void notFound() throws Exception {
			UUID enrollmentUuid = UUID.randomUUID();
			given(service.withdrawEnrollment(eq(enrollmentUuid), any(WithdrawEnrollmentRequest.class)))
					.willThrow(new ResourceNotFoundException("StudentEnrollment", enrollmentUuid));

			mockMvc.perform(post(ENROLLMENTS_BASE + "/" + enrollmentUuid + "/withdraw")
							.with(authentication(adminAuth()))
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(new WithdrawEnrollmentRequest(
									StudentEnrollmentStatus.WITHDRAWN,
									LocalDate.of(2026, 5, 15)))))
					.andExpect(status().isNotFound());
		}

		@Test
		@DisplayName("anonymous → 401")
		void anonymous() throws Exception {
			mockMvc.perform(post(ENROLLMENTS_BASE + "/" + UUID.randomUUID() + "/withdraw")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(new WithdrawEnrollmentRequest(
									StudentEnrollmentStatus.WITHDRAWN,
									LocalDate.of(2026, 5, 15)))))
					.andExpect(status().isUnauthorized());
		}

		@Test
		@DisplayName("non-admin → 403")
		void forbidden() throws Exception {
			mockMvc.perform(post(ENROLLMENTS_BASE + "/" + UUID.randomUUID() + "/withdraw")
							.with(authentication(plainAuth()))
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(new WithdrawEnrollmentRequest(
									StudentEnrollmentStatus.WITHDRAWN,
									LocalDate.of(2026, 5, 15)))))
					.andExpect(status().isForbidden());
		}
	}

	// =========================================================================
	// GET /v1/academic/sections/{sectionUuid}/students
	// =========================================================================

	@Nested
	@DisplayName("GET /v1/academic/sections/{sectionUuid}/students")
	class ListRoster {

		@Test
		@DisplayName("TENANT_ADMIN → 200 with roster")
		void happyPath() throws Exception {
			UUID sectionUuid = UUID.randomUUID();
			given(service.listRoster(sectionUuid))
					.willReturn(List.of(stubRosterItem()));

			mockMvc.perform(get(SECTIONS_BASE + "/" + sectionUuid + "/students")
							.with(authentication(adminAuth())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(1))
					.andExpect(jsonPath("$[0].active").value(true));
		}

		@Test
		@DisplayName("non-admin → 403")
		void forbidden() throws Exception {
			mockMvc.perform(get(SECTIONS_BASE + "/" + UUID.randomUUID() + "/students")
							.with(authentication(plainAuth())))
					.andExpect(status().isForbidden());
		}
	}
}
