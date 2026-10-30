package com.edushift.infrastructure.seed;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ProductionSuperAdminBootstrapTest {

	@Test
	void disabledBootstrapDoesNotRequireCredentials() {
		var bootstrap = new ProductionSuperAdminBootstrap(null, null, null, false, "", "");
		assertDoesNotThrow(() -> bootstrap.run());
	}

	@Test
	void enabledBootstrapRejectsMissingEmail() {
		var bootstrap = new ProductionSuperAdminBootstrap(null, null, null, true, "", "TemporaryPass123!");
		assertThrows(IllegalStateException.class, () -> bootstrap.run());
	}

	@Test
	void enabledBootstrapRejectsInvalidEmail() {
		var bootstrap = new ProductionSuperAdminBootstrap(null, null, null, true, "invalid", "TemporaryPass123!");
		assertThrows(IllegalStateException.class, () -> bootstrap.run());
	}

	@Test
	void enabledBootstrapRejectsMissingPassword() {
		var bootstrap = new ProductionSuperAdminBootstrap(null, null, null, true, "admin@example.test", "");
		assertThrows(IllegalStateException.class, () -> bootstrap.run());
	}
}
