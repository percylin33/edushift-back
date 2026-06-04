package com.edushift;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
 * Composing {@code @SpringBootTest}, {@code @Testcontainers},
 * {@code @ActiveProfiles} and a {@code static @Container} field cannot be
 * expressed cleanly in a single meta-annotation (the container declaration
 * must be a field, not metadata). Inheritance is the simplest mechanism.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class IntegrationTest {

	/**
	 * Postgres container. {@code static} so it lives for the entire test class
	 * (Testcontainers' JUnit Jupiter extension treats static {@code @Container}
	 * fields as class-scoped). Pinning a tag (instead of {@code latest}) keeps
	 * runs reproducible and matches what we plan to deploy in prod.
	 *
	 * <p>Image is the slim Alpine variant (~80MB, vs ~440MB for the full image)
	 * which dramatically reduces first-run pull time on dev laptops and CI.
	 */
	@Container
	@ServiceConnection
	@SuppressWarnings("resource") // closed automatically by the Testcontainers extension
	static final PostgreSQLContainer<?> POSTGRES =
			new PostgreSQLContainer<>("postgres:16-alpine")
					.withDatabaseName("edushift_it")
					.withUsername("edushift")
					.withPassword("edushift");

}
