package com.edushift.modules.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.auth.dto.ResetPasswordValidateResponse;
import com.edushift.modules.auth.entity.PasswordResetToken;
import com.edushift.modules.auth.entity.RefreshToken;
import com.edushift.modules.auth.entity.RevocationReason;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.PasswordResetTokenRepository;
import com.edushift.modules.auth.repository.RefreshTokenRepository;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.notifications.service.NotificationService;
import com.edushift.modules.notifications.service.NotificationService.NotifyCommand;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.exception.UnauthorizedException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * Unit tests for {@link PasswordResetService} (Sprint 17 / BE-17.1).
 *
 * <p>Mock-based; no Spring context, no DB. Pins the anti-enumeration
 * contract, the cross-tenant safety, and the single-use / supersede /
 * revoke-all-refreshes side-effects.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetServiceTest {

	@Mock private UserRepository userRepository;
	@Mock private TenantRepository tenantRepository;
	@Mock private PasswordResetTokenRepository resetTokenRepository;
	@Mock private RefreshTokenRepository refreshTokenRepository;
	@Mock private JwtService jwtService;
	@Mock private NotificationService notificationService;
	@Mock private PasswordEncoder passwordEncoder;
	@Mock private PlatformTransactionManager txManager;

	private PasswordResetService service;

	@BeforeEach
	void setUp() {
		// Use SimpleMeterRegistry to satisfy the counter wiring without
		// bringing in a full Micrometer stack.
		service = new PasswordResetService(
				userRepository, tenantRepository, resetTokenRepository,
				refreshTokenRepository, jwtService, notificationService,
				passwordEncoder, txManager, new SimpleMeterRegistry());
		// TransactionTemplate is wired off txManager; configure it to always
		// return a no-op SimpleTransactionStatus so the lambdas execute.
		when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
	}

	// ====================================================================
	// requestReset
	// ====================================================================

	@Nested
	@DisplayName("requestReset — anti-enumeration contract")
	class RequestReset {

		@Test
		@DisplayName("Sends email + persists token when user exists in active tenant")
		void happyPath() {
			Tenant tenant = newTenant("acme", TenantStatus.ACTIVE);
			User user = newUser(tenant, "alice@acme.test", UserStatus.ACTIVE);
			when(tenantRepository.findBySlugIgnoreCase("acme")).thenReturn(Optional.of(tenant));
			when(userRepository.findByEmailAndTenantId("alice@acme.test", tenant.getId()))
					.thenReturn(Optional.of(user));
			when(jwtService.issueResetToken(eq(user), eq(tenant), any(UUID.class)))
					.thenReturn("signed.jwt.token");
			when(jwtService.resetTokenTtl()).thenReturn(Duration.ofHours(1));

			service.requestReset("alice@acme.test", "acme", "127.0.0.1");

			// Token persisted
			ArgumentCaptor<PasswordResetToken> rowCap = ArgumentCaptor.forClass(PasswordResetToken.class);
			verify(resetTokenRepository).save(rowCap.capture());
			PasswordResetToken row = rowCap.getValue();
			assertThat(row.getUserId()).isEqualTo(user.getId());
			assertThat(row.getRequestIp()).isEqualTo("127.0.0.1");
			assertThat(row.getExpiresAt()).isAfter(Instant.now());

			// Email queued
			ArgumentCaptor<NotifyCommand> cmdCap = ArgumentCaptor.forClass(NotifyCommand.class);
			verify(notificationService).notify(cmdCap.capture());
			NotifyCommand cmd = cmdCap.getValue();
			assertThat(cmd.templateKey()).isEqualTo("PASSWORD_RESET");
			assertThat(cmd.recipientUserId()).isEqualTo(user.getId());
			assertThat(cmd.recipientEmail()).isEqualTo(user.getEmail());
		}

		@Test
		@DisplayName("Unknown tenant: silently discards, no email, no DB row")
		void unknownTenant() {
			when(tenantRepository.findBySlugIgnoreCase("ghost")).thenReturn(Optional.empty());
			service.requestReset("anyone@ghost.test", "ghost", "127.0.0.1");
			verify(notificationService, never()).notify(any());
			verify(resetTokenRepository, never()).save(any(PasswordResetToken.class));
		}

		@Test
		@DisplayName("Inactive tenant: silently discards, no email, no DB row")
		void inactiveTenant() {
			Tenant tenant = newTenant("paused", TenantStatus.SUSPENDED);
			when(tenantRepository.findBySlugIgnoreCase("paused")).thenReturn(Optional.of(tenant));
			service.requestReset("anyone@paused.test", "paused", "127.0.0.1");
			verify(notificationService, never()).notify(any());
			verify(resetTokenRepository, never()).save(any(PasswordResetToken.class));
		}

		@Test
		@DisplayName("Unknown email: silently discards, no email, no DB row")
		void unknownEmail() {
			Tenant tenant = newTenant("acme", TenantStatus.ACTIVE);
			when(tenantRepository.findBySlugIgnoreCase("acme")).thenReturn(Optional.of(tenant));
			when(userRepository.findByEmailAndTenantId("ghost@acme.test", tenant.getId()))
					.thenReturn(Optional.empty());
			service.requestReset("ghost@acme.test", "acme", "127.0.0.1");
			verify(notificationService, never()).notify(any());
			verify(resetTokenRepository, never()).save(any(PasswordResetToken.class));
		}

		@Test
		@DisplayName("Locked user: silently discards, no email, no DB row")
		void lockedUser() {
			Tenant tenant = newTenant("acme", TenantStatus.ACTIVE);
			User user = newUser(tenant, "alice@acme.test", UserStatus.LOCKED);
			when(tenantRepository.findBySlugIgnoreCase("acme")).thenReturn(Optional.of(tenant));
			when(userRepository.findByEmailAndTenantId("alice@acme.test", tenant.getId()))
					.thenReturn(Optional.of(user));
			service.requestReset("alice@acme.test", "acme", "127.0.0.1");
			verify(notificationService, never()).notify(any());
			verify(resetTokenRepository, never()).save(any(PasswordResetToken.class));
		}

		@Test
		@DisplayName("New request supersedes all pending tokens for the same user")
		void supersedesPreviousTokens() {
			Tenant tenant = newTenant("acme", TenantStatus.ACTIVE);
			User user = newUser(tenant, "alice@acme.test", UserStatus.ACTIVE);
			when(tenantRepository.findBySlugIgnoreCase("acme")).thenReturn(Optional.of(tenant));
			when(userRepository.findByEmailAndTenantId("alice@acme.test", tenant.getId()))
					.thenReturn(Optional.of(user));
			when(jwtService.issueResetToken(eq(user), eq(tenant), any(UUID.class)))
					.thenReturn("signed.jwt.token");
			when(jwtService.resetTokenTtl()).thenReturn(Duration.ofHours(1));

			service.requestReset("alice@acme.test", "acme", "127.0.0.1");

			verify(resetTokenRepository).supersedeAllPendingForUser(eq(user.getId()), any(Instant.class));
		}
	}

	// ====================================================================
	// validateToken
	// ====================================================================

	@Nested
	@DisplayName("validateToken — read-only inspection")
	class ValidateToken {

		@Test
		@DisplayName("Blank token → valid=false with RESET_TOKEN_MISSING")
		void blank() {
			ResetPasswordValidateResponse resp = service.validateToken(" ");
			assertThat(resp.valid()).isFalse();
			assertThat(resp.reasonCode()).isEqualTo("RESET_TOKEN_MISSING");
		}

		@Test
		@DisplayName("Wrong token type → valid=false with RESET_TOKEN_WRONG_TYPE")
		void wrongType() {
			JwtService.JwtClaims claims = new JwtService.JwtClaims(
					"u", UUID.randomUUID(), "acme", java.util.Set.of(),
					JwtService.TokenType.ACCESS, UUID.randomUUID(), Instant.now(), Instant.now().plusSeconds(60));
			when(jwtService.parseAndValidate("t")).thenReturn(claims);
			ResetPasswordValidateResponse resp = service.validateToken("t");
			assertThat(resp.valid()).isFalse();
			assertThat(resp.reasonCode()).isEqualTo("RESET_TOKEN_WRONG_TYPE");
		}

		@Test
		@DisplayName("Expired token → valid=false with RESET_TOKEN_EXPIRED")
		void expired() {
			UUID jti = UUID.randomUUID();
			Tenant tenant = newTenant("acme", TenantStatus.ACTIVE);
			User user = newUser(tenant, "alice@acme.test", UserStatus.ACTIVE);
			JwtService.JwtClaims claims = new JwtService.JwtClaims(
					user.getPublicUuid().toString(), tenant.getId(), tenant.getSlug(),
					java.util.Set.of(), JwtService.TokenType.RESET, jti,
					Instant.now().minusSeconds(7200), Instant.now().minusSeconds(60));
			when(jwtService.parseAndValidate("t")).thenReturn(claims);
			when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));
			when(resetTokenRepository.findByJti(jti)).thenReturn(Optional.of(buildRow(jti, user.getId(), tenant.getId(), Instant.now().minusSeconds(60), null, null)));
			when(userRepository.findByPublicUuid(user.getPublicUuid())).thenReturn(Optional.of(user));
			ResetPasswordValidateResponse resp = service.validateToken("t");
			assertThat(resp.valid()).isFalse();
			assertThat(resp.reasonCode()).isEqualTo("RESET_TOKEN_EXPIRED");
		}
	}

	// ====================================================================
	// consumeToken
	// ====================================================================

	@Nested
	@DisplayName("consumeToken — terminal step")
	class ConsumeToken {

		@Test
		@DisplayName("Happy path: updates password, marks used, revokes all refresh tokens")
		void happyPath() {
			UUID jti = UUID.randomUUID();
			Tenant tenant = newTenant("acme", TenantStatus.ACTIVE);
			User user = newUser(tenant, "alice@acme.test", UserStatus.ACTIVE);
			JwtService.JwtClaims claims = new JwtService.JwtClaims(
					user.getPublicUuid().toString(), tenant.getId(), tenant.getSlug(),
					java.util.Set.of(), JwtService.TokenType.RESET, jti,
					Instant.now(), Instant.now().plusSeconds(3600));
			when(jwtService.parseAndValidate("t")).thenReturn(claims);
			when(resetTokenRepository.findByJti(jti))
					.thenReturn(Optional.of(buildRow(jti, user.getId(), tenant.getId(), Instant.now().plusSeconds(3600), null, null)));
			when(userRepository.findByPublicUuid(user.getPublicUuid())).thenReturn(Optional.of(user));
			when(passwordEncoder.encode("NewPass1!")).thenReturn("new-hash");
			when(refreshTokenRepository.revokeAllByUser(user.getId(), "ADMIN_REVOKE"))
					.thenReturn(3);

			service.consumeToken("t", "NewPass1!");

			// Password updated
			assertThat(user.getPasswordHash()).isEqualTo("new-hash");
			// Token marked used
			ArgumentCaptor<PasswordResetToken> rowCap = ArgumentCaptor.forClass(PasswordResetToken.class);
			verify(resetTokenRepository).saveAndFlush(rowCap.capture());
			assertThat(rowCap.getValue().getUsedAt()).isNotNull();
			// All refresh tokens revoked (call passes RevocationReason, the
			// default method converts to .name() before delegating)
			verify(refreshTokenRepository, atLeastOnce()).revokeAllByUser(eq(user.getId()), eq(RevocationReason.ADMIN_REVOKE));
		}

		@Test
		@DisplayName("Reused token → 401 RESET_TOKEN_USED")
		void reusedToken() {
			UUID jti = UUID.randomUUID();
			Tenant tenant = newTenant("acme", TenantStatus.ACTIVE);
			User user = newUser(tenant, "alice@acme.test", UserStatus.ACTIVE);
			JwtService.JwtClaims claims = new JwtService.JwtClaims(
					user.getPublicUuid().toString(), tenant.getId(), tenant.getSlug(),
					java.util.Set.of(), JwtService.TokenType.RESET, jti,
					Instant.now(), Instant.now().plusSeconds(3600));
			when(jwtService.parseAndValidate("t")).thenReturn(claims);
			PasswordResetToken row = buildRow(jti, user.getId(), tenant.getId(),
					Instant.now().plusSeconds(3600), Instant.now().minusSeconds(60), null);
			when(resetTokenRepository.findByJti(jti)).thenReturn(Optional.of(row));
			when(userRepository.findByPublicUuid(user.getPublicUuid())).thenReturn(Optional.of(user));

			assertThatThrownBy(() -> service.consumeToken("t", "NewPass1!"))
					.isInstanceOf(UnauthorizedException.class)
					.extracting("code").isEqualTo("RESET_TOKEN_USED");
		}

		@Test
		@DisplayName("Cross-tenant token (DB row tenantId != claim tenantId) → 401 RESET_TOKEN_INVALID")
		void crossTenant() {
			UUID jti = UUID.randomUUID();
			Tenant tenantA = newTenant("A", TenantStatus.ACTIVE);
			Tenant tenantB = newTenant("B", TenantStatus.ACTIVE);
			User user = newUser(tenantA, "alice@a.test", UserStatus.ACTIVE);
			JwtService.JwtClaims claims = new JwtService.JwtClaims(
					user.getPublicUuid().toString(), tenantA.getId(), tenantA.getSlug(),
					java.util.Set.of(), JwtService.TokenType.RESET, jti,
					Instant.now(), Instant.now().plusSeconds(3600));
			when(jwtService.parseAndValidate("t")).thenReturn(claims);
			// DB row claims to belong to tenantB
			PasswordResetToken row = buildRow(jti, user.getId(), tenantB.getId(),
					Instant.now().plusSeconds(3600), null, null);
			when(resetTokenRepository.findByJti(jti)).thenReturn(Optional.of(row));

			assertThatThrownBy(() -> service.consumeToken("t", "NewPass1!"))
					.isInstanceOf(UnauthorizedException.class)
					.extracting("code").isEqualTo("RESET_TOKEN_INVALID");
		}

		@Test
		@DisplayName("Locked user is un-locked by successful reset")
		void unlocksUser() {
			UUID jti = UUID.randomUUID();
			Tenant tenant = newTenant("acme", TenantStatus.ACTIVE);
			User user = newUser(tenant, "alice@acme.test", UserStatus.LOCKED);
			JwtService.JwtClaims claims = new JwtService.JwtClaims(
					user.getPublicUuid().toString(), tenant.getId(), tenant.getSlug(),
					java.util.Set.of(), JwtService.TokenType.RESET, jti,
					Instant.now(), Instant.now().plusSeconds(3600));
			when(jwtService.parseAndValidate("t")).thenReturn(claims);
			when(resetTokenRepository.findByJti(jti))
					.thenReturn(Optional.of(buildRow(jti, user.getId(), tenant.getId(), Instant.now().plusSeconds(3600), null, null)));
			when(userRepository.findByPublicUuid(user.getPublicUuid())).thenReturn(Optional.of(user));
			when(passwordEncoder.encode(anyString())).thenReturn("new-hash");
			when(refreshTokenRepository.revokeAllByUser(any(UUID.class), any(String.class))).thenReturn(0);

			service.consumeToken("t", "NewPass1!");

			assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
		}
	}

	// ====================================================================
	// helpers
	// ====================================================================

	private static Tenant newTenant(String slug, TenantStatus status) {
		Tenant t = new Tenant();
		t.setId(UUID.randomUUID());
		t.setSlug(slug);
		t.setName(slug);
		t.setStatus(status);
		return t;
	}

	private static User newUser(Tenant tenant, String email, UserStatus status) {
		User u = new User();
		u.setId(UUID.randomUUID());
		u.setPublicUuid(UUID.randomUUID());
		u.setEmail(email);
		u.setStatus(status);
		u.setTenantId(tenant.getId());
		// PublicUuid is needed for the cross-tenant / sub-claim checks.
		return u;
	}

	private static PasswordResetToken buildRow(UUID jti, UUID userId, UUID tenantId,
	                                            Instant expiresAt, Instant usedAt, Instant supersededAt) {
		PasswordResetToken r = new PasswordResetToken();
		r.setJti(jti);
		r.setUserId(userId);
		r.setTenantId(tenantId);
		r.setExpiresAt(expiresAt);
		r.setUsedAt(usedAt);
		r.setSupersededAt(supersededAt);
		return r;
	}
}