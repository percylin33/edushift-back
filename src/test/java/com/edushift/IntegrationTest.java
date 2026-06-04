package com.edushift;

import org.springframework.boot.test.context.SpringBootTest;
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
public abstract class IntegrationTest {

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
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
	}

}
