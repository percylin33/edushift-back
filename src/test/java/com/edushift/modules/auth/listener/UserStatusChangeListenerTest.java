package com.edushift.modules.auth.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.audit.events.AuditAction;
import com.edushift.modules.audit.service.AuditLogger;
import com.edushift.modules.auth.entity.RevocationReason;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.events.UserStatusChangeEvent;
import com.edushift.modules.auth.repository.RefreshTokenRepository;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Sprint 14 (MVP Closure) / DEBT-AUTH-4 — unit tests for
 * {@link UserStatusChangeListener}.
 *
 * <p>Verifies that the listener revokes all refresh tokens of a user
 * when their status transitions from authenticatable to non-authenticatable.
 */
@ExtendWith(MockitoExtension.class)
class UserStatusChangeListenerTest {

	@Mock private RefreshTokenRepository refreshTokenRepository;
	@Mock private UserRepository userRepository;
	@Mock private AuditLogger auditLogger;

	private UserStatusChangeListener listener;

	private static final UUID TENANT_ID = UUID.randomUUID();
	private static final UUID USER_PUBLIC_UUID = UUID.randomUUID();
	private static final UUID ACTOR_PUBLIC_UUID = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		listener = new UserStatusChangeListener(
				refreshTokenRepository, userRepository, auditLogger);
		TenantContext.set(TENANT_ID);
	}

	@AfterEach
	void tearDown() {
		TenantContext.clear();
	}

	private User userWithStatus(UserStatus status) {
		User u = new User();
		u.setId(UUID.randomUUID());
		u.setPublicUuid(USER_PUBLIC_UUID);
		u.setTenantId(TENANT_ID);
		u.setStatus(status);
		u.setRoles(new String[]{"STUDENT"});
		return u;
	}

	@Test
	@DisplayName("DEBT-AUTH-4: ACTIVE → SUSPENDED revokes 3 refresh tokens + audit")
	void suspendingUserRevokesAllTokens() {
		User u = userWithStatus(UserStatus.SUSPENDED);  // already set in updateUser
		when(userRepository.findByPublicUuid(USER_PUBLIC_UUID)).thenReturn(Optional.of(u));
		when(refreshTokenRepository.revokeAllByUser(u.getId(), RevocationReason.ADMIN_REVOKE))
				.thenReturn(3);

		listener.onUserStatusChange(new UserStatusChangeEvent(
				USER_PUBLIC_UUID, UserStatus.ACTIVE, UserStatus.SUSPENDED,
				"admin-disable", ACTOR_PUBLIC_UUID));

		verify(refreshTokenRepository, times(1))
				.revokeAllByUser(u.getId(), RevocationReason.ADMIN_REVOKE);
		verify(auditLogger, times(1)).log(
				eq(AuditAction.ADMIN_REVOKE), eq("refresh_token"), org.mockito.ArgumentMatchers.isNull(),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyMap());
	}

	@Test
	@DisplayName("DEBT-AUTH-4: ACTIVE → LOCKED also revokes")
	void lockingUserRevokesAllTokens() {
		User u = userWithStatus(UserStatus.LOCKED);
		when(userRepository.findByPublicUuid(USER_PUBLIC_UUID)).thenReturn(Optional.of(u));
		when(refreshTokenRepository.revokeAllByUser(u.getId(), RevocationReason.ADMIN_REVOKE))
				.thenReturn(1);

		listener.onUserStatusChange(new UserStatusChangeEvent(
				USER_PUBLIC_UUID, UserStatus.ACTIVE, UserStatus.LOCKED,
				"auto-lockout", ACTOR_PUBLIC_UUID));

		verify(refreshTokenRepository, times(1))
				.revokeAllByUser(u.getId(), RevocationReason.ADMIN_REVOKE);
	}

	@Test
	@DisplayName("DEBT-AUTH-4: same-status transition is a no-op")
	void sameStatusNoOp() {
		listener.onUserStatusChange(new UserStatusChangeEvent(
				USER_PUBLIC_UUID, UserStatus.ACTIVE, UserStatus.ACTIVE,
				"double-toggle", ACTOR_PUBLIC_UUID));
		verify(userRepository, never()).findByPublicUuid(USER_PUBLIC_UUID);
	}

	@Test
	@DisplayName("DEBT-AUTH-4: ACTIVE → PENDING_VERIFICATION does NOT revoke (still authenticatable after verify)")
	void pendingVerificationDoesNotRevoke() {
		User u = userWithStatus(UserStatus.PENDING_VERIFICATION);
		// No need to mock findByPublicUuid: the listener skips the revoke
		// because the new status CAN authenticate (after email verify).
		when(userRepository.findByPublicUuid(USER_PUBLIC_UUID)).thenReturn(Optional.of(u));

		listener.onUserStatusChange(new UserStatusChangeEvent(
				USER_PUBLIC_UUID, UserStatus.ACTIVE, UserStatus.PENDING_VERIFICATION,
				"reset-email", ACTOR_PUBLIC_UUID));

		verify(refreshTokenRepository, never()).revokeAllByUser(
				(UUID) org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(RevocationReason.class));
	}

	@Test
	@DisplayName("DEBT-AUTH-4: re-enabling SUSPENDED → ACTIVE does NOT revoke")
	void reenableDoesNotRevoke() {
		User u = userWithStatus(UserStatus.ACTIVE);
		when(userRepository.findByPublicUuid(USER_PUBLIC_UUID)).thenReturn(Optional.of(u));

		listener.onUserStatusChange(new UserStatusChangeEvent(
				USER_PUBLIC_UUID, UserStatus.SUSPENDED, UserStatus.ACTIVE,
				"admin-enable", ACTOR_PUBLIC_UUID));

		verify(refreshTokenRepository, never()).revokeAllByUser(
				(UUID) org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(RevocationReason.class));
	}

	@Test
	@DisplayName("DEBT-AUTH-4: missing user → silent no-op, no audit spam")
	void missingUserIsNoOp() {
		when(userRepository.findByPublicUuid(USER_PUBLIC_UUID)).thenReturn(Optional.empty());

		listener.onUserStatusChange(new UserStatusChangeEvent(
				USER_PUBLIC_UUID, UserStatus.ACTIVE, UserStatus.SUSPENDED,
				"admin-disable", ACTOR_PUBLIC_UUID));

		verify(refreshTokenRepository, never())
				.revokeAllByUser((UUID) org.mockito.ArgumentMatchers.any(),
						org.mockito.ArgumentMatchers.any(RevocationReason.class));
		verify(auditLogger, never()).log(org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	private static <T> T eq(T value) {
		return org.mockito.ArgumentMatchers.eq(value);
	}
}
