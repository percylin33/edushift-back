package com.edushift.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.edushift.infrastructure.multitenancy.MultiTenancyConfiguration;
import com.edushift.infrastructure.multitenancy.TenantInterceptor;
import com.edushift.modules.auth.security.JwtAuthenticationFilter;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unit test (no Docker / no Testcontainers) that pins the response-header
 * contract configured in
 * {@link SecurityConfig#securityFilterChain(HttpSecurity)}.
 *
 * <h3>Why this exists alongside {@code SecurityHeadersIT}</h3>
 * The IT variant needs a Postgres container and is the gold standard for
 * end-to-end coverage, but the project has historically lacked Docker in
 * the dev sandbox (see {@code DEBT-7A-28}). This unit test lets the
 * contract be verified locally and in CI even without containers.
 *
 * <p>It follows the same {@code @WebMvcTest} + {@code excludeFilters}
 * pattern that the existing {@code CompetencyControllerTest} uses to
 * isolate the controller slice from the multi-tenant plumbing.
 *
 * <h3>What is verified</h3>
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff}</li>
 *   <li>{@code X-Frame-Options: DENY}</li>
 *   <li>{@code Referrer-Policy: strict-origin-when-cross-origin}</li>
 *   <li>{@code Permissions-Policy} contains {@code geolocation=()} and
 *       {@code microphone=()}</li>
 *   <li>{@code Content-Security-Policy} contains
 *       {@code frame-ancestors 'none'} and {@code default-src 'self'}</li>
 *   <li>{@code Cross-Origin-Opener-Policy: same-origin}</li>
 *   <li>{@code Cross-Origin-Embedder-Policy: require-corp}</li>
 *   <li>{@code Cross-Origin-Resource-Policy: same-origin}</li>
 * </ul>
 *
 * <h3>What is NOT verified</h3>
 * <ul>
 *   <li>{@code Strict-Transport-Security}: only emitted when the request is
 *       served over HTTPS or {@code server.forward-headers-strategy=native}
 *       is enabled. Verifying it manually against a TLS-terminating proxy
 *       is documented in {@code docs/architecture/security.md}.</li>
 * </ul>
 */
@WebMvcTest(
		controllers = SecurityHeadersTest.DummyController.class,
		excludeFilters = @ComponentScan.Filter(
				type = FilterType.ASSIGNABLE_TYPE,
				classes = {MultiTenancyConfiguration.class, TenantInterceptor.class}))
@Import({SecurityConfig.class, GlobalExceptionHandler.class, com.edushift.test.EdushiftWebMvcTestConfig.class})
class SecurityHeadersTest {

	@RestController
	static class DummyController {
		@GetMapping("/v1/_test/public")
		String publicEndpoint() {
			return "ok";
		}

		@GetMapping("/v1/_test/private")
		String privateEndpoint() {
			return "ok";
		}
	}

	@MockitoBean
	private JwtAuthenticationFilter jwtAuthenticationFilter;

	@MockitoBean
	private JwtService jwtService;

	@org.springframework.beans.factory.annotation.Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("Public endpoint emits the full set of defense-in-depth headers")
	void publicEndpointEmitsSecurityHeaders() throws Exception {
		mockMvc.perform(get("/v1/_test/public"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().string("X-Frame-Options", "DENY"))
				.andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
				.andExpect(header().exists("Permissions-Policy"))
				.andExpect(header().string("Permissions-Policy",
						org.hamcrest.Matchers.containsString("geolocation=()")))
				.andExpect(header().string("Permissions-Policy",
						org.hamcrest.Matchers.containsString("microphone=()")))
				.andExpect(header().string("Permissions-Policy",
						org.hamcrest.Matchers.containsString("payment=()")))
				.andExpect(header().exists("Content-Security-Policy"))
				.andExpect(header().string("Content-Security-Policy",
						org.hamcrest.Matchers.containsString("default-src 'self'")))
				.andExpect(header().string("Content-Security-Policy",
						org.hamcrest.Matchers.containsString("frame-ancestors 'none'")))
				.andExpect(header().string("Content-Security-Policy",
						org.hamcrest.Matchers.containsString("base-uri 'self'")))
				.andExpect(header().string("Cross-Origin-Opener-Policy", "same-origin"))
				.andExpect(header().string("Cross-Origin-Embedder-Policy", "require-corp"))
				.andExpect(header().string("Cross-Origin-Resource-Policy", "same-origin"));
	}

	@Test
	@DisplayName("HSTS header is NOT emitted on plain HTTP (documented limitation)")
	void hstsNotEmittedOnPlainHttp() throws Exception {
		// Strict-Transport-Security is intentionally skipped by Spring
		// Security when the request is not served over HTTPS. The header
		// is verified manually against a TLS-terminating proxy in
		// docs/architecture/security.md.
		mockMvc.perform(get("/v1/_test/public"))
				.andExpect(header().doesNotExist("Strict-Transport-Security"));
	}
}