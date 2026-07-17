package com.edushift.modules.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.auth.entity.FailedLoginAttempt;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.exception.UserLockedException;
import com.edushift.modules.auth.repository.FailedLoginAttemptRepository;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Sprint 14 (MVP Closure) / DEBT-AUTH-7 — unit tests for
 * {@link LoginAttemptService}.
 *
 * <p>Coverage:
 * <ul>
 *   <li>5 failures → row reaches threshold + user locked.</li>
 *   <li>Locked user is rejected with {@link UserLockedException}.</li>
 *   <li>{@code TENANT_ADMIN} / {@code SUPER_ADMIN} are NEVER locked.</li>
 *   <li>Successful login clears the counter.</li>
 *   <li>Unknown-email failures still count (anti-probing).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

	@Mock private FailedLoginAttemptRepository attemptRepository;
	@Mock private UserRepository userRepository;
	@Mock private com.edushift.modules.tenants.repository.TenantRepository tenantRepository;
	@InjectMocks private LoginAttemptService service;

	private static final UUID TENANT_ID = UUID.randomUUID();
	private static final String EMAIL = "student@tecnosur.com";

	@BeforeEach
	void bindTenant() {
		TenantContext.set(TENANT_ID);
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private User student() {
		User u = new User();
		u.setPublicUuid(UUID.randomUUID());
		u.setId(UUID.randomUUID());
		u.setEmail(EMAIL);
		u.setRoles(new String[]{UserRole.STUDENT.name()});
		u.setStatus(com.edushift.modules.auth.entity.UserStatus.ACTIVE);
		u.setTemporarilyLockedUntil(null);
		return u;
	}

	private User tenantAdmin() {
		User u = student();
		u.setRoles(new String[]{UserRole.TENANT_ADMIN.name()});
		return u;
	}

	@Test
	@DisplayName("DEBT-AUTH-7: 5 failures → user locked for 15 minutes")
	void fifthFailureLocksUser() {
		User u = student();
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(u));
		FailedLoginAttempt existing = new FailedLoginAttempt();
		existing.setEmail(EMAIL);
		existing.setStatus(FailedLoginAttempt.Status.ACTIVE);
		existing.setAttemptCount(4);
		existing.setLastAttemptAt(Instant.now());
		existing.setFirstAttemptAt(Instant.now());
		when(attemptRepository.findMostRecent(EMAIL)).thenReturn(Optional.of(existing));
		lenient().when(attemptRepository.saveAndFlush(any(FailedLoginAttempt.class)))
				.thenAnswer(inv -> inv.getArgument(0));

		service.recordFailure(EMAIL);

		ArgumentCaptor<FailedLoginAttempt> rowCaptor =
				ArgumentCaptor.forClass(FailedLoginAttempt.class);
		verify(attemptRepository, times(2)).saveAndFlush(rowCaptor.capture());

		FailedLoginAttempt last = rowCaptor.getAllValues().get(rowCaptor.getAllValues().size() - 1);
		assertThat(last.getAttemptCount()).isEqualTo(5);
		assertThat(last.getStatus()).isEqualTo(FailedLoginAttempt.Status.LOCKED);
		assertThat(last.getLockedUntil()).isAfter(Instant.now());
		assertThat(u.getTemporarilyLockedUntil()).isNotNull();
	}

	@Test
	@DisplayName("DEBT-AUTH-7: assertNotLocked throws UserLockedException on locked user")
	void assertNotLockedThrowsWhenLocked() {
		User u = student();
		u.setTemporarilyLockedUntil(Instant.now().plusSeconds(60));

		assertThatThrownBy(() -> service.assertNotLocked(u))
				.isInstanceOf(UserLockedException.class)
				.hasMessageContaining("temporarily locked");
	}

	@Test
	@DisplayName("DEBT-AUTH-7: TENANT_ADMIN users are NEVER locked (DOS mitigation)")
	void tenantAdminNotLocked() {
		User admin = tenantAdmin();

		// Should NOT throw even with a flag-set timestamp.
		admin.setTemporarilyLockedUntil(Instant.now().plusSeconds(60));
		service.assertNotLocked(admin);  // returns normally

		when(userRepository.findByEmail("admin@tecnosur.com")).thenReturn(Optional.of(admin));
		TenantContext.set(TENANT_ID);
		service.recordFailure("admin@tecnosur.com");
		verify(attemptRepository, never()).saveAndFlush(any(FailedLoginAttempt.class));
	}

	@Test
	@DisplayName("DEBT-AUTH-7: Successful login clears the counter")
	void successClearsCounter() {
		User u = student();
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(u));
		FailedLoginAttempt existing = new FailedLoginAttempt();
		existing.setEmail(EMAIL);
		existing.setStatus(FailedLoginAttempt.Status.ACTIVE);
		existing.setAttemptCount(3);
		existing.setLastAttemptAt(Instant.now());
		existing.setFirstAttemptAt(Instant.now());
		when(attemptRepository.findMostRecent(EMAIL)).thenReturn(Optional.of(existing));
		lenient().when(attemptRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

		service.recordSuccessfulLogin(EMAIL);

		assertThat(existing.getStatus()).isEqualTo(FailedLoginAttempt.Status.CLEARED);
		assertThat(existing.getAttemptCount()).isZero();
		assertThat(u.getTemporarilyLockedUntil()).isNull();
	}

	@Test
	@DisplayName("DEBT-AUTH-7: Unknown email still counts (anti-probing) — short-circuits in repo")
	void unknownEmailShortCircuits() {
		when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
		// The production code still reads the tenant slug to satisfy the
		// NOT NULL constraint on failed_login_attempts.tenant_slug,
		// even when the user lookup misses. Mock it to return a real
		// tenant so the save flow can complete.
		var tenant = new com.edushift.modules.tenants.entity.Tenant();
		tenant.setSlug("tecnosur");
		when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

		// Should NOT throw. The attempt IS still counted (anti-probing
		// defense — we don't want attackers to enumerate which emails
		// are real), just without the user entity attached.
		service.recordFailure("ghost@example.com");

		verify(attemptRepository, times(1)).saveAndFlush(any());
	}

	@Test
	@DisplayName("DEBT-AUTH-7: Null tenant context → no-op")
	void noTenantContextNoOp() {
		TenantContext.clear();
		service.recordFailure(EMAIL);
		verify(attemptRepository, never()).saveAndFlush(any());
	}

	@Test
	@DisplayName("DEBT-AUTH-7: empty email is ignored")
	void emptyEmailIgnored() {
		service.recordFailure("");
		service.recordFailure(null);
		verify(attemptRepository, never()).saveAndFlush(any());
	}

	@Test
	@DisplayName("DEBT-AUTH-7: Remaining lock duration helper")
	void remainingLockDuration() {
		User u = student();
		assertThat(service.remainingLockDuration(u)).isZero();
		u.setTemporarilyLockedUntil(Instant.now().plusSeconds(120));
		assertThat(service.remainingLockDuration(u).getSeconds()).isBetween(110L, 130L);
	}
}
