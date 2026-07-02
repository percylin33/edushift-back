package com.edushift.modules.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Sprint 14 (MVP Closure) / DEBT-USR-1 — naming canonicalization.
 *
 * <p>Locks in the design decision (ADR-14.4) that the canonical role name is
 * {@code PARENT}. Validates:
 * <ul>
 *   <li>The Java enum has {@code PARENT} (matches the DB and JWT).</li>
 *   <li>There is no Java-level {@code GUARDIAN} alias. The frontend
 *       translates {@code GUARDIAN → PARENT} at the boundary via the
 *       adapter — see {@code FE-14.3} and the migration notes in
 *       {@code docs/modules/users.md} §5 D-USR-09.</li>
 *   <li>All five canonical roles are present and parseable.</li>
 * </ul>
 */
class UserRoleParityTest {

	@Test
	@DisplayName("DEBT-USR-1: UserRole has PARENT as canonical name")
	void parentIsCanonical() {
		assertThat(UserRole.valueOf("PARENT"))
				.as("UserRole.PARENT is the canonical name").isNotNull();
	}

	@Test
	@DisplayName("DEBT-USR-1: UserRole does NOT have a legacy GUARDIAN entry")
	void noLegacyGuardianEntry() {
		Set<String> roleNames = Arrays.stream(UserRole.values())
				.map(Enum::name)
				.collect(Collectors.toSet());
		assertThat(roleNames)
				.as("Canonical enum names (DB / JWT)")
				.containsExactlyInAnyOrder(
						"TENANT_ADMIN", "TEACHER", "STUDENT", "PARENT", "STAFF");
		assertThat(roleNames)
				.as("Deprecated GUARDIAN value must NOT exist at the BE layer")
				.doesNotContain("GUARDIAN");
	}

	@Test
	@DisplayName("DEBT-USR-1: fromName() parses all canonical roles")
	void fromNameAcceptsAllCanonical() {
		for (String name : new String[]{"TENANT_ADMIN", "TEACHER", "STUDENT", "PARENT", "STAFF"}) {
			assertThat(UserRole.fromName(name)).isNotNull();
		}
		assertThat(UserRole.fromName("GUARDIAN"))
				.as("Client code that still sends GUARDIAN must update to PARENT — "
						+ "we return null so the controller surfaces a 422 INVALID_ROLE.")
				.isNull();
	}

	@Test
	@DisplayName("DEBT-USR-1: fromName() returns null on unknown input (not exception)")
	void fromNameReturnsNullOnUnknown() {
		assertThat(UserRole.fromName(null)).isNull();
		assertThat(UserRole.fromName("")).isNull();
		assertThat(UserRole.fromName("guardian")).isNull();  // case-sensitive
		assertThat(UserRole.fromName("ADMIN")).isNull();
	}
}
