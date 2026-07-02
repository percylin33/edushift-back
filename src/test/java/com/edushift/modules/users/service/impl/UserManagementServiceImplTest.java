package com.edushift.modules.users.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.users.dto.AssignRolesRequest;
import com.edushift.modules.users.dto.UpdateUserRequest;
import com.edushift.modules.users.dto.UserDetailResponse;
import com.edushift.modules.users.dto.UserListFilters;
import com.edushift.modules.users.dto.UserListItem;
import com.edushift.modules.users.mapper.UserManagementMapper;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.multitenancy.TenantContext;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

/**
 * Unit tests for {@link UserManagementServiceImpl}.
 *
 * <h3>Mocking strategy</h3>
 * <ul>
 *   <li>{@link UserRepository} is fully mocked.</li>
 *   <li>{@link UserManagementMapper} is the <em>real</em> bean (already
 *       pinned by {@code UserManagementMapperTest}); using it gives us
 *       realistic {@link UserDetailResponse} payloads to assert against.</li>
 *   <li>{@link TenantContext} is set in {@link #setUp} so the
 *       last-admin guardrail's native query has a tenant id to pass.</li>
 *   <li>The {@link SecurityContextHolder} is seeded with a
 *       {@link JwtAuthenticationToken} whose {@code getName()} is the
 *       admin's public UUID — that's the contract the production
 *       {@code JwtAuthenticationFilter} delivers.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class UserManagementServiceImplTest {

	@Mock private UserRepository userRepository;

	@Mock private ApplicationEventPublisher eventPublisher;

	private UserManagementServiceImpl service;

	private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID ADMIN_PUBLIC_UUID = UUID.fromString("22222222-2222-2222-2222-222222222222");

	@BeforeEach
	void setUp() {
		service = new UserManagementServiceImpl(userRepository, new UserManagementMapper(), eventPublisher);
		TenantContext.set(TENANT_ID);
		seedSecurityContext(ADMIN_PUBLIC_UUID);
	}

	@AfterEach
	void tearDown() {
		TenantContext.clear();
		SecurityContextHolder.clearContext();
	}

	// ===========================================================================
	// listUsers
	// ===========================================================================

	@Nested
	@DisplayName("listUsers — paginated read")
	class ListUsers {

		@Test
		@DisplayName("happy path — delegates to repository.findAll(spec, pageable) and maps each row")
		void happyPath() {
			User a = newUser("a@acme.test", "Ada", "L", UserStatus.ACTIVE);
			User b = newUser("b@acme.test", "Bob", "M", UserStatus.SUSPENDED);
			when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
					.thenReturn(new PageImpl<>(List.of(a, b)));

			Page<UserListItem> page = service.listUsers(UserListFilters.empty(), Pageable.unpaged());

			assertThat(page.getContent()).hasSize(2);
			assertThat(page.getContent().get(0).email()).isEqualTo("a@acme.test");
			assertThat(page.getContent().get(1).status()).isEqualTo(UserStatus.SUSPENDED);
		}

		@Test
		@DisplayName("null filters are treated as empty filters (no NullPointerException downstream)")
		void nullFiltersAreSafe() {
			when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
					.thenReturn(new PageImpl<>(List.of()));

			service.listUsers(null, Pageable.unpaged());

			verify(userRepository).findAll(any(Specification.class), any(Pageable.class));
		}
	}

	// ===========================================================================
	// getUser
	// ===========================================================================

	@Nested
	@DisplayName("getUser — single read")
	class GetUser {

		@Test
		@DisplayName("happy path — returns the detail projection")
		void happyPath() {
			UUID publicUuid = UUID.randomUUID();
			User user = newUser("ada@acme.test", "Ada", "Lovelace", UserStatus.ACTIVE);
			user.setPublicUuid(publicUuid);
			when(userRepository.findByPublicUuid(publicUuid)).thenReturn(Optional.of(user));

			UserDetailResponse response = service.getUser(publicUuid);

			assertThat(response.publicUuid()).isEqualTo(publicUuid);
			assertThat(response.fullName()).isEqualTo("Ada Lovelace");
		}

		@Test
		@DisplayName("unknown publicUuid → ResourceNotFoundException(RESOURCE_NOT_FOUND)")
		void unknownThrows() {
			UUID publicUuid = UUID.randomUUID();
			when(userRepository.findByPublicUuid(publicUuid)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.getUser(publicUuid))
					.isInstanceOf(ResourceNotFoundException.class);
		}
	}

	// ===========================================================================
	// updateUser
	// ===========================================================================

	@Nested
	@DisplayName("updateUser — partial profile patch")
	class UpdateUser {

		@Test
		@DisplayName("non-empty patch is applied + saved + returned")
		void appliesPatch() {
			UUID publicUuid = UUID.randomUUID();
			User user = newUser("ada@acme.test", "Ada", "L", UserStatus.ACTIVE);
			user.setPublicUuid(publicUuid);
			when(userRepository.findByPublicUuid(publicUuid)).thenReturn(Optional.of(user));
			when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

			UpdateUserRequest patch = new UpdateUserRequest("Augusta", null, null, null);
			UserDetailResponse response = service.updateUser(publicUuid, patch);

			assertThat(response.firstName()).isEqualTo("Augusta");
			verify(userRepository).save(user);
		}

		@Test
		@DisplayName("empty patch short-circuits — no save() call")
		void emptyPatchSkipsSave() {
			UUID publicUuid = UUID.randomUUID();
			User user = newUser("ada@acme.test", "Ada", "L", UserStatus.ACTIVE);
			user.setPublicUuid(publicUuid);
			when(userRepository.findByPublicUuid(publicUuid)).thenReturn(Optional.of(user));

			service.updateUser(publicUuid, new UpdateUserRequest(null, null, null, null));

			verify(userRepository, never()).save(any(User.class));
		}

		@Test
		@DisplayName("null patch short-circuits — no save() call")
		void nullPatchSkipsSave() {
			UUID publicUuid = UUID.randomUUID();
			User user = newUser("ada@acme.test", "Ada", "L", UserStatus.ACTIVE);
			user.setPublicUuid(publicUuid);
			when(userRepository.findByPublicUuid(publicUuid)).thenReturn(Optional.of(user));

			service.updateUser(publicUuid, null);

			verify(userRepository, never()).save(any(User.class));
		}
	}

	// ===========================================================================
	// assignRoles
	// ===========================================================================

	@Nested
	@DisplayName("assignRoles — wholesale role replacement")
	class AssignRoles {

		@Test
		@DisplayName("happy path — replaces the role set, persists, returns the new detail")
		void replacesRoles() {
			UUID publicUuid = UUID.randomUUID();
			User user = newUser("teach@acme.test", "T", "User", UserStatus.ACTIVE);
			user.setPublicUuid(publicUuid);
			user.addRole(UserRole.TEACHER);
			when(userRepository.findByPublicUuid(publicUuid)).thenReturn(Optional.of(user));
			when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

			AssignRolesRequest request = new AssignRolesRequest(Set.of("STAFF", "TEACHER"));
			UserDetailResponse response = service.assignRoles(publicUuid, request);

			assertThat(response.roles()).containsExactlyInAnyOrder("STAFF", "TEACHER");
			ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
			verify(userRepository).save(captor.capture());
			assertThat(captor.getValue().getRoleSet())
					.containsExactlyInAnyOrder(UserRole.STAFF, UserRole.TEACHER);
		}

		@Test
		@DisplayName("unknown role → BusinessException(INVALID_ROLE), no save")
		void unknownRoleRejected() {
			UUID publicUuid = UUID.randomUUID();
			User user = newUser("teach@acme.test", "T", "User", UserStatus.ACTIVE);
			user.setPublicUuid(publicUuid);
			when(userRepository.findByPublicUuid(publicUuid)).thenReturn(Optional.of(user));

			AssignRolesRequest request = new AssignRolesRequest(Set.of("WIZARD"));

			assertThatThrownBy(() -> service.assignRoles(publicUuid, request))
					.isInstanceOfSatisfying(BusinessException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("INVALID_ROLE"));

			verify(userRepository, never()).save(any(User.class));
		}

		@Test
		@DisplayName("removing TENANT_ADMIN from the last admin → ConflictException(LAST_ADMIN_PROTECTION)")
		void lastAdminCannotLoseRole() {
			UUID publicUuid = UUID.randomUUID();
			User user = newUser("admin@acme.test", "A", "User", UserStatus.ACTIVE);
			user.setPublicUuid(publicUuid);
			user.addRole(UserRole.TENANT_ADMIN);
			when(userRepository.findByPublicUuid(publicUuid)).thenReturn(Optional.of(user));
			when(userRepository.countActiveTenantAdmins(TENANT_ID)).thenReturn(1L);

			AssignRolesRequest request = new AssignRolesRequest(Set.of("TEACHER"));

			assertThatThrownBy(() -> service.assignRoles(publicUuid, request))
					.isInstanceOfSatisfying(ConflictException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("LAST_ADMIN_PROTECTION"));

			verify(userRepository, never()).save(any(User.class));
		}

		@Test
		@DisplayName("removing TENANT_ADMIN when other admins exist → succeeds")
		void admin_demotion_allowed_when_others_present() {
			UUID publicUuid = UUID.randomUUID();
			User user = newUser("admin@acme.test", "A", "User", UserStatus.ACTIVE);
			user.setPublicUuid(publicUuid);
			user.addRole(UserRole.TENANT_ADMIN);
			when(userRepository.findByPublicUuid(publicUuid)).thenReturn(Optional.of(user));
			when(userRepository.countActiveTenantAdmins(TENANT_ID)).thenReturn(2L);
			when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

			AssignRolesRequest request = new AssignRolesRequest(Set.of("TEACHER"));
			UserDetailResponse response = service.assignRoles(publicUuid, request);

			assertThat(response.roles()).containsExactly("TEACHER");
		}
	}

	// ===========================================================================
	// disableUser
	// ===========================================================================

	@Nested
	@DisplayName("disableUser — admin lockout")
	class DisableUser {

		@Test
		@DisplayName("happy path — ACTIVE non-admin → SUSPENDED")
		void happyPath() {
			UUID publicUuid = UUID.randomUUID();
			User user = newUser("teach@acme.test", "T", "User", UserStatus.ACTIVE);
			user.setPublicUuid(publicUuid);
			user.addRole(UserRole.TEACHER);
			when(userRepository.findByPublicUuid(publicUuid)).thenReturn(Optional.of(user));
			when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

			UserDetailResponse response = service.disableUser(publicUuid);

			assertThat(response.status()).isEqualTo(UserStatus.SUSPENDED);
		}

		@Test
		@DisplayName("self-lockout → BusinessException(SELF_LOCKOUT)")
		void selfLockoutRejected() {
			UUID publicUuid = ADMIN_PUBLIC_UUID;
			User user = newUser("admin@acme.test", "A", "User", UserStatus.ACTIVE);
			user.setPublicUuid(publicUuid);
			when(userRepository.findByPublicUuid(publicUuid)).thenReturn(Optional.of(user));

			assertThatThrownBy(() -> service.disableUser(publicUuid))
					.isInstanceOfSatisfying(BusinessException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("SELF_LOCKOUT"));

			verify(userRepository, never()).save(any(User.class));
		}

		@Test
		@DisplayName("disabling the last active admin → ConflictException(LAST_ADMIN_PROTECTION)")
		void lastAdminCannotBeDisabled() {
			UUID publicUuid = UUID.randomUUID();
			User user = newUser("admin@acme.test", "A", "User", UserStatus.ACTIVE);
			user.setPublicUuid(publicUuid);
			user.addRole(UserRole.TENANT_ADMIN);
			when(userRepository.findByPublicUuid(publicUuid)).thenReturn(Optional.of(user));
			when(userRepository.countActiveTenantAdmins(TENANT_ID)).thenReturn(1L);

			assertThatThrownBy(() -> service.disableUser(publicUuid))
					.isInstanceOfSatisfying(ConflictException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("LAST_ADMIN_PROTECTION"));
		}

		@Test
		@DisplayName("already SUSPENDED → idempotent no-op (no save)")
		void alreadySuspendedIsIdempotent() {
			UUID publicUuid = UUID.randomUUID();
			User user = newUser("teach@acme.test", "T", "User", UserStatus.SUSPENDED);
			user.setPublicUuid(publicUuid);
			when(userRepository.findByPublicUuid(publicUuid)).thenReturn(Optional.of(user));

			UserDetailResponse response = service.disableUser(publicUuid);

			assertThat(response.status()).isEqualTo(UserStatus.SUSPENDED);
			verify(userRepository, never()).save(any(User.class));
		}
	}

	// ===========================================================================
	// enableUser
	// ===========================================================================

	@Nested
	@DisplayName("enableUser — un-suspend")
	class EnableUser {

		@Test
		@DisplayName("happy path — SUSPENDED → ACTIVE")
		void happyPath() {
			UUID publicUuid = UUID.randomUUID();
			User user = newUser("teach@acme.test", "T", "User", UserStatus.SUSPENDED);
			user.setPublicUuid(publicUuid);
			when(userRepository.findByPublicUuid(publicUuid)).thenReturn(Optional.of(user));
			when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

			UserDetailResponse response = service.enableUser(publicUuid);

			assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
		}

		@Test
		@DisplayName("already ACTIVE → idempotent no-op")
		void alreadyActiveIsIdempotent() {
			UUID publicUuid = UUID.randomUUID();
			User user = newUser("teach@acme.test", "T", "User", UserStatus.ACTIVE);
			user.setPublicUuid(publicUuid);
			when(userRepository.findByPublicUuid(publicUuid)).thenReturn(Optional.of(user));

			service.enableUser(publicUuid);

			verify(userRepository, never()).save(any(User.class));
		}

		@Test
		@DisplayName("PENDING_VERIFICATION → ConflictException(USER_NOT_ENABLEABLE)")
		void pendingVerificationRefuses() {
			UUID publicUuid = UUID.randomUUID();
			User user = newUser("new@acme.test", "N", "User", UserStatus.PENDING_VERIFICATION);
			user.setPublicUuid(publicUuid);
			when(userRepository.findByPublicUuid(publicUuid)).thenReturn(Optional.of(user));

			assertThatThrownBy(() -> service.enableUser(publicUuid))
					.isInstanceOfSatisfying(ConflictException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("USER_NOT_ENABLEABLE"));
		}

		@Test
		@DisplayName("LOCKED → ConflictException(USER_NOT_ENABLEABLE)")
		void lockedRefuses() {
			UUID publicUuid = UUID.randomUUID();
			User user = newUser("locked@acme.test", "L", "User", UserStatus.LOCKED);
			user.setPublicUuid(publicUuid);
			when(userRepository.findByPublicUuid(publicUuid)).thenReturn(Optional.of(user));

			assertThatThrownBy(() -> service.enableUser(publicUuid))
					.isInstanceOf(ConflictException.class);
		}
	}

	// ===========================================================================
	// resetPassword
	// ===========================================================================

	@Nested
	@DisplayName("resetPassword — Sprint 9 stub")
	class ResetPassword {

		@Test
		@DisplayName("loads the user, logs the intent, returns void; never saves (Sprint 9 will)")
		void stub() {
			UUID publicUuid = UUID.randomUUID();
			User user = newUser("ada@acme.test", "Ada", "L", UserStatus.ACTIVE);
			user.setPublicUuid(publicUuid);
			when(userRepository.findByPublicUuid(publicUuid)).thenReturn(Optional.of(user));

			service.resetPassword(publicUuid);

			verify(userRepository, never()).save(any(User.class));
		}

		@Test
		@DisplayName("unknown user → ResourceNotFoundException")
		void unknownUserStillFailsFast() {
			UUID publicUuid = UUID.randomUUID();
			when(userRepository.findByPublicUuid(publicUuid)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.resetPassword(publicUuid))
					.isInstanceOf(ResourceNotFoundException.class);
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

	private static void seedSecurityContext(UUID adminPublicUuid) {
		JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
				adminPublicUuid,
				TENANT_ID,
				"acme",
				"admin@acme.test");
		JwtAuthenticationToken auth = new JwtAuthenticationToken(principal, "fake.token", List.of());
		SecurityContextImpl ctx = new SecurityContextImpl();
		ctx.setAuthentication(auth);
		SecurityContextHolder.setContext(ctx);
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
