package com.edushift.modules.academic.levelgrade.controller;

import static org.mockito.ArgumentMatchers.any;
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
import com.edushift.modules.academic.levelgrade.dto.AcademicLevelResponse;
import com.edushift.modules.academic.levelgrade.dto.CreateAcademicLevelRequest;
import com.edushift.modules.academic.levelgrade.service.AcademicLevelService;
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
		controllers = AcademicLevelController.class,
		excludeFilters = @ComponentScan.Filter(
				type = FilterType.ASSIGNABLE_TYPE,
				classes = {MultiTenancyConfiguration.class, TenantInterceptor.class}))
@Import({
		GlobalExceptionHandler.class,
		com.edushift.config.SecurityConfig.class,
		com.edushift.config.WebConfiguration.class
})
class AcademicLevelControllerTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;

	@MockitoBean private AcademicLevelService service;
	@MockitoBean private JwtService jwtService;

	private static final String BASE = "/v1/academic/levels";

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

	private AcademicLevelResponse stub(String code) {
		return new AcademicLevelResponse(
				UUID.randomUUID(), code, code, 1, List.of(),
				Instant.parse("2026-01-01T00:00:00Z"),
				Instant.parse("2026-01-01T00:00:00Z"));
	}

	@Nested
	@DisplayName("GET /v1/academic/levels")
	class List_ {

		@Test
		@DisplayName("TENANT_ADMIN — 200 with array")
		void happyPath() throws Exception {
			given(service.listLevels()).willReturn(List.of(stub("INICIAL"), stub("PRIMARIA")));

			mockMvc.perform(get(BASE).with(authentication(adminAuth())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[0].code").value("INICIAL"))
					.andExpect(jsonPath("$[1].code").value("PRIMARIA"));
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
	}

	@Nested
	@DisplayName("POST /v1/academic/levels")
	class Create {

		@Test
		@DisplayName("happy path — 201")
		void happyPath() throws Exception {
			given(service.createLevel(any())).willReturn(stub("IGCSE"));

			mockMvc.perform(post(BASE)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(
									new CreateAcademicLevelRequest("IGCSE", "IGCSE", 4))))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.code").value("IGCSE"));
		}

		@Test
		@DisplayName("invalid code (lowercase + spaces) — 400")
		void invalidCode() throws Exception {
			mockMvc.perform(post(BASE)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(
									new CreateAcademicLevelRequest("invalid code!", "name", 1))))
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("code taken — 409")
		void codeTaken() throws Exception {
			given(service.createLevel(any()))
					.willThrow(new ConflictException("LEVEL_CODE_TAKEN", "taken"));

			mockMvc.perform(post(BASE)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(
									new CreateAcademicLevelRequest("IGCSE", "IGCSE", 4))))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code").value("LEVEL_CODE_TAKEN"));
		}
	}

	@Nested
	@DisplayName("DELETE /v1/academic/levels/{publicUuid}")
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
		@DisplayName("level has grades — 409 LEVEL_HAS_GRADES")
		void hasGrades() throws Exception {
			UUID id = UUID.randomUUID();
			org.mockito.BDDMockito.willThrow(new ConflictException("LEVEL_HAS_GRADES", "has"))
					.given(service).deleteLevel(id);

			mockMvc.perform(delete(BASE + "/" + id)
							.with(csrf())
							.with(authentication(adminAuth())))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code").value("LEVEL_HAS_GRADES"));
		}

		@Test
		@DisplayName("unknown — 404")
		void notFound() throws Exception {
			UUID id = UUID.randomUUID();
			org.mockito.BDDMockito.willThrow(new ResourceNotFoundException("AcademicLevel", id))
					.given(service).deleteLevel(id);

			mockMvc.perform(delete(BASE + "/" + id)
							.with(csrf())
							.with(authentication(adminAuth())))
					.andExpect(status().isNotFound());
		}
	}
}
