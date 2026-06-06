package com.edushift.modules.academic.period.controller;

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
import com.edushift.modules.academic.period.dto.AcademicPeriodListItem;
import com.edushift.modules.academic.period.dto.AcademicPeriodResponse;
import com.edushift.modules.academic.period.dto.CreateAcademicPeriodRequest;
import com.edushift.modules.academic.period.entity.PeriodType;
import com.edushift.modules.academic.period.service.AcademicPeriodService;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.GlobalExceptionHandler;
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
		controllers = AcademicPeriodController.class,
		excludeFilters = @ComponentScan.Filter(
				type = FilterType.ASSIGNABLE_TYPE,
				classes = {MultiTenancyConfiguration.class, TenantInterceptor.class}))
@Import({
		GlobalExceptionHandler.class,
		com.edushift.config.SecurityConfig.class,
		com.edushift.config.WebConfiguration.class
})
class AcademicPeriodControllerTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;

	@MockitoBean private AcademicPeriodService service;
	@MockitoBean private JwtService jwtService;

	private static final String BASE = "/v1/academic/periods";

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

	private AcademicPeriodResponse stubResponse() {
		return new AcademicPeriodResponse(
				UUID.randomUUID(), UUID.randomUUID(), "2026",
				PeriodType.BIMESTRE, 1, "I Bimestre",
				LocalDate.parse("2026-03-01"), LocalDate.parse("2026-05-15"),
				Instant.parse("2026-01-01T00:00:00Z"),
				Instant.parse("2026-01-01T00:00:00Z"));
	}

	private AcademicPeriodListItem stubItem() {
		return new AcademicPeriodListItem(
				UUID.randomUUID(), UUID.randomUUID(),
				PeriodType.BIMESTRE, 1, "I Bimestre",
				LocalDate.parse("2026-03-01"), LocalDate.parse("2026-05-15"));
	}

	// =========================================================================
	// GET /
	// =========================================================================

	@Nested
	@DisplayName("GET /v1/academic/periods")
	class List_ {

		@Test
		@DisplayName("TENANT_ADMIN — 200 with array")
		void happyPath() throws Exception {
			given(service.listPeriods(any(), any())).willReturn(List.of(stubItem()));

			mockMvc.perform(get(BASE).with(authentication(adminAuth())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[0].periodType").value("BIMESTRE"));
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
		@DisplayName("?periodType narrows result")
		void filterByType() throws Exception {
			given(service.listPeriods(any(), any())).willReturn(List.of());

			mockMvc.perform(get(BASE)
							.param("periodType", "TRIMESTRE")
							.with(authentication(adminAuth())))
					.andExpect(status().isOk());
		}
	}

	// =========================================================================
	// POST /
	// =========================================================================

	@Nested
	@DisplayName("POST /v1/academic/periods")
	class Create {

		@Test
		@DisplayName("happy path — 201")
		void happyPath() throws Exception {
			given(service.createPeriod(any())).willReturn(stubResponse());

			mockMvc.perform(post(BASE)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new CreateAcademicPeriodRequest(
									UUID.randomUUID(), PeriodType.BIMESTRE, 1, null,
									LocalDate.parse("2026-03-01"),
									LocalDate.parse("2026-05-15")))))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.name").value("I Bimestre"));
		}

		@Test
		@DisplayName("missing periodType — 400")
		void missingType() throws Exception {
			String body = """
					{
					  "academicYearPublicUuid": "%s",
					  "ordinal": 1,
					  "startDate": "2026-03-01",
					  "endDate": "2026-05-15"
					}""".formatted(UUID.randomUUID());

			mockMvc.perform(post(BASE)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(body))
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("ordinal 0 → 400")
		void invalidOrdinal() throws Exception {
			mockMvc.perform(post(BASE)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new CreateAcademicPeriodRequest(
									UUID.randomUUID(), PeriodType.BIMESTRE, 0, null,
									LocalDate.parse("2026-03-01"),
									LocalDate.parse("2026-05-15")))))
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("ordinal gap → 422")
		void ordinalGap() throws Exception {
			given(service.createPeriod(any()))
					.willThrow(new BusinessException("PERIOD_ORDINAL_GAP",
							"Cannot create ordinal 4 — next available is 3"));

			mockMvc.perform(post(BASE)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new CreateAcademicPeriodRequest(
									UUID.randomUUID(), PeriodType.BIMESTRE, 4, null,
									LocalDate.parse("2026-08-01"),
									LocalDate.parse("2026-09-30")))))
					.andExpect(status().isUnprocessableEntity())
					.andExpect(jsonPath("$.errors[0].code").value("PERIOD_ORDINAL_GAP"));
		}

		@Test
		@DisplayName("date overlap → 409")
		void overlap() throws Exception {
			given(service.createPeriod(any()))
					.willThrow(new ConflictException("PERIOD_DATE_OVERLAP", "overlap"));

			mockMvc.perform(post(BASE)
							.with(csrf())
							.with(authentication(adminAuth()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new CreateAcademicPeriodRequest(
									UUID.randomUUID(), PeriodType.BIMESTRE, 1, null,
									LocalDate.parse("2026-03-01"),
									LocalDate.parse("2026-05-15")))))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code").value("PERIOD_DATE_OVERLAP"));
		}
	}

	// =========================================================================
	// DELETE
	// =========================================================================

	@Nested
	@DisplayName("DELETE /v1/academic/periods/{id}")
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
		@DisplayName("middle ordinal — 409")
		void middleOrdinal() throws Exception {
			UUID id = UUID.randomUUID();
			org.mockito.Mockito.doThrow(
							new ConflictException("PERIOD_NOT_LAST_ORDINAL", "not last"))
					.when(service).deletePeriod(id);

			mockMvc.perform(delete(BASE + "/" + id)
							.with(csrf())
							.with(authentication(adminAuth())))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.errors[0].code").value("PERIOD_NOT_LAST_ORDINAL"));
		}
	}
}
