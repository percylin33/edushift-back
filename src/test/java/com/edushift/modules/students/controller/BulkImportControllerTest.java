package com.edushift.modules.students.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.infrastructure.multitenancy.MultiTenancyConfiguration;
import com.edushift.infrastructure.multitenancy.TenantInterceptor;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.students.dto.BulkImportJobResponse;
import com.edushift.modules.students.entity.BulkImportJobType;
import com.edushift.modules.students.entity.BulkImportStatus;
import com.edushift.modules.students.service.BulkImportService;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.exception.GlobalExceptionHandler;
import com.edushift.shared.exception.ResourceNotFoundException;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
		controllers = BulkImportController.class,
		excludeFilters = @ComponentScan.Filter(
				type = FilterType.ASSIGNABLE_TYPE,
				classes = {MultiTenancyConfiguration.class, TenantInterceptor.class}))
@Import({
		GlobalExceptionHandler.class,
		com.edushift.config.SecurityConfig.class,
		com.edushift.config.WebConfiguration.class,
		com.edushift.test.EdushiftWebMvcTestConfig.class,
})
class BulkImportControllerTest {

	@Autowired private MockMvc mockMvc;

	@MockitoBean private BulkImportService service;
	@MockitoBean private JwtService jwtService;
	@MockitoBean private com.edushift.shared.security.LmsRoleAuthorityMapper roleAuthorityMapper;

private static final String BASE = "/v1/students/bulk-import";

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

	private BulkImportJobResponse stubJob(UUID publicUuid, BulkImportStatus status) {
		return new BulkImportJobResponse(
				publicUuid,
				BulkImportJobType.STUDENTS,
				status,
				"upload.xlsx", 1024L,
				null, 0, 0, List.of(),
				null, null, null, null);
	}

	// ===========================================================================
	// GET /template
	// ===========================================================================

	@Nested
	@DisplayName("GET /v1/students/bulk-import/template")
	class Template {

		@Test
		@DisplayName("happy path — returns the .xlsx with content-disposition")
		void happyPath() throws Exception {
			byte[] payload = new byte[] {0x50, 0x4B, 0x03, 0x04}; // PK zip header, opaque
			given(service.generateStudentsTemplate()).willReturn(payload);

			mockMvc.perform(get(BASE + "/template").with(authentication(adminAuth())))
					.andExpect(status().isOk())
					.andExpect(header().string("Content-Type",
							"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
					.andExpect(header().string("Content-Disposition",
							"attachment; filename=\"students-template.xlsx\""))
					.andExpect(content().bytes(payload));
		}

		@Test
		@DisplayName("anonymous → 401")
		void anonymousRejected() throws Exception {
			mockMvc.perform(get(BASE + "/template")).andExpect(status().isUnauthorized());
		}

		@Test
		@DisplayName("missing role → 403")
		void forbiddenWithoutRole() throws Exception {
			mockMvc.perform(get(BASE + "/template").with(authentication(plainAuth())))
					.andExpect(status().isForbidden());
		}
	}

	// ===========================================================================
	// POST /
	// ===========================================================================

	@Nested
	@DisplayName("POST /v1/students/bulk-import")
	class Upload {

		@Test
		@DisplayName("TENANT_ADMIN + multipart upload → 202 with PENDING job")
		void happyPath() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.enqueueStudentsImport(any()))
					.willReturn(stubJob(id, BulkImportStatus.PENDING));

			MockMultipartFile file = new MockMultipartFile(
					"file", "students.xlsx",
					"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
					"payload".getBytes());

			mockMvc.perform(multipart(BASE).file(file)
							.with(csrf())
							.with(authentication(adminAuth())))
					.andExpect(status().isAccepted())
					.andExpect(jsonPath("$.success").value(true))
					.andExpect(jsonPath("$.data.publicUuid").value(id.toString()))
					.andExpect(jsonPath("$.data.status").value("PENDING"));
		}

		@Test
		@DisplayName("INVALID_FILE → 422")
		void invalidFile() throws Exception {
			given(service.enqueueStudentsImport(any()))
					.willThrow(new BusinessException("INVALID_FILE", "empty"));

			MockMultipartFile file = new MockMultipartFile(
					"file", "students.xlsx",
					"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
					new byte[0]);

			mockMvc.perform(multipart(BASE).file(file)
							.with(csrf())
							.with(authentication(adminAuth())))
					.andExpect(status().isUnprocessableEntity())
					.andExpect(jsonPath("$.errors[0].code").value("INVALID_FILE"));
		}

		@Test
		@DisplayName("missing role → 403, service never invoked")
		void forbiddenWithoutRole() throws Exception {
			MockMultipartFile file = new MockMultipartFile(
					"file", "students.xlsx",
					"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
					"payload".getBytes());

			mockMvc.perform(multipart(BASE).file(file)
							.with(csrf())
							.with(authentication(plainAuth())))
					.andExpect(status().isForbidden());

			then(service).should(never()).enqueueStudentsImport(any());
		}

		@Test
		@DisplayName("anonymous → 401")
		void anonymousRejected() throws Exception {
			MockMultipartFile file = new MockMultipartFile(
					"file", "students.xlsx",
					"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
					"payload".getBytes());

			mockMvc.perform(multipart(BASE).file(file).with(csrf()))
					.andExpect(status().isUnauthorized());
		}
	}

	// ===========================================================================
	// GET /{publicUuid}
	// ===========================================================================

	@Nested
	@DisplayName("GET /v1/students/bulk-import/{publicUuid}")
	class GetStatus {

		@Test
		@DisplayName("happy path — 200 with the current state")
		void happyPath() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.getJob(id)).willReturn(stubJob(id, BulkImportStatus.COMPLETED));

			mockMvc.perform(get(BASE + "/" + id).with(authentication(adminAuth())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status").value("COMPLETED"));
		}

		@Test
		@DisplayName("unknown publicUuid → 404 RESOURCE_NOT_FOUND")
		void notFound() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.getJob(id))
					.willThrow(new ResourceNotFoundException("BulkImportJob", id));

			mockMvc.perform(get(BASE + "/" + id).with(authentication(adminAuth())))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_FOUND"));
		}

		@Test
		@DisplayName("missing role → 403")
		void forbiddenWithoutRole() throws Exception {
			UUID id = UUID.randomUUID();
			mockMvc.perform(get(BASE + "/" + id).with(authentication(plainAuth())))
					.andExpect(status().isForbidden());
		}
	}
}
