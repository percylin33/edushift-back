package com.edushift.modules.students.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.infrastructure.multitenancy.MultiTenancyConfiguration;
import com.edushift.infrastructure.multitenancy.TenantInterceptor;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.students.dto.GuardianProfileResponse;
import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.service.StudentGuardianService;
import com.edushift.shared.exception.GlobalExceptionHandler;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
		controllers = GuardianController.class,
		excludeFilters = @ComponentScan.Filter(
				type = FilterType.ASSIGNABLE_TYPE,
				classes = {MultiTenancyConfiguration.class, TenantInterceptor.class}))
@Import({
		GlobalExceptionHandler.class,
		com.edushift.config.SecurityConfig.class,
		com.edushift.config.WebConfiguration.class,
		com.edushift.test.EdushiftWebMvcTestConfig.class,
})
class GuardianControllerTest {

	@Autowired private MockMvc mockMvc;

	@MockitoBean private StudentGuardianService service;
	@MockitoBean private JwtService jwtService;
	@MockitoBean private com.edushift.shared.security.LmsRoleAuthorityMapper roleAuthorityMapper;

	private static JwtAuthenticationToken adminAuth() {
		JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
				UUID.randomUUID(), UUID.randomUUID(),
				"acme", "admin@acme.test");
		return new JwtAuthenticationToken(
				principal, "fake.token",
				List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")));
	}

	@Test
	@DisplayName("GET /guardians/by-document — 200 with profile")
	void lookupByDocumentOk() throws Exception {
		UUID guardianId = UUID.randomUUID();
		given(service.lookupByDocument(DocumentType.DNI, "87654321"))
				.willReturn(new GuardianProfileResponse(
						guardianId, DocumentType.DNI, "87654321",
						"Percy", "Valderrama Arias", "Percy Valderrama Arias",
						"percy@test.com", "+51 999", "Engineer", null));

		mockMvc.perform(get("/v1/guardians/by-document")
						.param("documentType", "DNI")
						.param("documentNumber", "87654321")
						.with(authentication(adminAuth())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.firstName").value("Percy"))
				.andExpect(jsonPath("$.data.lastName").value("Valderrama Arias"));
	}

	@Test
	@DisplayName("GET /guardians/by-document — 404 when unknown")
	void lookupByDocumentNotFound() throws Exception {
		given(service.lookupByDocument(DocumentType.DNI, "99999999"))
				.willThrow(new ResourceNotFoundException("RESOURCE_NOT_FOUND", "not found"));

		mockMvc.perform(get("/v1/guardians/by-document")
						.param("documentType", "DNI")
						.param("documentNumber", "99999999")
						.with(authentication(adminAuth())))
				.andExpect(status().isNotFound());
	}
}
