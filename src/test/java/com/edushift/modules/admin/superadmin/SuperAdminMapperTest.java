package com.edushift.modules.admin.superadmin;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.admin.superadmin.dto.SuperAdminSummary;
import com.edushift.modules.admin.superadmin.mapper.SuperAdminMapper;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link SuperAdminMapper}. No Spring context.
 *
 * <p>The mapper is a defensive boundary: it must <em>never</em> leak
 * {@code passwordHash}, {@code mfaSecretHash}, or {@code mfaRecoveryCodesHash}
 * to the wire DTO. The mapper deliberately has no access to those fields
 * (the {@link SuperAdminSummary} record doesn't even declare them), so the
 * invariant is structural rather than enforced by tests — but the tests
 * below document the contract and catch regressions if someone widens the
 * DTO.</p>
 */
class SuperAdminMapperTest {

	private static final UUID PUBLIC_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");

	private static User baseUser() {
		User u = new User();
		u.setPublicUuid(PUBLIC_UUID);
		u.setEmail("super@edushift.pe");
		u.setFirstName("Super");
		u.setLastName("Admin");
		u.setStatus(UserStatus.ACTIVE);
		u.setMfaEnabled(true);
		u.setLastLoginAt(Instant.parse("2026-07-01T10:00:00Z"));
		u.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
		u.setRoleSet(new LinkedHashSet<>(Set.of(UserRole.SUPER_ADMIN)));
		return u;
	}

	@Nested
	@DisplayName("Happy-path round-trip")
	class HappyPath {

		@Test
		@DisplayName("copies all public fields and drops roles to List<String>")
		void copiesAllFields() {
			SuperAdminSummary summary = SuperAdminMapper.toSummary(baseUser());

			assertThat(summary.publicUuid()).isEqualTo(PUBLIC_UUID);
			assertThat(summary.email()).isEqualTo("super@edushift.pe");
			assertThat(summary.firstName()).isEqualTo("Super");
			assertThat(summary.lastName()).isEqualTo("Admin");
			assertThat(summary.status()).isEqualTo("ACTIVE");
			assertThat(summary.mfaEnabled()).isTrue();
			assertThat(summary.lastLoginAt()).isEqualTo(Instant.parse("2026-07-01T10:00:00Z"));
			assertThat(summary.createdAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
			assertThat(summary.roles()).containsExactly("SUPER_ADMIN");
		}

		@Test
		@DisplayName("roles list is a defensive copy — mutating it does not affect the entity")
		void rolesAreDefensiveCopy() {
			User u = baseUser();
			SuperAdminSummary summary = SuperAdminMapper.toSummary(u);

			// getRoleNames() returns a LinkedHashSet built on the spot, so
			// mutating the returned DTO list must not bleed into the entity.
			assertThat(summary.roles()).isInstanceOf(java.util.List.class);
			assertThat(summary.roles()).hasSize(1);
			// The mapper uses List.copyOf(...) so the returned list is immutable.
			assertThatThrownByByListAdd(summary);
		}

		private void assertThatThrownByByListAdd(SuperAdminSummary summary) {
			try {
				summary.roles().add("HACKED");
				throw new AssertionError("expected List.copyOf to make roles() immutable");
			}
			catch (UnsupportedOperationException expected) {
				// ok — immutable list, defensive copy in place
			}
		}
	}

	@Nested
	@DisplayName("Null / empty edge cases")
	class EdgeCases {

		@Test
		@DisplayName("null user → null summary")
		void nullUser() {
			assertThat(SuperAdminMapper.toSummary(null)).isNull();
		}

		@Test
		@DisplayName("null roles array → empty list (not null)")
		void nullRoles() {
			User u = baseUser();
			u.setRoleSet(null);
			SuperAdminSummary summary = SuperAdminMapper.toSummary(u);
			assertThat(summary.roles()).isNotNull().isEmpty();
		}

		@Test
		@DisplayName("null status → null status field on DTO (no NPE)")
		void nullStatus() {
			User u = baseUser();
			u.setStatus(null);
			SuperAdminSummary summary = SuperAdminMapper.toSummary(u);
			assertThat(summary.status()).isNull();
		}

		@Test
		@DisplayName("multi-role user (shouldn't happen for SUPER_ADMIN, but mapper doesn't care)")
		void multiRole() {
			User u = baseUser();
			u.setRoleSet(new LinkedHashSet<>(Set.of(UserRole.SUPER_ADMIN, UserRole.TENANT_ADMIN)));
			SuperAdminSummary summary = SuperAdminMapper.toSummary(u);
			assertThat(summary.roles()).containsExactlyInAnyOrder("SUPER_ADMIN", "TENANT_ADMIN");
		}
	}

	@Nested
	@DisplayName("Security invariants")
	class SecurityInvariants {

		@Test
		@DisplayName("SuperAdminSummary record has NO field for passwordHash / mfaSecret / recoveryCodes")
		void dtoDoesNotExposeSensitiveFields() throws Exception {
			// Structural check: the record components are the only ones that
			// can be serialized to JSON. This test documents the contract.
			java.lang.reflect.RecordComponent[] components =
					SuperAdminSummary.class.getRecordComponents();
			assertThat(components)
					.extracting(java.lang.reflect.RecordComponent::getName)
					.containsExactlyInAnyOrder(
							"publicUuid", "email", "firstName", "lastName",
							"status", "mfaEnabled", "lastLoginAt", "createdAt", "roles");

			// Belt-and-braces: even if a developer adds a sensitive field, the
			// explicit setters on User must not be called by the mapper.
			User u = baseUser();
			u.setPasswordHash("THIS-IS-A-SECRET-BCRYPT-HASH");
			u.setMfaSecretHash("THIS-IS-A-SECRET-MFA-HASH");
			SuperAdminSummary summary = SuperAdminMapper.toSummary(u);
			// Register the jsr310 module so Instant can be serialized.
			com.fasterxml.jackson.databind.ObjectMapper om =
					new com.fasterxml.jackson.databind.ObjectMapper()
							.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
			String json = om.writeValueAsString(summary);
			assertThat(json)
					.as("passwordHash / mfaSecretHash must never leak into JSON")
					.doesNotContain("THIS-IS-A-SECRET");
		}
	}
}
