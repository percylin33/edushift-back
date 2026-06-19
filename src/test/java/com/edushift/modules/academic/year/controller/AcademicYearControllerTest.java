package com.edushift.modules.academic.year.controller;

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
import com.edushift.modules.academic.year.dto.AcademicYearListItem;
import com.edushift.modules.academic.year.dto.AcademicYearResponse;
import com.edushift.modules.academic.year.dto.CreateAcademicYearRequest;
import com.edushift.modules.academic.year.dto.UpdateAcademicYearRequest;
import com.edushift.modules.academic.year.entity.AcademicYearStatus;
import com.edushift.modules.academic.year.service.AcademicYearService;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.auth.service.JwtService;
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
		controllers = AcademicYearController.class,
		excludeFilters = @ComponentScan.Filter(
				type = FilterType.ASSIGNABLE_TYPE,
				classes = {MultiTenancyConfiguration.class, TenantInterceptor.class}))
@Import({
		GlobalExceptionHandler.class,
		com.edushift.config.SecurityConfig.class,
		com.edushift.config.WebConfiguration.class
})
class AcademicYearControllerTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;

	@MockitoBean private AcademicYearService service;
	@MockitoBean private JwtService jwtService;
	@MockitoBean private com.edushift.shared.security.LmsRoleAuthorityMapper roleAuthorityMapper;

private static final String BASE = "/v1/academic/years";

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

	private AcademicYearResponse stubResponse(UUID publicUuid, AcademicYearStatus status) {
		return new AcademicYearResponse(
				publicUuid, "2026", status,
				LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 15),
				Instant.parse("2026-01-01T00:00:00Z"),
				Instant.parse("2026-06-01T00:00:00Z"));
	}

	private AcademicYearListItem stubListItem(String name, AcademicYearStatus status) {
		return new AcademicYearListItem(
				UUID.randomUUID(), name, status,
				LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 15));
	}

	// ===========================================================================
	// GET / (list)
	// ===========================================================================

	@Nested
	@DisplayName("GET /v1/academic/years — list")
	class ListYears {

		@Test
		@DisplayName("TENANT_ADMIN → 200 with array")
		void happyPath() throws Exception {
			given(service.listYears(any())).willReturn(List.of(
					stubListItem("2026", AcademicYearStatus.ACTIVE),
					stubListItem("2025", AcademicYearStatus.CLOSED)));

			mockMvc.perform(get(BASE).with(authentication(adminAuth())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[0].name").value("2026"))
					.andExpect(jsonPath("$[0].status").value("ACTIVE"));
		}

		@Test
		@DisplayName("status filter pasthrough")
		void statusFilter() throws Exception {
			given(service.listYears(eq(AcademicYearStatus.ACTIVE)))
					.willReturn(List.of(stubListItem("2026", AcademicYearStatus.ACTIVE)));

			mockMvc.perform(get(BASE + "?status=ACTIVE").with(authentication(adminAuth())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[0].status").value("ACTIVE"));
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
	@DisplayName("GET /v1/academic/years/{publicUuid}")
	class GetOne {

		@Test
		@DisplayName("happy path — 200 with envelope")
		void happyPath() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.getYear(id)).willReturn(stubResponse(id, AcademicYearStatus.PLANNING));

			mockMvc.perform(get(BASE + "/" + id).with(authentication(adminAuth())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success").value(true))
					.andExpect(jsonPath("$.data.publicUuid").value(id.toString()))
					.andExpect(jsonPath("$.data.name").value("2026"));
		}

		@Test
		@DisplayName("unknown publicUuid → 404 RESOURCE_NOT_FOUND")
		void notFound() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.getYear(id))
					.willThrow(new ResourceNotFoundException("AcademicYear", id));

			mockMvc.perform(get(BASE + "/" + id).with(authentication(adminAuth())))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_FOUND"));
		}
	}

	// ===========================================================================
	// POST /
	// ===========================================================================

	@Nested
	@DisplayName("POST /v1/academic/years")
	class Create {

		@Test
		@DisplayName("TENANT_ADMIN + valid body → 201 with PLANNING status")
		void happyPath() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.createYear(any(CreateAcademicYearRequest.class)))
					.willReturn(stubResponse(id, AcademicYearStatus.PLANNING));

			CreateAcademicYearRequest body = new CreateAcademicYearRequest(
					"2026", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 15));

			mockMvc.perform(post(BASE)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(body)))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.publicUuid").value(id.toString()))
					.andExpect(jsonPath("$.data.status").value("PLANNING"));
		}

		@Test
		@DisplayName("name already taken → 409 ACADEMIC_YEAR_NAME_TAKEN")
		void nameTaken() throws Exception {
			given(service.createYear(any()))
					.willThrow(new ConflictException("ACADEMIC_YEAR_NAME_TAKEN", "taken"));

			CreateAcademicYearRequest body = new CreateAcademicYearRequest(
					"2026", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 15));

			mockMvc.perform(post(BASE)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(body)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code").value("ACADEMIC_YEAR_NAME_TAKEN"));
		}

		@Test
		@DisplayName("missing required field → 400 (validation, service never invoked)")
		void missingFieldRejected() throws Exception {
			CreateAcademicYearRequest body = new CreateAcademicYearRequest(
					null, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 15));

			mockMvc.perform(post(BASE)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(body)))
					.andExpect(status().isBadRequest());

			then(service).should(never()).createYear(any());
		}

		@Test
		@DisplayName("missing role → 403")
		void forbiddenWithoutRole() throws Exception {
			CreateAcademicYearRequest body = new CreateAcademicYearRequest(
					"2026", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 15));

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
	@DisplayName("PUT /v1/academic/years/{publicUuid}")
	class Update {

		@Test
		@DisplayName("happy path — 200")
		void happyPath() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.updateYear(eq(id), any(UpdateAcademicYearRequest.class)))
					.willReturn(stubResponse(id, AcademicYearStatus.PLANNING));

			UpdateAcademicYearRequest patch = new UpdateAcademicYearRequest(
					"Año 2026", null, null);

			mockMvc.perform(put(BASE + "/" + id)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(patch)))
					.andExpect(status().isOk());
		}

		@Test
		@DisplayName("CLOSED year → 409 ACADEMIC_YEAR_LOCKED")
		void locked() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.updateYear(eq(id), any()))
					.willThrow(new ConflictException("ACADEMIC_YEAR_LOCKED", "read-only"));

			UpdateAcademicYearRequest patch = new UpdateAcademicYearRequest(
					"any", null, null);

			mockMvc.perform(put(BASE + "/" + id)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(json(patch)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code").value("ACADEMIC_YEAR_LOCKED"));
		}
	}

	// ===========================================================================
	// POST /{publicUuid}/activate
	// ===========================================================================

	@Nested
	@DisplayName("POST /v1/academic/years/{publicUuid}/activate")
	class Activate {

		@Test
		@DisplayName("TENANT_ADMIN — 200 with ACTIVE status")
		void happyPath() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.activateYear(id)).willReturn(stubResponse(id, AcademicYearStatus.ACTIVE));

			mockMvc.perform(post(BASE + "/" + id + "/activate")
							.with(csrf())
							.with(authentication(adminAuth())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status").value("ACTIVE"));
		}

		@Test
		@DisplayName("CLOSED target → 409 ACADEMIC_YEAR_NOT_ACTIVATABLE")
		void notActivatable() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.activateYear(id))
					.willThrow(new ConflictException("ACADEMIC_YEAR_NOT_ACTIVATABLE", "CLOSED"));

			mockMvc.perform(post(BASE + "/" + id + "/activate")
							.with(csrf())
							.with(authentication(adminAuth())))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code").value("ACADEMIC_YEAR_NOT_ACTIVATABLE"));
		}

		@Test
		@DisplayName("unknown id → 404")
		void notFound() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.activateYear(id))
					.willThrow(new ResourceNotFoundException("AcademicYear", id));

			mockMvc.perform(post(BASE + "/" + id + "/activate")
							.with(csrf())
							.with(authentication(adminAuth())))
					.andExpect(status().isNotFound());
		}

		@Test
		@DisplayName("missing role → 403")
		void forbiddenWithoutRole() throws Exception {
			UUID id = UUID.randomUUID();

			mockMvc.perform(post(BASE + "/" + id + "/activate")
							.with(csrf())
							.with(authentication(plainAuth())))
					.andExpect(status().isForbidden());

			then(service).should(never()).activateYear(any());
		}
	}

	// ===========================================================================
	// DELETE /{publicUuid}
	// ===========================================================================

	@Nested
	@DisplayName("DELETE /v1/academic/years/{publicUuid}")
	class Delete {

		@Test
		@DisplayName("happy path — 204")
		void happyPath() throws Exception {
			UUID id = UUID.randomUUID();

			mockMvc.perform(delete(BASE + "/" + id)
							.with(csrf())
							.with(authentication(adminAuth())))
					.andExpect(status().isNoContent());

			then(service).should().deleteYear(id);
		}

		@Test
		@DisplayName("ACTIVE year → 409 ACADEMIC_YEAR_IN_USE")
		void inUse() throws Exception {
			UUID id = UUID.randomUUID();
			willThrow(new ConflictException("ACADEMIC_YEAR_IN_USE", "active"))
					.given(service).deleteYear(id);

			mockMvc.perform(delete(BASE + "/" + id)
							.with(csrf())
							.with(authentication(adminAuth())))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code").value("ACADEMIC_YEAR_IN_USE"));
		}

		@Test
		@DisplayName("missing role → 403, service never invoked")
		void forbiddenWithoutRole() throws Exception {
			UUID id = UUID.randomUUID();

			mockMvc.perform(delete(BASE + "/" + id)
							.with(csrf())
							.with(authentication(plainAuth())))
					.andExpect(status().isForbidden());

			then(service).should(never()).deleteYear(any());
		}
	}
}
