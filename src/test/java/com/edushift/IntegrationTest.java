package com.edushift;

import com.edushift.test.TestMailConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for full-stack integration tests.
 *
 * <h3>What this gives you</h3>
 * <ul>
 *   <li>A real, ephemeral Postgres 16 container started once per test class
 *       and shared across all {@code @Test} methods (the container survives
 *       the whole class lifecycle thanks to the {@code static} field — see
 *       Testcontainers docs).</li>
 *   <li>Spring Boot 3.x {@link ServiceConnection} magic that auto-wires the
 *       container's {@code jdbcUrl}, {@code username} and {@code password}
 *       into the application's {@code DataSource}. No
 *       {@code @DynamicPropertySource} boilerplate is needed.</li>
 *   <li>{@code @ActiveProfiles("test")} activates {@code application-test.properties}
 *       which disables Redis / cache and pins a deterministic JWT secret.</li>
 *   <li>{@code WebEnvironment.RANDOM_PORT} starts an embedded Tomcat on a
 *       random port; subclasses get a {@code TestRestTemplate} bean
 *       pre-configured with that base URL.</li>
 * </ul>
 *
 * <h3>What this does NOT do</h3>
 * <ul>
 *   <li>It does not seed any data. Each subclass / test method is responsible
 *       for creating the rows it needs (this keeps test bodies self-explanatory
 *       and avoids the "magic fixture" pattern that hides assumptions).</li>
 *   <li>It does not roll back DB state between tests. Tests share a single
 *       Postgres container per class, so methods must use unique slugs /
 *       emails (UUID suffixes) to avoid stepping on each other.</li>
 * </ul>
 *
 * <h3>Why a separate class instead of an annotation</h3>
 * Composing {@code @SpringBootTest}, {@code @ActiveProfiles} and the static
 * container in a single meta-annotation is not possible — the container
 * declaration must be a field, not metadata. Inheritance is the simplest
 * mechanism.
 *
 * <h3>Why the singleton container pattern (and NOT {@code @Testcontainers})</h3>
 * The first revision used {@code @Testcontainers} + {@code @Container}, which
 * is JUnit 5 idiomatic but treats the field as <em>class-scoped</em>: the
 * extension stops the container at the end of each test class. When Maven
 * Failsafe runs <em>multiple</em> {@code IT} classes in the same JVM (e.g.
 * {@code AuthTenantIsolationIT} followed by {@code TenantsTenantIsolationIT}),
 * the second class would see {@code Hikari pool empty / Connection has been
 * closed} because the container had been torn down between classes. The
 * documented fix from Testcontainers itself is the <em>singleton container
 * pattern</em> — start the container once in a static initializer and let
 * the JVM shutdown reap it. JDBC URL and credentials are then bridged to
 * Spring via {@code @DynamicPropertySource} (the lower-level equivalent of
 * the {@code @ServiceConnection} hook the previous revision relied on).
 *
 * @see <a href="https://java.testcontainers.org/test_framework_integration/manual_lifecycle_control/">
 *      Testcontainers — manual lifecycle control / singleton container pattern</a>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestMailConfig.class)
public abstract class IntegrationTest {

	private static final java.util.concurrent.atomic.AtomicInteger TEST_CLIENT_IDS =
			new java.util.concurrent.atomic.AtomicInteger();

	@org.springframework.beans.factory.annotation.Autowired
	private org.springframework.boot.test.web.client.TestRestTemplate fixtureHttpClient;

	@org.junit.jupiter.api.BeforeEach
	void isolateHttpClientRateLimitBucket() {
		var interceptors = fixtureHttpClient.getRestTemplate().getInterceptors();
		interceptors.removeIf(value -> value instanceof TestClientAddress);
		int client = TEST_CLIENT_IDS.incrementAndGet();
		interceptors.add(new TestClientAddress("198.18." + ((client >>> 8) & 255) + "." + (client & 255)));
	}

	/** Each test is a separate client; requests within one test still share the real limit. */
	private record TestClientAddress(String ip) implements org.springframework.http.client.ClientHttpRequestInterceptor {
		@Override
		public org.springframework.http.client.ClientHttpResponse intercept(
				org.springframework.http.HttpRequest request, byte[] body,
				org.springframework.http.client.ClientHttpRequestExecution execution) throws java.io.IOException {
			if (!request.getHeaders().containsKey("X-Forwarded-For")) {
				request.getHeaders().set("X-Forwarded-For", ip);
			}
			return execution.execute(request, body);
		}
	}

	@org.springframework.beans.factory.annotation.Autowired
	private com.edushift.modules.admin.plans.PlatformPlanRepository fixturePlans;

	/** Creates an unsaved school with the required catalog plan, using actual migrated data. */
	protected com.edushift.modules.tenants.entity.Tenant newTestTenant() {
		var tenant = new com.edushift.modules.tenants.entity.Tenant();
		tenant.setPlan(com.edushift.modules.tenants.entity.TenantPlan.BASIC);
		tenant.setPlanId(fixturePlans.findByCode("BASIC").orElseThrow().getId());
		return tenant;
	}

	protected static String sessionCookie(org.springframework.http.ResponseEntity<?> response, String name) {
		var cookies = response.getHeaders().getOrEmpty(org.springframework.http.HttpHeaders.SET_COOKIE)
				.stream().flatMap(header -> java.net.HttpCookie.parse(header).stream()).toList();
		return sessionCookie(cookies, name);
	}

	protected static com.edushift.modules.auth.dto.AuthResponse readSessionResponse(
			org.springframework.http.ResponseEntity<com.edushift.modules.auth.dto.AuthResponse> response) {
		var body = java.util.Objects.requireNonNull(response.getBody());
		org.assertj.core.api.Assertions.assertThat(body.accessToken()).isNull();
		org.assertj.core.api.Assertions.assertThat(body.refreshToken()).isNull();
		return new com.edushift.modules.auth.dto.AuthResponse(
				sessionCookie(response, "edushift_access_token"), sessionCookie(response, "edushift_refresh_token"),
				body.tokenType(), body.expiresInSec(), body.user());
	}

	/** Reads the browser session from HttpOnly response cookies for bearer-based isolation checks. */
	protected static com.edushift.modules.auth.dto.AuthResponse readSessionResponse(
			com.fasterxml.jackson.databind.ObjectMapper mapper,
			org.springframework.http.ResponseEntity<String> response) throws java.io.IOException {
		var body = mapper.readValue(response.getBody(), com.edushift.modules.auth.dto.AuthResponse.class);
		org.assertj.core.api.Assertions.assertThat(body.accessToken()).isNull();
		org.assertj.core.api.Assertions.assertThat(body.refreshToken()).isNull();
		var cookies = response.getHeaders().getOrEmpty(org.springframework.http.HttpHeaders.SET_COOKIE)
				.stream().flatMap(header -> java.net.HttpCookie.parse(header).stream()).toList();
		String access = sessionCookie(cookies, "edushift_access_token");
		String refresh = sessionCookie(cookies, "edushift_refresh_token");
		return new com.edushift.modules.auth.dto.AuthResponse(
				access, refresh, body.tokenType(), body.expiresInSec(), body.user());
	}

	private static String sessionCookie(java.util.List<java.net.HttpCookie> cookies, String name) {
		var cookie = cookies.stream().filter(value -> value.getName().equals(name)).findFirst().orElseThrow(
				() -> new AssertionError("Missing session cookie: " + name));
		org.assertj.core.api.Assertions.assertThat(cookie.isHttpOnly()).as(name + " must be HttpOnly").isTrue();
		org.assertj.core.api.Assertions.assertThat(cookie.getValue()).as(name + " must not be blank").isNotBlank();
		return cookie.getValue();
	}

	/**
	 * Postgres container, started once per JVM and shared across all
	 * integration test classes. Tag pinned for reproducibility; Alpine image
	 * keeps first-run pulls under ~80 MB (vs ~440 MB for the full image).
	 */
	@SuppressWarnings("resource") // intentional: lifetime == JVM
	static final PostgreSQLContainer<?> POSTGRES =
			new PostgreSQLContainer<>("postgres:16-alpine")
					.withDatabaseName("edushift_it")
					.withUsername("edushift")
					.withPassword("edushift");

	static {
		POSTGRES.start();
	}

	@DynamicPropertySource
	static void registerPostgresProperties(DynamicPropertyRegistry registry) {
		String paymentKey = java.util.Base64.getEncoder().encodeToString(
				new java.security.SecureRandom().generateSeed(32));
		registry.add("payment.encryption.key", () -> paymentKey);
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
	}

}
