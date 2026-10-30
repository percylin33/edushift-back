package com.edushift.modules.admin.prep;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.config.SecurityConfig;
import com.edushift.config.WebConfiguration;
import com.edushift.infrastructure.multitenancy.MultiTenancyConfiguration;
import com.edushift.infrastructure.multitenancy.TenantInterceptor;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.shared.exception.GlobalExceptionHandler;
import com.edushift.test.AbstractControllerTest;
import com.edushift.test.EdushiftWebMvcTestConfig;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
		controllers = AdminPrepPackageController.class,
		excludeFilters = @ComponentScan.Filter(
				type = FilterType.ASSIGNABLE_TYPE,
				classes = {MultiTenancyConfiguration.class, TenantInterceptor.class}))
@Import({
		GlobalExceptionHandler.class,
		SecurityConfig.class,
		WebConfiguration.class,
		EdushiftWebMvcTestConfig.class,
})
class AdminPrepPackageControllerTest extends AbstractControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PrepPackageService prepPackageService;

	@MockitoBean
	private JwtService jwtService;

	@Test
	@DisplayName("SUPER_ADMIN downloads ZIP with Content-Disposition")
	void superAdminDownloadsZip() throws Exception {
		byte[] body = new byte[] {0x50, 0x4B, 0x03, 0x04};
		given(prepPackageService.build(eq("Colegio Demo"), eq(PrepPackageVariant.PILOT)))
				.willReturn(new PrepPackageDownload(body, "prep-datos-colegio-demo.zip"));

		mockMvc.perform(get(url("/admin/prep-package"))
						.param("schoolName", "Colegio Demo")
						.param("variant", "PILOT")
						.with(auth(superAdmin()))
						.with(csrf()))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "application/zip"))
				.andExpect(header().string(
						"Content-Disposition",
						Matchers.containsString("prep-datos-colegio-demo.zip")));
	}

	@Test
	@DisplayName("TENANT_ADMIN is forbidden")
	void tenantAdminForbidden() throws Exception {
		mockMvc.perform(get(url("/admin/prep-package"))
						.with(auth(tenantAdmin()))
						.with(csrf()))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("Anonymous is unauthorized")
	void anonymousUnauthorized() throws Exception {
		mockMvc.perform(get(url("/admin/prep-package")).with(csrf()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("Invalid variant returns 400")
	void invalidVariantBadRequest() throws Exception {
		mockMvc.perform(get(url("/admin/prep-package"))
						.param("variant", "BETA")
						.with(auth(superAdmin()))
						.with(csrf()))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Defaults schoolName and PRODUCTION variant")
	void defaultsWork() throws Exception {
		given(prepPackageService.build(any(), eq(PrepPackageVariant.PRODUCTION)))
				.willReturn(new PrepPackageDownload(new byte[] {1, 2}, "prep-datos-tu-institucion.zip"));

		mockMvc.perform(get(url("/admin/prep-package"))
						.with(auth(superAdmin()))
						.with(csrf()))
				.andExpect(status().isOk());
	}
}
