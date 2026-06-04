package com.edushift;

import org.junit.jupiter.api.Test;

/**
 * Smoke test that verifies the full Spring context boots cleanly under the
 * {@code test} profile (Testcontainers Postgres + cache disabled + JWT secret
 * from {@code application-test.properties}).
 *
 * <p>Inherits from {@link IntegrationTest} so the Postgres container is
 * spun up and Flyway runs through V1 → V_n. If any migration is invalid or
 * any bean wiring breaks, this test fails first — before the more elaborate
 * integration tests have a chance to obscure the root cause behind a stack
 * of higher-level assertions.
 */
class EduShiftApplicationTests extends IntegrationTest {

	@Test
	void contextLoads() {
	}

}
