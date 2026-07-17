package com.edushift.modules.academic.section.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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
import com.edushift.modules.academic.section.dto.CreateSectionRequest;
import com.edushift.modules.academic.section.dto.SectionListItem;
import com.edushift.modules.academic.section.dto.SectionResponse;
import com.edushift.modules.academic.section.dto.UpdateSectionRequest;
import com.edushift.modules.academic.section.service.SectionService;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.auth.service.JwtService;
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
		controllers = SectionController.class,
		excludeFilters = @ComponentScan.Filter(
				type = FilterType.ASSIGNABLE_TYPE,
				classes = {MultiTenancyConfiguration.class, TenantInterceptor.class}))
@Import({
		GlobalExceptionHandler.class,
		com.edushift.config.SecurityConfig.class,
		com.edushift.config.WebConfiguration.class,
		com.edushift.test.EdushiftWebMvcTestConfig.class,
})
class SectionControllerTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;

	@MockitoBean private SectionService service;
	@MockitoBean private JwtService jwtService;
	@MockitoBean private com.edushift.shared.security.LmsRoleAuthorityMapper roleAuthorityMapper;

private static final String BASE = "/v1/academic/sections";

	private static JwtAuthenticationToken adminAuth() {
		JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
				UUID.randomUUID(), UUID.randomUUID(), "acme", "admin@acme.test");
		return new JwtAuthenticationToken(principal, "fake.token",
				List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")));
	}

	private static JwtAuthenticationToken plainAuth() {
		JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
				UUID.randomUUID(), UUID.randomUUID(), "acme", "user@acme.test");
		return new JwtAuthenticationToken(principal, "fake.token", List.<GrantedAuthority>of());
	}

	private SectionResponse stubResponse(String name) {
		return new SectionResponse(
				UUID.randomUUID(), UUID.randomUUID(), "2026", "ACTIVE",
				UUID.randomUUID(), "1ro Primaria", 1,
				UUID.randomUUID(), "PRIMARIA", "Primaria",
				name, 30, 1,
				Instant.parse("2026-01-01T00:00:00Z"),
				Instant.parse("2026-01-01T00:00:00Z"));
	}

	private SectionListItem stubListItem(String name) {
		return new SectionListItem(
				UUID.randomUUID(), UUID.randomUUID(), "2026", "ACTIVE",
				UUID.randomUUID(), "1ro Primaria", 1,
				UUID.randomUUID(), "PRIMARIA",
				name, 30, 1);
	}

	// =========================================================================
	// GET /
	// =========================================================================

	@Nested
	@DisplayName("GET /v1/academic/sections")
	class List_ {

		@Test
		@DisplayName("TENANT_ADMIN — 200 with array")
		void happyPath() throws Exception {
			given(service.listSections(any(), any(), any()))
					.willReturn(List.of(stubListItem("A"), stubListItem("B")));

			mockMvc.perform(get(BASE).with(authentication(adminAuth())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[0].name").value("A"))
					.andExpect(jsonPath("$[1].name").value("B"));
		}

		@Test
		@DisplayName("anonymous — 401")
		void anonymous() throws Exception {
			mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
		}

		@Test
		@DisplayName("authenticated without role — 403")
		void plain() throws Exception {
			mockMvc.perform(get(BASE).with(authentication(plainAuth())))
					.andExpect(status().isForbidden());
		}

		@Test
		@DisplayName("?academicYearId=&gradeId= forwarded to service")
		void withFilters() throws Exception {
			UUID year = UUID.randomUUID();
			UUID grade = UUID.randomUUID();
			given(service.listSections(eq(year), eq(grade), any())).willReturn(List.of());

			mockMvc.perform(get(BASE)
							.param("academicYearId", year.toString())
							.param("gradeId", grade.toString())
							.with(authentication(adminAuth())))
					.andExpect(status().isOk());
		}
	}

	// =========================================================================
	// POST /
	// =========================================================================

	@Nested
	@DisplayName("POST /v1/academic/sections")
	class Create {

		@Test
		@DisplayName("happy path — 201")
		void happyPath() throws Exception {
			given(service.createSection(any())).willReturn(stubResponse("A"));

			mockMvc.perform(post(BASE)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new CreateSectionRequest(
									UUID.randomUUID(), UUID.randomUUID(), "A", 30, 1))))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.name").value("A"));
		}

		@Test
		@DisplayName("missing required fields — 400")
		void missingFields() throws Exception {
			mockMvc.perform(post(BASE)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"name\":\"A\"}"))
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("year CLOSED → 409 ACADEMIC_YEAR_LOCKED")
		void yearLocked() throws Exception {
			given(service.createSection(any()))
					.willThrow(new ConflictException("ACADEMIC_YEAR_LOCKED", "locked"));

			mockMvc.perform(post(BASE)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new CreateSectionRequest(
									UUID.randomUUID(), UUID.randomUUID(), "A", null, null))))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code").value("ACADEMIC_YEAR_LOCKED"));
		}

		@Test
		@DisplayName("name taken → 409 SECTION_NAME_TAKEN")
		void nameTaken() throws Exception {
			given(service.createSection(any()))
					.willThrow(new ConflictException("SECTION_NAME_TAKEN", "taken"));

			mockMvc.perform(post(BASE)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new CreateSectionRequest(
									UUID.randomUUID(), UUID.randomUUID(), "A", null, null))))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code").value("SECTION_NAME_TAKEN"));
		}
	}

	// =========================================================================
	// PUT / DELETE
	// =========================================================================

	@Nested
	@DisplayName("PUT /v1/academic/sections/{id}")
	class Update {

		@Test
		@DisplayName("happy path — 200")
		void happyPath() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.updateSection(eq(id), any())).willReturn(stubResponse("A1"));

			mockMvc.perform(put(BASE + "/" + id)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(
									new UpdateSectionRequest("A1", null, null))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.name").value("A1"));
		}

		@Test
		@DisplayName("unknown — 404")
		void unknown() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.updateSection(eq(id), any()))
					.willThrow(new ResourceNotFoundException("Section", id));

			mockMvc.perform(put(BASE + "/" + id)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"name\":\"A1\"}"))
					.andExpect(status().isNotFound());
		}
	}

	@Nested
	@DisplayName("DELETE /v1/academic/sections/{id}")
	class Delete {

		@Test
		@DisplayName("happy path — 204")
		void happyPath() throws Exception {
			UUID id = UUID.randomUUID();
			mockMvc.perform(delete(BASE + "/" + id)
							.with(csrf())
							.with(authentication(adminAuth())))
					.andExpect(status().isNoContent());
		}

		@Test
		@DisplayName("year CLOSED → 409")
		void yearLocked() throws Exception {
			UUID id = UUID.randomUUID();
			org.mockito.BDDMockito.willThrow(new ConflictException("ACADEMIC_YEAR_LOCKED", "locked"))
					.given(service).deleteSection(id);

			mockMvc.perform(delete(BASE + "/" + id)
							.with(csrf())
							.with(authentication(adminAuth())))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code").value("ACADEMIC_YEAR_LOCKED"));
		}
	}
}
