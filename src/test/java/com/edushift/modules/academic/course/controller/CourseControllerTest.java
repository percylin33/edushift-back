package com.edushift.modules.academic.course.controller;

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
import com.edushift.modules.academic.course.dto.CourseListItem;
import com.edushift.modules.academic.course.dto.CourseResponse;
import com.edushift.modules.academic.course.dto.CreateCourseRequest;
import com.edushift.modules.academic.course.dto.UpdateCourseLevelsRequest;
import com.edushift.modules.academic.course.service.CourseService;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.shared.exception.BusinessException;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
		controllers = CourseController.class,
		excludeFilters = @ComponentScan.Filter(
				type = FilterType.ASSIGNABLE_TYPE,
				classes = {MultiTenancyConfiguration.class, TenantInterceptor.class}))
@Import({
		GlobalExceptionHandler.class,
		com.edushift.config.SecurityConfig.class,
		com.edushift.config.WebConfiguration.class
})
class CourseControllerTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;

	@MockitoBean private CourseService service;
	@MockitoBean private JwtService jwtService;

	private static final String BASE = "/v1/academic/courses";

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

	private CourseResponse stubResponse(String code, String name) {
		return new CourseResponse(
				UUID.randomUUID(), code, name, null, 4, 5, true,
				List.of(new CourseResponse.CourseLevelRef(
						UUID.randomUUID(), "PRIMARIA", "Primaria", 2)),
				Instant.parse("2026-01-01T00:00:00Z"),
				Instant.parse("2026-01-01T00:00:00Z"));
	}

	private CourseListItem stubItem(String code, String name) {
		return new CourseListItem(
				UUID.randomUUID(), code, name, 4, 5, true,
				List.of(new CourseResponse.CourseLevelRef(
						UUID.randomUUID(), "PRIMARIA", "Primaria", 2)));
	}

	// =========================================================================
	// GET /
	// =========================================================================

	@Nested
	@DisplayName("GET /v1/academic/courses")
	class List_ {

		@Test
		@DisplayName("TENANT_ADMIN — 200 with array")
		void happyPath() throws Exception {
			given(service.listCourses(any(), any()))
					.willReturn(List.of(stubItem("MAT", "Matematica"), stubItem("COMU", "Comunicacion")));

			mockMvc.perform(get(BASE).with(authentication(adminAuth())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[0].code").value("MAT"))
					.andExpect(jsonPath("$[1].code").value("COMU"));
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
		@DisplayName("?levelId forwarded to service")
		void filterByLevel() throws Exception {
			UUID levelId = UUID.randomUUID();
			given(service.listCourses(eq(levelId), any())).willReturn(List.of());

			mockMvc.perform(get(BASE)
							.param("levelId", levelId.toString())
							.with(authentication(adminAuth())))
					.andExpect(status().isOk());
		}
	}

	// =========================================================================
	// POST /
	// =========================================================================

	@Nested
	@DisplayName("POST /v1/academic/courses")
	class Create {

		@Test
		@DisplayName("happy path — 201")
		void happyPath() throws Exception {
			given(service.createCourse(any())).willReturn(stubResponse("MAT", "Matematica"));

			mockMvc.perform(post(BASE)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new CreateCourseRequest(
									"MAT", "Matematica", null, 4, 5, true,
									List.of(UUID.randomUUID())))))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.code").value("MAT"));
		}

		@Test
		@DisplayName("empty levelPublicUuids in body — 400 (bean validation)")
		void emptyLevels() throws Exception {
			mockMvc.perform(post(BASE)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new CreateCourseRequest(
									"MAT", "Matematica", null, null, null, null, List.of()))))
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("invalid code (lowercase + space) — 400")
		void invalidCode() throws Exception {
			mockMvc.perform(post(BASE)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new CreateCourseRequest(
									"bad code!", "Matematica", null, null, null, null,
									List.of(UUID.randomUUID())))))
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("code taken — 409")
		void codeTaken() throws Exception {
			given(service.createCourse(any()))
					.willThrow(new ConflictException("COURSE_CODE_TAKEN", "taken"));

			mockMvc.perform(post(BASE)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new CreateCourseRequest(
									"MAT", "Matematica", null, null, null, null,
									List.of(UUID.randomUUID())))))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code").value("COURSE_CODE_TAKEN"));
		}

		@Test
		@DisplayName("dedup leaves zero levels at service → 422")
		void serviceDedupRejection() throws Exception {
			given(service.createCourse(any()))
					.willThrow(new BusinessException("COURSE_NEEDS_AT_LEAST_ONE_LEVEL",
							"need at least one level"));

			mockMvc.perform(post(BASE)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new CreateCourseRequest(
									"MAT", "Matematica", null, null, null, null,
									List.of(UUID.randomUUID())))))
					.andExpect(status().isUnprocessableEntity())
					.andExpect(jsonPath("$.errors[0].code").value("COURSE_NEEDS_AT_LEAST_ONE_LEVEL"));
		}
	}

	// =========================================================================
	// POST /{id}/levels
	// =========================================================================

	@Nested
	@DisplayName("POST /v1/academic/courses/{id}/levels")
	class ReplaceLevels {

		@Test
		@DisplayName("happy path — 200 with updated levels")
		void happyPath() throws Exception {
			UUID id = UUID.randomUUID();
			given(service.replaceLevels(eq(id), any())).willReturn(stubResponse("MAT", "Matematica"));

			mockMvc.perform(post(BASE + "/" + id + "/levels")
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(
									new UpdateCourseLevelsRequest(List.of(UUID.randomUUID())))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.levels").isArray());
		}

		@Test
		@DisplayName("empty payload — 400")
		void emptyPayload() throws Exception {
			UUID id = UUID.randomUUID();

			mockMvc.perform(post(BASE + "/" + id + "/levels")
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"levelPublicUuids\":[]}"))
					.andExpect(status().isBadRequest());
		}
	}

	// =========================================================================
	// DELETE
	// =========================================================================

	@Nested
	@DisplayName("DELETE /v1/academic/courses/{id}")
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
	}
}
