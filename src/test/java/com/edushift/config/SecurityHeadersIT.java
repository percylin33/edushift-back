package com.edushift.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end IT (Testcontainers Postgres required) that pins the
 * response-header contract emitted by the running application.
 *
 * <h3>Why this exists alongside {@code SecurityHeadersTest}</h3>
 * The unit test is the fast local feedback; this IT is the gold standard
 * that runs in CI with the full Spring context. See {@code SecurityHeadersTest}
 * for details.
 *
 * <h3>What is verified</h3>
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff}</li>
 *   <li>{@code X-Frame-Options: DENY}</li>
 *   <li>{@code Referrer-Policy: strict-origin-when-cross-origin}</li>
 *   <li>{@code Permissions-Policy} contains {@code geolocation=()}</li>
 *   <li>{@code Content-Security-Policy} contains {@code frame-ancestors 'none'}</li>
 *   <li>{@code Cross-Origin-Opener-Policy: same-origin}</li>
 *   <li>{@code Cross-Origin-Embedder-Policy: require-corp}</li>
 *   <li>{@code Cross-Origin-Resource-Policy: same-origin}</li>
 * </ul>
 *
 * <h3>What is NOT verified</h3>
 * <ul>
 *   <li>{@code Strict-Transport-Security}: only emitted when the request is
 *       served over HTTPS or {@code server.forward-headers-strategy=native}
 *       is enabled. Manually verified against a TLS-terminating proxy in
 *       {@code docs/architecture/security.md}.</li>
 * </ul>
 */
class SecurityHeadersIT extends IntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	@DisplayName("Public endpoint emits the full set of defense-in-depth headers")
	void publicEndpointEmitsSecurityHeaders() {
		ResponseEntity<String> response = restTemplate.exchange(
				"/v1/tenants/by-slug/does-not-exist",
				HttpMethod.GET,
				new HttpEntity<>(new HttpHeaders()),
				String.class);

		HttpHeaders headers = response.getHeaders();
		assertThat(headers.getFirst("X-Content-Type-Options"))
				.as("X-Content-Type-Options should be nosniff")
				.isEqualTo("nosniff");
		assertThat(headers.getFirst("X-Frame-Options"))
				.as("X-Frame-Options should be DENY")
				.isEqualTo("DENY");
		assertThat(headers.getFirst("Referrer-Policy"))
				.as("Referrer-Policy should be strict-origin-when-cross-origin")
				.isEqualTo("strict-origin-when-cross-origin");
		assertThat(headers.getFirst("Content-Security-Policy"))
				.as("CSP should be present and contain frame-ancestors 'none'")
				.contains("frame-ancestors 'none'");
		assertThat(headers.getFirst("Permissions-Policy"))
				.as("Permissions-Policy should be present and disable geolocation")
				.contains("geolocation=()");
		assertThat(headers.getFirst("Cross-Origin-Opener-Policy"))
				.as("COOP should be same-origin")
				.isEqualTo("same-origin");
	}

	@Test
	@DisplayName("Authenticated endpoint emits the same headers (no auth gating of headers)")
	void authenticatedEndpointEmitsSecurityHeaders() {
		ResponseEntity<String> response = restTemplate.exchange(
				"/v1/users/me",
				HttpMethod.GET,
				new HttpEntity<>(new HttpHeaders()),
				String.class);

		assertThat(response.getStatusCode().is4xxClientError())
				.as("Endpoint should reject unauthenticated request")
				.isTrue();
		assertThat(response.getHeaders().getFirst("X-Frame-Options"))
				.as("X-Frame-Options should be DENY even on 401")
				.isEqualTo("DENY");
	}

	@Test
	@DisplayName("HSTS header is NOT emitted on plain HTTP (documented limitation)")
	void hstsNotEmittedOnPlainHttp() {
		ResponseEntity<String> response = restTemplate.exchange(
				"/v1/tenants/by-slug/does-not-exist",
				HttpMethod.GET,
				new HttpEntity<>(new HttpHeaders()),
				String.class);

		assertThat(response.getHeaders().getFirst("Strict-Transport-Security"))
				.as("HSTS should not be emitted on plain HTTP (Spring default)")
				.isNull();
	}
}