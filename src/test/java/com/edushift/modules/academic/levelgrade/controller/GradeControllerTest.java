package com.edushift.modules.academic.levelgrade.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.infrastructure.multitenancy.MultiTenancyConfiguration;
import com.edushift.infrastructure.multitenancy.TenantInterceptor;
import com.edushift.modules.academic.levelgrade.dto.CreateGradeRequest;
import com.edushift.modules.academic.levelgrade.dto.GradeReorderRequest;
import com.edushift.modules.academic.levelgrade.dto.GradeResponse;
import com.edushift.modules.academic.levelgrade.service.GradeService;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.GlobalExceptionHandler;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
		controllers = GradeController.class,
		excludeFilters = @ComponentScan.Filter(
				type = FilterType.ASSIGNABLE_TYPE,
				classes = {MultiTenancyConfiguration.class, TenantInterceptor.class}))
@Import({
		GlobalExceptionHandler.class,
		com.edushift.config.SecurityConfig.class,
		com.edushift.config.WebConfiguration.class,
		com.edushift.test.EdushiftWebMvcTestConfig.class,
})
class GradeControllerTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;

	@MockitoBean private GradeService service;
	@MockitoBean private JwtService jwtService;
	@MockitoBean private com.edushift.shared.security.LmsRoleAuthorityMapper roleAuthorityMapper;

	private static JwtAuthenticationToken adminAuth() {
		JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
				UUID.randomUUID(), UUID.randomUUID(), "acme", "admin@acme.test");
		return new JwtAuthenticationToken(principal, "fake.token",
				List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")));
	}

	private GradeResponse stub(UUID levelUuid, String name, int ordinal) {
		return new GradeResponse(UUID.randomUUID(), levelUuid, name, ordinal,
				Instant.parse("2026-01-01T00:00:00Z"),
				Instant.parse("2026-01-01T00:00:00Z"));
	}

	@Nested
	@DisplayName("POST /v1/academic/levels/{levelUuid}/grades")
	class Create {

		@Test
		@DisplayName("happy path — 201")
		void happyPath() throws Exception {
			UUID levelUuid = UUID.randomUUID();
			given(service.createGrade(eq(levelUuid), any())).willReturn(stub(levelUuid, "1ro", 1));

			mockMvc.perform(post("/v1/academic/levels/" + levelUuid + "/grades")
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new CreateGradeRequest("1ro", 1))))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.name").value("1ro"));
		}

		@Test
		@DisplayName("ordinal taken — 409")
		void ordinalTaken() throws Exception {
			UUID levelUuid = UUID.randomUUID();
			given(service.createGrade(eq(levelUuid), any()))
					.willThrow(new ConflictException("GRADE_ORDINAL_TAKEN", "taken"));

			mockMvc.perform(post("/v1/academic/levels/" + levelUuid + "/grades")
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new CreateGradeRequest("1ro", 1))))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code").value("GRADE_ORDINAL_TAKEN"));
		}
	}

	@Nested
	@DisplayName("PATCH /v1/academic/levels/{levelUuid}/grades/reorder")
	class Reorder {

		@Test
		@DisplayName("happy path — 200 with reordered list")
		void happyPath() throws Exception {
			UUID levelUuid = UUID.randomUUID();
			given(service.reorderGrades(eq(levelUuid), any())).willReturn(List.of(
					stub(levelUuid, "Algo", 1),
					stub(levelUuid, "Otro", 2)
			));

			GradeReorderRequest body = new GradeReorderRequest(List.of(
					new GradeReorderRequest.Item(UUID.randomUUID(), 2),
					new GradeReorderRequest.Item(UUID.randomUUID(), 1)
			));

			mockMvc.perform(patch("/v1/academic/levels/" + levelUuid + "/grades/reorder")
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(body)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$").isArray());
		}

		@Test
		@DisplayName("empty items — 400")
		void emptyItems() throws Exception {
			UUID levelUuid = UUID.randomUUID();
			GradeReorderRequest body = new GradeReorderRequest(List.of());

			mockMvc.perform(patch("/v1/academic/levels/" + levelUuid + "/grades/reorder")
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(body)))
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("payload contains foreign grade — 409 GRADE_REORDER_INVALID")
		void foreignGrade() throws Exception {
			UUID levelUuid = UUID.randomUUID();
			given(service.reorderGrades(eq(levelUuid), any()))
					.willThrow(new ConflictException("GRADE_REORDER_INVALID", "foreign"));

			GradeReorderRequest body = new GradeReorderRequest(List.of(
					new GradeReorderRequest.Item(UUID.randomUUID(), 1)
			));

			mockMvc.perform(patch("/v1/academic/levels/" + levelUuid + "/grades/reorder")
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(body)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code").value("GRADE_REORDER_INVALID"));
		}
	}
}
