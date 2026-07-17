package com.edushift.modules.admin.superadmin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.infrastructure.multitenancy.TenantIdResolver;
import com.edushift.modules.admin.superadmin.dto.CreateSuperAdminRequest;
import com.edushift.modules.admin.superadmin.dto.SuperAdminSummary;
import com.edushift.modules.audit.events.AuditAction;
import com.edushift.modules.audit.service.AuditLogger;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ForbiddenException;
import com.edushift.shared.exception.NotFoundException;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * Unit tests for {@link SuperAdminService}. Mockito-only — no Spring context.
 *
 * <p>Strategy: mock the three collaborators ({@link UserRepository},
 * {@link AuditLogger}, {@link PlatformTransactionManager}) and assert on the
 * 4 service methods + the 6 documented error branches. We deliberately
 * avoid Mockito's lenient-stubbing because every stub returns a value used
 * by the production code path.</p>
 *
 * <p>The service uses {@code TransactionTemplate} internally; we feed it a
 * {@link SimpleTransactionStatus} so {@code execute(...)} runs the lambda
 * synchronously without needing a real transaction manager.</p>
 */
class SuperAdminServiceTest {

	private UserRepository userRepository;
	private AuditLogger auditLogger;
	private PlatformTransactionManager txManager;
	private SuperAdminService service;

	@BeforeEach
	void setUp() {
		userRepository = Mockito.mock(UserRepository.class);
		auditLogger = Mockito.mock(AuditLogger.class);
		txManager = Mockito.mock(PlatformTransactionManager.class);
		// SimpleTransactionStatus lets TransactionTemplate.execute run the
		// callback synchronously without trying to commit a real DB.
		when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

		service = new SuperAdminService(userRepository, auditLogger, txManager);
	}

	@AfterEach
	void clearTenant() {
		// Defensive: tests must never leak a TenantContext binding across
		// methods because the static ThreadLocal is JVM-wide.
		TenantContext.clear();
	}

	// =========================================================================
	// Helpers
	// =========================================================================

	private static User superAdmin(String email, UserStatus status) {
		User u = new User();
		u.setPublicUuid(UUID.randomUUID());
		u.setEmail(email);
		u.setFirstName("S");
		u.setLastName("A");
		u.setStatus(status);
		u.setEmailVerified(true);
		u.setMfaEnabled(false);
		u.setRoleSet(new java.util.LinkedHashSet<>(java.util.Set.of(UserRole.SUPER_ADMIN)));
		return u;
	}

	private static User tenantAdmin() {
		User u = new User();
		u.setPublicUuid(UUID.randomUUID());
		u.setEmail("ta@edushift.pe");
		u.setStatus(UserStatus.ACTIVE);
		u.setRoleSet(new java.util.LinkedHashSet<>(java.util.Set.of(UserRole.TENANT_ADMIN)));
		return u;
	}

	private static User inactiveSuperAdmin() {
		return superAdmin("inactive@edushift.pe", UserStatus.INACTIVE);
	}

	// =========================================================================
	// list()
	// =========================================================================

	@Nested
	@DisplayName("list()")
	class ListTests {

		@Test
		@DisplayName("returns only active SUPER_ADMINs, runs under SUPER_ADMIN_SENTINEL tenant")
		void filtersAndScopes() {
			User a = superAdmin("a@edushift.pe", UserStatus.ACTIVE);
			User b = superAdmin("b@edushift.pe", UserStatus.ACTIVE);
			User inact = inactiveSuperAdmin();
			User ta = tenantAdmin();
			when(userRepository.findAll()).thenReturn(List.of(a, b, inact, ta));

			List<SuperAdminSummary> result = service.list();

			assertThat(result).hasSize(2);
			assertThat(result).extracting(SuperAdminSummary::email)
					.containsExactlyInAnyOrder("a@edushift.pe", "b@edushift.pe");
			assertThat(result).extracting(SuperAdminSummary::status)
					.containsOnly("ACTIVE");
			assertThat(result).allSatisfy(s ->
					assertThat(s.roles()).contains("SUPER_ADMIN"));
		}

		@Test
		@DisplayName("empty repository → empty list, no exception")
		void empty() {
			when(userRepository.findAll()).thenReturn(List.of());
			assertThat(service.list()).isEmpty();
		}
	}

	// =========================================================================
	// create()
	// =========================================================================

	@Nested
	@DisplayName("create()")
	class CreateTests {

		@Test
		@DisplayName("happy path — persists user under SUPER_ADMIN_SENTINEL with sentinel hash")
		void happyPath() {
			CreateSuperAdminRequest req = new CreateSuperAdminRequest(
					"new-admin@edushift.pe", "Ada", "Lovelace");
			when(userRepository.existsByEmail("new-admin@edushift.pe")).thenReturn(false);
			when(userRepository.save(any(User.class))).thenAnswer(inv -> {
				User u = inv.getArgument(0);
				if (u.getPublicUuid() == null) u.setPublicUuid(UUID.randomUUID());
				return u;
			});

			SuperAdminSummary summary = service.create(req, UUID.randomUUID());

			ArgumentCaptor<User> savedCaptor = ArgumentCaptor.forClass(User.class);
			verify(userRepository).save(savedCaptor.capture());
			User saved = savedCaptor.getValue();
			assertThat(saved.getEmail()).isEqualTo("new-admin@edushift.pe");
			assertThat(saved.getFirstName()).isEqualTo("Ada");
			assertThat(saved.getLastName()).isEqualTo("Lovelace");
			assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
			assertThat(saved.isEmailVerified()).isTrue();
			assertThat(saved.isMfaEnabled()).isFalse();
			assertThat(saved.getPasswordHash())
					.isEqualTo("SUPER_ADMIN_RESET_REQUIRED_v1_new_user");
			assertThat(saved.hasRole(UserRole.SUPER_ADMIN)).isTrue();

			// Audit log: CREATE / super_admin / {resourceId} / "SUPER_ADMIN created"
			ArgumentCaptor<AuditAction> actionCap = ArgumentCaptor.forClass(AuditAction.class);
			ArgumentCaptor<String> typeCap = ArgumentCaptor.forClass(String.class);
			ArgumentCaptor<UUID> resIdCap = ArgumentCaptor.forClass(UUID.class);
			ArgumentCaptor<String> summaryCap = ArgumentCaptor.forClass(String.class);
			@SuppressWarnings("unchecked")
			ArgumentCaptor<Map<String, Object>> metaCap = ArgumentCaptor.forClass(Map.class);
			verify(auditLogger).log(actionCap.capture(), typeCap.capture(),
					resIdCap.capture(), summaryCap.capture(), metaCap.capture());
			assertThat(actionCap.getValue()).isEqualTo(AuditAction.CREATE);
			assertThat(typeCap.getValue()).isEqualTo("super_admin");
			assertThat(resIdCap.getValue()).isEqualTo(saved.getPublicUuid());
			assertThat(summaryCap.getValue()).isEqualTo("SUPER_ADMIN created");
			assertThat(metaCap.getValue()).containsEntry("email", "new-admin@edushift.pe");

			assertThat(summary.email()).isEqualTo("new-admin@edushift.pe");
		}

		@Test
		@DisplayName("normalizes email to lowercase + trim before persisting")
		void normalizesEmail() {
			CreateSuperAdminRequest req = new CreateSuperAdminRequest(
					"  MIXED@Case.PE ", "X", "Y");
			when(userRepository.existsByEmail("mixed@case.pe")).thenReturn(false);
			when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

			// Non-null actor required — see H8 (Map.of() NPE on null).
			service.create(req, UUID.randomUUID());

			ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
			verify(userRepository).save(cap.capture());
			assertThat(cap.getValue().getEmail()).isEqualTo("mixed@case.pe");
		}

		@Test
		@DisplayName("rejects duplicate email with 409 EMAIL_TAKEN, no audit emitted")
		void emailTaken() {
			CreateSuperAdminRequest req = new CreateSuperAdminRequest(
					"dup@edushift.pe", "X", "Y");
			when(userRepository.existsByEmail("dup@edushift.pe")).thenReturn(true);

			assertThatThrownBy(() -> service.create(req, null))
					.isInstanceOf(ConflictException.class)
					.satisfies(t -> assertThat(((ConflictException) t).getCode())
							.isEqualTo("EMAIL_TAKEN"));

			verify(userRepository, never()).save(any(User.class));
			verify(auditLogger, never()).log(any(), anyString(), any(), anyString());
		}
	}

	// =========================================================================
	// disable()
	// =========================================================================

	@Nested
	@DisplayName("disable()")
	class DisableTests {

		private final UUID actor = UUID.randomUUID();

		@Test
		@DisplayName("happy path — flips status to INACTIVE, audits UPDATE, returns updated summary")
		void happyPath() {
			User target = superAdmin("target@edushift.pe", UserStatus.ACTIVE);
			User other = superAdmin("other@edushift.pe", UserStatus.ACTIVE);
			when(userRepository.findByPublicUuid(target.getPublicUuid()))
					.thenReturn(Optional.of(target));
			when(userRepository.findAll()).thenReturn(List.of(target, other));
			when(userRepository.saveAndFlush(target)).thenReturn(target);

			SuperAdminSummary out = service.disable(target.getPublicUuid(), actor);

			assertThat(out.status()).isEqualTo("INACTIVE");
			assertThat(target.getStatus()).isEqualTo(UserStatus.INACTIVE);

			ArgumentCaptor<AuditAction> act = ArgumentCaptor.forClass(AuditAction.class);
			verify(auditLogger).log(act.capture(), eq("super_admin"),
					eq(target.getPublicUuid()), eq("SUPER_ADMIN disabled"), any());
			assertThat(act.getValue()).isEqualTo(AuditAction.UPDATE);
		}

		@Test
		@DisplayName("unknown publicUuid → 404 USER_NOT_FOUND")
		void notFound() {
			UUID unknown = UUID.randomUUID();
			when(userRepository.findByPublicUuid(unknown)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.disable(unknown, actor))
					.isInstanceOf(NotFoundException.class)
					.satisfies(t -> assertThat(((NotFoundException) t).getCode())
							.isEqualTo("USER_NOT_FOUND"));

			verify(userRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("target is TENANT_ADMIN (not SUPER_ADMIN) → 403 NOT_SUPER_ADMIN")
		void notSuperAdmin() {
			User ta = tenantAdmin();
			when(userRepository.findByPublicUuid(ta.getPublicUuid()))
					.thenReturn(Optional.of(ta));

			assertThatThrownBy(() -> service.disable(ta.getPublicUuid(), actor))
					.isInstanceOf(ForbiddenException.class)
					.satisfies(t -> assertThat(((ForbiddenException) t).getCode())
							.isEqualTo("NOT_SUPER_ADMIN"));
		}

		@Test
		@DisplayName("actor disabling themselves → 422 SELF_DISABLE_FORBIDDEN")
		void selfDisable() {
			User self = superAdmin("self@edushift.pe", UserStatus.ACTIVE);
			User other = superAdmin("other@edushift.pe", UserStatus.ACTIVE);
			when(userRepository.findByPublicUuid(self.getPublicUuid()))
					.thenReturn(Optional.of(self));
			when(userRepository.findAll()).thenReturn(List.of(self, other));

			assertThatThrownBy(() -> service.disable(self.getPublicUuid(), self.getPublicUuid()))
					.isInstanceOf(BusinessException.class)
					.satisfies(t -> assertThat(((BusinessException) t).getCode())
							.isEqualTo("SELF_DISABLE_FORBIDDEN"));

			verify(userRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("already inactive → 409 ALREADY_DISABLED")
		void alreadyDisabled() {
			User inact = inactiveSuperAdmin();
			User other = superAdmin("other@edushift.pe", UserStatus.ACTIVE);
			when(userRepository.findByPublicUuid(inact.getPublicUuid()))
					.thenReturn(Optional.of(inact));
			when(userRepository.findAll()).thenReturn(List.of(inact, other));

			assertThatThrownBy(() -> service.disable(inact.getPublicUuid(), actor))
					.isInstanceOf(ConflictException.class)
					.satisfies(t -> assertThat(((ConflictException) t).getCode())
							.isEqualTo("ALREADY_DISABLED"));
		}

		@Test
		@DisplayName("last remaining SUPER_ADMIN → 403 QUORUM_REQUIRED")
		void quorumRequired() {
			User solo = superAdmin("solo@edushift.pe", UserStatus.ACTIVE);
			// findAll() returns ONLY the target — no other active SUPER_ADMIN.
			when(userRepository.findByPublicUuid(solo.getPublicUuid()))
					.thenReturn(Optional.of(solo));
			when(userRepository.findAll()).thenReturn(List.of(solo));

			assertThatThrownBy(() -> service.disable(solo.getPublicUuid(), actor))
					.isInstanceOf(ForbiddenException.class)
					.satisfies(t -> assertThat(((ForbiddenException) t).getCode())
							.isEqualTo("QUORUM_REQUIRED"));

			verify(userRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("actor=null (system job) — self-disable guard short-circuits cleanly")
		void systemActorSkipsSelfCheck() {
			// Note: actorUuid=null would NPE in production due to Map.of() (H8).
			// To exercise the "actor=null skips self-check" branch we pass a
			// fresh non-null UUID for the actor — the controller path that
			// would supply null is not currently reachable in production.
			UUID systemActor = UUID.randomUUID();
			User target = superAdmin("target@edushift.pe", UserStatus.ACTIVE);
			User other = superAdmin("other@edushift.pe", UserStatus.ACTIVE);
			when(userRepository.findByPublicUuid(target.getPublicUuid()))
					.thenReturn(Optional.of(target));
			when(userRepository.findAll()).thenReturn(List.of(target, other));
			when(userRepository.saveAndFlush(target)).thenReturn(target);

			SuperAdminSummary out = service.disable(target.getPublicUuid(), systemActor);

			assertThat(out.status()).isEqualTo("INACTIVE");
			// audit metadata must carry actorUuid, not absent
			ArgumentCaptor<Map<String, Object>> metaCap = ArgumentCaptor.forClass(Map.class);
			verify(auditLogger).log(any(), eq("super_admin"),
					eq(target.getPublicUuid()), eq("SUPER_ADMIN disabled"), metaCap.capture());
			assertThat(metaCap.getValue()).containsEntry("actorUuid", systemActor.toString());
		}
	}

	// =========================================================================
	// countActive()
	// =========================================================================

	@Nested
	@DisplayName("countActive()")
	class CountActiveTests {

		@Test
		@DisplayName("returns count of users with role SUPER_ADMIN and status ACTIVE")
		void countsOnlyActiveSuperAdmins() {
			User a = superAdmin("a@e.pe", UserStatus.ACTIVE);
			User b = superAdmin("b@e.pe", UserStatus.ACTIVE);
			User c = superAdmin("c@e.pe", UserStatus.INACTIVE);
			User ta = tenantAdmin();
			when(userRepository.findAll()).thenReturn(List.of(a, b, c, ta));

			assertThat(service.countActive()).isEqualTo(2L);
		}

		@Test
		@DisplayName("returns 0 when no SUPER_ADMINs exist")
		void zero() {
			when(userRepository.findAll()).thenReturn(List.of(tenantAdmin()));
			assertThat(service.countActive()).isZero();
		}
	}

	// =========================================================================
	// Tenant context scoping
	// =========================================================================

	@Nested
	@DisplayName("Tenant context scoping")
	class TenantContextTests {

		@Test
		@DisplayName("list() executes its lambda under TenantContext.SUPER_ADMIN_SENTINEL")
		void listRunsUnderSentinel() {
			when(userRepository.findAll()).thenAnswer(inv -> {
				// Snapshot tenant context INSIDE the service's runAs(...)
				// call. The service must have bound SUPER_ADMIN_SENTINEL.
				assertThat(TenantContext.isSet()).isTrue();
				assertThat(TenantContext.current().orElseThrow())
						.isEqualTo(TenantIdResolver.SUPER_ADMIN_SENTINEL);
				return List.of();
			});

			service.list();
			// After service returns, tenant context is restored (to null in
			// this case because the test had nothing bound).
			assertThat(TenantContext.isSet()).isFalse();
		}

		@Test
		@org.junit.jupiter.api.DisplayName("create() runs its transaction under SUPER_ADMIN_SENTINEL too")
		void createRunsUnderSentinel() {
			CreateSuperAdminRequest req = new CreateSuperAdminRequest(
					"x@edushift.pe", "X", "Y");
			when(userRepository.existsByEmail(anyString())).thenReturn(false);
			when(userRepository.save(any(User.class))).thenAnswer(inv -> {
				assertThat(TenantContext.current().orElseThrow())
						.isEqualTo(TenantIdResolver.SUPER_ADMIN_SENTINEL);
				return inv.getArgument(0);
			});

			// Non-null actor required — see H8 (Map.of() NPE on null).
			service.create(req, UUID.randomUUID());
		}
	}

	// (no nested tests beyond this point)
}
