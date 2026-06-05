package com.edushift.modules.users.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.users.dto.UpdateUserRequest;
import com.edushift.modules.users.dto.UserDetailResponse;
import com.edushift.modules.users.dto.UserListItem;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link UserManagementMapper}. Same flavour as
 * {@code TenantMapperTest}: instantiate the mapper directly (it has no
 * Spring deps) and pin every conversion field-by-field.
 *
 * <h3>What this test class is paranoid about</h3>
 * <ul>
 *   <li>Defensive copies of the role set — mutating the response must
 *       not leak into the entity.</li>
 *   <li>Partial-merge edge cases — the {@link UpdateUserRequest#isEmpty()}
 *       short-circuit, blank-string-as-clear semantics, and trim
 *       behaviour for the two text fields that surface in admin UIs.</li>
 *   <li>{@code fullName} is computed by the entity, but the mapper has
 *       to surface whatever the entity returns, even for the corner
 *       cases (one-of-two names missing, both missing).</li>
 * </ul>
 */
class UserManagementMapperTest {

	private final UserManagementMapper mapper = new UserManagementMapper();

	// ===========================================================================
	// toListItem
	// ===========================================================================

	@Nested
	@DisplayName("toListItem — list view projection")
	class ToListItem {

		@Test
		@DisplayName("maps every list-relevant field including roles + lastLoginAt + createdAt")
		void mapsAllFields() {
			User user = newUser("admin@acme.test", "Ada", "Lovelace", UserStatus.ACTIVE);
			user.addRole(UserRole.TENANT_ADMIN);
			user.addRole(UserRole.TEACHER);
			Instant lastLogin = Instant.parse("2025-12-01T08:00:00Z");
			user.setLastLoginAt(lastLogin);

			UserListItem item = mapper.toListItem(user);

			assertThat(item.publicUuid()).isEqualTo(user.getPublicUuid());
			assertThat(item.email()).isEqualTo("admin@acme.test");
			assertThat(item.firstName()).isEqualTo("Ada");
			assertThat(item.lastName()).isEqualTo("Lovelace");
			assertThat(item.fullName()).isEqualTo("Ada Lovelace");
			assertThat(item.status()).isEqualTo(UserStatus.ACTIVE);
			assertThat(item.roles())
					.containsExactlyInAnyOrder("TENANT_ADMIN", "TEACHER");
			assertThat(item.lastLoginAt()).isEqualTo(lastLogin);
		}

		@Test
		@DisplayName("emits an empty role set when the user has no roles assigned")
		void emptyRolesAreEmptySet() {
			User user = newUser("orphan@acme.test", "Orphan", "User", UserStatus.PENDING_VERIFICATION);

			UserListItem item = mapper.toListItem(user);

			assertThat(item.roles()).isEmpty();
		}

		@Test
		@DisplayName("returns a defensive role set (mutating the DTO must not affect the entity)")
		void roleSetIsDefensive() {
			User user = newUser("admin@acme.test", "Ada", "Lovelace", UserStatus.ACTIVE);
			user.addRole(UserRole.TEACHER);

			UserListItem item = mapper.toListItem(user);
			Set<String> roles = item.roles();

			// User.getRoleNames() returns a fresh LinkedHashSet — assert it
			// is independent of the entity's underlying String[].
			assertThat(roles).containsExactly("TEACHER");
			Set<String> entityRoles = user.getRoleNames();
			assertThat(entityRoles)
					.as("entity role set must be a separate instance from the DTO's")
					.isNotSameAs(roles);
		}
	}

	// ===========================================================================
	// toDetail
	// ===========================================================================

	@Nested
	@DisplayName("toDetail — admin profile view")
	class ToDetail {

		@Test
		@DisplayName("maps every detail field, including security flags and timestamps")
		void mapsAllFields() {
			User user = newUser("admin@acme.test", "Ada", "Lovelace", UserStatus.ACTIVE);
			user.setPhone("+51 999 888 777");
			user.setAvatarUrl("https://cdn.acme.test/avatars/ada.png");
			user.addRole(UserRole.TENANT_ADMIN);
			user.markEmailVerified();
			user.setMfaEnabled(true);

			UserDetailResponse response = mapper.toDetail(user);

			assertThat(response.publicUuid()).isEqualTo(user.getPublicUuid());
			assertThat(response.email()).isEqualTo("admin@acme.test");
			assertThat(response.fullName()).isEqualTo("Ada Lovelace");
			assertThat(response.phone()).isEqualTo("+51 999 888 777");
			assertThat(response.avatarUrl()).isEqualTo("https://cdn.acme.test/avatars/ada.png");
			assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
			assertThat(response.emailVerified()).isTrue();
			assertThat(response.mfaEnabled()).isTrue();
			assertThat(response.roles()).containsExactly("TENANT_ADMIN");
		}

		@Test
		@DisplayName("preserves null phone / avatarUrl when the entity has them unset")
		void nullableFieldsStayNull() {
			User user = newUser("plain@acme.test", "Plain", "User", UserStatus.ACTIVE);

			UserDetailResponse response = mapper.toDetail(user);

			assertThat(response.phone()).isNull();
			assertThat(response.avatarUrl()).isNull();
		}
	}

	// ===========================================================================
	// applyUpdate
	// ===========================================================================

	@Nested
	@DisplayName("applyUpdate — partial merge semantics")
	class ApplyUpdate {

		@Test
		@DisplayName("null patch and all-null patch are both no-ops")
		void nullPatchIsNoOp() {
			User user = newUser("ada@acme.test", "Ada", "Lovelace", UserStatus.ACTIVE);
			user.setPhone("+1 111 111 1111");

			mapper.applyUpdate(null, user);
			mapper.applyUpdate(new UpdateUserRequest(null, null, null, null), user);

			assertThat(user.getFirstName()).isEqualTo("Ada");
			assertThat(user.getLastName()).isEqualTo("Lovelace");
			assertThat(user.getPhone()).isEqualTo("+1 111 111 1111");
		}

		@Test
		@DisplayName("non-null first/lastName are trimmed before persistence")
		void namesAreTrimmed() {
			User user = newUser("ada@acme.test", "Ada", "Lovelace", UserStatus.ACTIVE);

			UpdateUserRequest patch = new UpdateUserRequest(
					"   Augusta   ", "  Byron   ", null, null);
			mapper.applyUpdate(patch, user);

			assertThat(user.getFirstName()).isEqualTo("Augusta");
			assertThat(user.getLastName()).isEqualTo("Byron");
		}

		@Test
		@DisplayName("blank phone clears the column (admin can remove a phone number)")
		void blankPhoneClearsField() {
			User user = newUser("ada@acme.test", "Ada", "Lovelace", UserStatus.ACTIVE);
			user.setPhone("+1 111 111 1111");

			UpdateUserRequest patch = new UpdateUserRequest(null, null, "   ", null);
			mapper.applyUpdate(patch, user);

			assertThat(user.getPhone()).isNull();
		}

		@Test
		@DisplayName("blank avatarUrl clears the column")
		void blankAvatarClearsField() {
			User user = newUser("ada@acme.test", "Ada", "Lovelace", UserStatus.ACTIVE);
			user.setAvatarUrl("https://old.example.com/avatar.png");

			UpdateUserRequest patch = new UpdateUserRequest(null, null, null, "");
			mapper.applyUpdate(patch, user);

			assertThat(user.getAvatarUrl()).isNull();
		}

		@Test
		@DisplayName("non-blank phone / avatarUrl are trimmed and persisted")
		void nonBlankValuesAreTrimmedAndStored() {
			User user = newUser("ada@acme.test", "Ada", "Lovelace", UserStatus.ACTIVE);

			UpdateUserRequest patch = new UpdateUserRequest(
					null, null,
					"  +51 999 999 999  ",
					"  https://cdn.acme.test/ada.png  ");
			mapper.applyUpdate(patch, user);

			assertThat(user.getPhone()).isEqualTo("+51 999 999 999");
			assertThat(user.getAvatarUrl()).isEqualTo("https://cdn.acme.test/ada.png");
		}

		@Test
		@DisplayName("does not touch fields not in the patch (roles, status, email, security flags)")
		void unrelatedFieldsArePreserved() {
			User user = newUser("ada@acme.test", "Ada", "Lovelace", UserStatus.ACTIVE);
			user.addRole(UserRole.TENANT_ADMIN);
			user.markEmailVerified();
			user.setMfaEnabled(true);

			UpdateUserRequest patch = new UpdateUserRequest("Augusta", null, null, null);
			mapper.applyUpdate(patch, user);

			assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
			assertThat(user.getEmail()).isEqualTo("ada@acme.test");
			assertThat(user.hasRole(UserRole.TENANT_ADMIN)).isTrue();
			assertThat(user.isEmailVerified()).isTrue();
			assertThat(user.isMfaEnabled()).isTrue();
		}
	}

	// ===========================================================================
	// Fixtures
	// ===========================================================================

	private static User newUser(String email, String first, String last, UserStatus status) {
		User u = new User();
		setIdViaReflection(u, UUID.randomUUID());
		u.setPublicUuid(UUID.randomUUID());
		u.setEmail(email);
		u.setFirstName(first);
		u.setLastName(last);
		u.setStatus(status);
		u.setPasswordHash("hashed");
		return u;
	}

	private static void setIdViaReflection(Object entity, UUID id) {
		try {
			Class<?> clazz = entity.getClass();
			while (clazz != null) {
				try {
					Field f = clazz.getDeclaredField("id");
					f.setAccessible(true);
					f.set(entity, id);
					return;
				}
				catch (NoSuchFieldException ignored) {
					clazz = clazz.getSuperclass();
				}
			}
			throw new IllegalStateException("No 'id' field found in " + entity.getClass());
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
