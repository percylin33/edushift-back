package com.edushift.modules.admin.tenants;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.config.SecurityConfig;
import com.edushift.config.WebConfiguration;
import com.edushift.infrastructure.multitenancy.MultiTenancyConfiguration;
import com.edushift.infrastructure.multitenancy.TenantInterceptor;
import com.edushift.modules.admin.tenants.dto.AdminTenantAiSettingsResponse;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.shared.exception.GlobalExceptionHandler;
import com.edushift.test.AbstractControllerTest;
import com.edushift.test.EdushiftWebMvcTestConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
		controllers = AdminTenantAiSettingsController.class,
		excludeFilters = @ComponentScan.Filter(
				type = FilterType.ASSIGNABLE_TYPE,
				classes = {MultiTenancyConfiguration.class, TenantInterceptor.class}))
@Import({GlobalExceptionHandler.class, SecurityConfig.class, WebConfiguration.class,
		EdushiftWebMvcTestConfig.class})
class AdminTenantAiSettingsControllerTest extends AbstractControllerTest {

	private static final UUID TENANT_UUID =
			UUID.fromString("519ec200-0000-4000-8000-000000000001");

	@Autowired private MockMvc mockMvc;
	@MockitoBean private AdminTenantAiSettingsService service;
	@MockitoBean private JwtService jwtService;

	@Test
	void getSettingsReturnsStatusForSuperAdmin() throws Exception {
		given(service.get(TENANT_UUID)).willReturn(enabledResponse());

		mockMvc.perform(get(url("/admin/tenants/" + TENANT_UUID + "/ai-settings"))
					.with(auth(superAdmin())).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.enabled").value(true))
				.andExpect(jsonPath("$.data.planAvailable").value(true))
				.andExpect(jsonPath("$.data.dailyRequestLimit").value(1000));
	}

	@Test
	void updateSettingsEnablesAiForSuperAdmin() throws Exception {
		given(service.update(eq(TENANT_UUID), eq(true), eq(ANY_USER))).willReturn(enabledResponse());

		mockMvc.perform(put(url("/admin/tenants/" + TENANT_UUID + "/ai-settings"))
					.with(auth(superAdmin())).with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"enabled\":true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.enabled").value(true));

		verify(service).update(TENANT_UUID, true, ANY_USER);
	}

	@Test
	void updateSettingsRejectsMissingEnabledValue() throws Exception {
		mockMvc.perform(put(url("/admin/tenants/" + TENANT_UUID + "/ai-settings"))
					.with(auth(superAdmin())).with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"enabled\":null}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void tenantAdminCannotManagePlatformAiSettings() throws Exception {
		mockMvc.perform(put(url("/admin/tenants/" + TENANT_UUID + "/ai-settings"))
					.with(auth(tenantAdmin())).with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"enabled\":true}"))
				.andExpect(status().isForbidden());
}

	private static AdminTenantAiSettingsResponse enabledResponse() {
		return new AdminTenantAiSettingsResponse(true, true, true, "ENTERPRISE",
				1000, 5_000_000L, null);
	}
}
